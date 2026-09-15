package com.thaiprompt.smschecker.domain.attribution

import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-site attribution (CONTRACT §E) — เครื่องเดียวผูก Thaiprompt + จันทรา.online
 * ยอดเงินหนึ่งยอดต้องไม่ถูกอนุมัติที่สองเว็บ และต้องบอกได้ว่าเป็นของเว็บไหน
 */
class SiteAttributionTest {

    private val tp = "Thaiprompt"
    private val jt = "จันทรา.online"

    private fun reply(serverId: Long, site: String, matched: Boolean, external: String? = null) =
        SiteMatchReply(serverId = serverId, siteName = site, match = if (matched) "order-$serverId" else null, externalSite = external)

    // ───────────── SiteMatchDecision (GET /orders/match fan-out) ─────────────

    @Test
    fun `no server matched - no winner, no conflict`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, false), reply(2, jt, false)))
        assertNull(d.winner)
        assertFalse(d.isConflict)
        assertNull(d.externalSite)
    }

    @Test
    fun `exactly one server matched - that server wins`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, false), reply(2, jt, true)))
        assertEquals(2L, d.winner?.serverId)
        assertEquals("order-2", d.winner?.match)
        assertFalse(d.isConflict)
    }

    @Test
    fun `two servers matched - conflict, nobody wins, order is deterministic`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, true), reply(2, jt, true)))
        assertTrue(d.isConflict)
        assertNull(d.winner)
        assertEquals(listOf(1L, 2L), d.matches.map { it.serverId })
    }

    @Test
    fun `external_site is only a hint - never counted as a match`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, false, external = jt)))
        assertNull(d.winner)
        assertFalse(d.isConflict)
        assertEquals(jt, d.externalSite)
        assertEquals(1L, d.externalSiteFromServerId)
    }

    @Test
    fun `hint from one server plus a real match elsewhere - still a single winner`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, false, external = jt), reply(2, jt, true)))
        assertEquals(2L, d.winner?.serverId)
        assertEquals(jt, d.externalSite)
    }

    @Test
    fun `same server replying twice is not a conflict`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, true), reply(1, tp, true)))
        assertFalse(d.isConflict)
        assertEquals(1L, d.winner?.serverId)
    }

    @Test
    fun `blank external_site is ignored`() {
        val d = SiteMatchDecision.decide(listOf(reply(1, tp, false, external = "   ")))
        assertNull(d.externalSite)
        assertNull(d.externalSiteFromServerId)
    }

    // ───────────── SiteAttributionMerger ─────────────

    private val empty = SiteAttribution()
    private fun matched(id: Long, name: String, src: String = SiteAttribution.SOURCE_MATCH) =
        AttributionEvent.Matched(id, name, src)

    @Test
    fun `first real match sets the site`() {
        val a = SiteAttributionMerger.merge(empty, matched(2, jt))
        assertEquals(2L, a.serverId)
        assertEquals(jt, a.siteName)
        assertFalse(a.conflict)
    }

    @Test
    fun `match then notify from the same server keeps the first name`() {
        val a = SiteAttributionMerger.merge(empty, matched(2, jt))
        val b = SiteAttributionMerger.merge(a, matched(2, "Juntra local label", SiteAttribution.SOURCE_NOTIFY))
        assertEquals(a, b)
    }

    @Test
    fun `a second server claiming the same credit becomes a conflict`() {
        val a = SiteAttributionMerger.merge(empty, matched(1, tp))
        val b = SiteAttributionMerger.merge(a, matched(2, jt, SiteAttribution.SOURCE_NOTIFY))
        assertTrue(b.conflict)
        assertNull(b.serverId)
        assertNull(b.siteName)
        assertEquals(listOf(tp, jt), b.conflictSites)
    }

    @Test
    fun `hint is used only when nothing is known`() {
        val hinted = SiteAttributionMerger.merge(empty, AttributionEvent.Hint(2, jt, fromServerId = 1))
        assertEquals(jt, hinted.siteName)
        assertEquals(SiteAttribution.SOURCE_HINT, hinted.source)

        val known = SiteAttributionMerger.merge(empty, matched(1, tp))
        assertEquals(known, SiteAttributionMerger.merge(known, AttributionEvent.Hint(2, jt)))
    }

    @Test
    fun `real match replaces a hint without raising a conflict`() {
        val hinted = SiteAttributionMerger.merge(empty, AttributionEvent.Hint(null, jt))
        val a = SiteAttributionMerger.merge(hinted, matched(2, jt))
        assertFalse(a.conflict)
        assertEquals(2L, a.serverId)
        assertEquals(SiteAttribution.SOURCE_MATCH, a.source)
    }

    @Test
    fun `conflict wins and keeps growing - never silently resolves`() {
        val c = SiteAttributionMerger.merge(empty, AttributionEvent.Conflict(listOf(tp, jt)))
        assertTrue(c.conflict)
        val c2 = SiteAttributionMerger.merge(c, matched(3, "Shop C"))
        assertTrue(c2.conflict)
        assertEquals(listOf(tp, jt, "Shop C"), c2.conflictSites)
        // hint and repeated matches do not clear the conflict
        assertEquals(c2, SiteAttributionMerger.merge(c2, AttributionEvent.Hint(2, jt)))
        assertEquals(c2, SiteAttributionMerger.merge(c2, matched(1, tp)))
    }

    @Test
    fun `conflict event drops a hint and keeps a known site`() {
        val hinted = SiteAttributionMerger.merge(empty, AttributionEvent.Hint(2, jt))
        assertEquals(listOf(tp, "Shop C"),
            SiteAttributionMerger.merge(hinted, AttributionEvent.Conflict(listOf(tp, "Shop C"))).conflictSites)

        val known = SiteAttributionMerger.merge(empty, matched(2, jt))
        assertEquals(listOf(jt, tp),
            SiteAttributionMerger.merge(known, AttributionEvent.Conflict(listOf(tp, jt))).conflictSites)
    }

    @Test
    fun `attribution survives the database column round trip`() {
        val conflict = SiteAttributionMerger.merge(empty, AttributionEvent.Conflict(listOf(tp, jt)))
        val tx = BankTransaction(
            bank = "KBANK", type = TransactionType.CREDIT, amount = "100.37",
            accountNumber = "", senderOrReceiver = "", referenceNumber = "",
            rawMessage = "", senderAddress = "", timestamp = 0L,
            matchedServerId = conflict.serverId,
            matchedSiteName = conflict.siteName,
            matchConflict = conflict.conflict,
            conflictSites = conflict.conflictSitesColumn(),
            attributionSource = conflict.source
        )
        assertEquals(conflict, SiteAttribution.of(tx))
        assertNull(tx.attributedSiteName())
        assertEquals(listOf(tp, jt), tx.conflictSiteList())
    }

    // ───────────── SiteNames ─────────────

    @Test
    fun `display name priority is server_name then website_name then local label`() {
        assertEquals(jt, SiteNames.resolve(jt, "Other", "Local"))
        assertEquals("Web", SiteNames.resolve("  ", "Web", "Local"))
        assertEquals("Local", SiteNames.resolve(null, null, "Local"))
        assertEquals("a b", SiteNames.clean(" a \n  b "))
    }

    @Test
    fun `thai domain and punycode are the same host`() {
        val thai = SiteNames.hostOf("https://จันทรา.online/api/v1")
        assertEquals("xn--82c4af5bzdj.online", thai)
        assertEquals(thai, SiteNames.hostOf("https://www.xn--82c4af5bzdj.online"))
        assertEquals(thai, SiteNames.hostOf(jt))
        assertNull(SiteNames.hostOf("Thaiprompt"))
    }

    @Test
    fun `external_site hint maps to the paired server by name or by domain`() {
        val servers = listOf(
            ServerConfig(id = 1, name = "Thaiprompt Main", baseUrl = "https://main.thaiprompt.online", apiKey = "", secretKey = ""),
            ServerConfig(id = 2, name = "Juntra", baseUrl = "https://xn--82c4af5bzdj.online", apiKey = "", secretKey = "")
        )
        // by domain (server has not reported its site name yet)
        assertEquals(2L, SiteNames.serverIdForSite(jt, servers))
        // by server-provided site name
        val named = servers.map { if (it.id == 2L) it.copy(baseUrl = "https://juntra.example", siteName = jt) else it }
        assertEquals(2L, SiteNames.serverIdForSite(jt, named))
        // by local label, case-insensitive
        assertEquals(1L, SiteNames.serverIdForSite("thaiprompt main", servers))
        // unknown site
        assertNull(SiteNames.serverIdForSite("อื่นๆ.com", servers))
    }

    @Test
    fun `server display name prefers the server-provided site name`() {
        val s = ServerConfig(name = "Local label", baseUrl = "https://x.test", apiKey = "", secretKey = "")
        assertEquals("Local label", s.displayName())
        assertEquals(jt, s.copy(siteName = jt).displayName())
        assertEquals("Local label", s.copy(siteName = "  ").displayName())
    }
}
