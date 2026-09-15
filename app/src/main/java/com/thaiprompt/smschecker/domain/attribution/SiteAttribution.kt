package com.thaiprompt.smschecker.domain.attribution

import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.ServerConfig
import java.net.IDN

/**
 * 🌐 (2026-09-15) Multi-site attribution — CONTRACT §E
 *
 * เครื่องเดียวอ่าน SMS ของบัญชีธนาคารเดียว แต่ผูกหลายเซิร์ฟ (เช่น Thaiprompt + จันทรา.online)
 * ยอดเงินเข้าแต่ละยอดต้องบอกได้ว่าเป็นของ "เว็บไหน" และห้ามอนุมัติยอดเดียวที่สองเว็บ
 *
 * ไฟล์นี้เป็น logic ล้วน (ไม่มี Android) — ทดสอบได้ใน unit test:
 *  - [SiteMatchDecision]      ตัดสินผล GET /orders/match จากทุกเซิร์ฟแบบ deterministic
 *  - [SiteAttributionMerger]  รวมหลักฐานหลายทาง (/orders/match, /notify, external_site) เป็นสถานะเดียว
 *  - [SiteNames]              ตั้งชื่อเว็บ + จับคู่ชื่อเว็บจาก hint กับเซิร์ฟในเครื่อง
 */
data class SiteAttribution(
    val serverId: Long? = null,
    val siteName: String? = null,
    val source: String? = null,
    val conflict: Boolean = false,
    val conflictSites: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = serverId == null && siteName == null && !conflict
    val isHint: Boolean get() = !conflict && source == SOURCE_HINT

    /** ค่า conflictSites สำหรับเก็บลง DB (คั่นด้วย '\n' — ชื่อเว็บผ่าน [SiteNames.clean] แล้วจึงไม่มี '\n') */
    fun conflictSitesColumn(): String? =
        conflictSites.takeIf { it.isNotEmpty() }?.joinToString(STORAGE_SEPARATOR)

    companion object {
        /** /orders/match ตรงที่เซิร์ฟเดียว (หรือ reconciler / smart-auto อนุมัติบิลของเซิร์ฟนั้น) */
        const val SOURCE_MATCH = "match"
        /** /notify ตอบ matched=true */
        const val SOURCE_NOTIFY = "notify"
        /** external_site hint (§C3) — ไม่ใช่ match ไม่เคยใช้อนุมัติ */
        const val SOURCE_HINT = "hint"
        /** ตรงกับบิลมากกว่า 1 เว็บ */
        const val SOURCE_CONFLICT = "conflict"

        const val STORAGE_SEPARATOR = "\n"

        fun of(tx: BankTransaction): SiteAttribution = SiteAttribution(
            serverId = tx.matchedServerId,
            siteName = tx.matchedSiteName,
            source = tx.attributionSource,
            conflict = tx.matchConflict,
            conflictSites = tx.conflictSiteList()
        )
    }
}

/** หลักฐานใหม่ 1 ชิ้นว่ายอดนี้เป็นของเว็บไหน */
sealed class AttributionEvent {
    /** เซิร์ฟ [serverId] ยืนยันว่ายอดนี้ตรงกับบิลของมัน */
    data class Matched(val serverId: Long, val siteName: String, val source: String) : AttributionEvent()

    /**
     * ยอดนี้ตรงกับบิลหลายเว็บพร้อมกัน → แอพไม่อนุมัติที่ไหนเลย
     * [alreadyApprovedAt] = เว็บที่เซิร์ฟตอบมาว่าอนุมัติไปแล้ว (เซิร์ฟ auto-confirm เองใน /orders/match)
     *   ใช้แค่ในข้อความแจ้งเตือน ไม่ได้เก็บลง DB
     */
    data class Conflict(
        val siteNames: List<String>,
        val alreadyApprovedAt: List<String> = emptyList()
    ) : AttributionEvent()

    /** เซิร์ฟ [fromServerId] บอกว่ายอดนี้เป็นของเว็บ [siteName] (external_site) — [serverId] = เซิร์ฟในเครื่องที่ตรงกับชื่อนั้น (ถ้ามี) */
    data class Hint(val serverId: Long?, val siteName: String, val fromServerId: Long? = null) : AttributionEvent()
}

object SiteAttributionMerger {

    /**
     * กติกา (เรียงตามความสำคัญ):
     *  1. conflict ชนะทุกอย่าง — เมื่อชนแล้วจะไม่กลับไปเป็นเว็บเดียวเอง (แอดมินต้องตรวจ)
     *  2. match จริง (match/notify) ชนะ hint และชนะ "ยังไม่รู้"
     *  3. match จริงจากเซิร์ฟเดิมซ้ำ = ไม่เปลี่ยน (ไม่ให้ชื่อสลับไปมาระหว่าง /orders/match กับ /notify)
     *  4. match จริงจาก "อีกเซิร์ฟ" หลังจากมีเจ้าของแล้ว = conflict (สองเว็บรับยอดเดียวกัน)
     *  5. hint ใช้ได้เฉพาะตอนยังไม่มีข้อมูลใดๆ เลย
     */
    fun merge(current: SiteAttribution, event: AttributionEvent): SiteAttribution = when (event) {
        is AttributionEvent.Conflict -> SiteAttribution(
            source = SiteAttribution.SOURCE_CONFLICT,
            conflict = true,
            conflictSites = union(knownSites(current), event.siteNames)
        )

        is AttributionEvent.Matched -> when {
            current.conflict ->
                current.copy(conflictSites = union(current.conflictSites, listOf(event.siteName)))
            current.isEmpty || current.isHint ->
                SiteAttribution(serverId = event.serverId, siteName = event.siteName, source = event.source)
            current.serverId == event.serverId ->
                current
            else -> SiteAttribution(
                source = SiteAttribution.SOURCE_CONFLICT,
                conflict = true,
                conflictSites = union(listOfNotNull(current.siteName), listOf(event.siteName))
            )
        }

        is AttributionEvent.Hint ->
            if (current.isEmpty) {
                SiteAttribution(serverId = event.serverId, siteName = event.siteName, source = SiteAttribution.SOURCE_HINT)
            } else {
                current
            }
    }

    private fun knownSites(current: SiteAttribution): List<String> = when {
        current.conflict -> current.conflictSites
        current.isHint || current.isEmpty -> emptyList()
        else -> listOfNotNull(current.siteName)
    }

    private fun union(a: List<String>, b: List<String>): List<String> =
        (a + b).mapNotNull { SiteNames.clean(it) }.distinct()
}

/**
 * ผล GET /orders/match ของเซิร์ฟ 1 ตัว
 * [match] != null = เซิร์ฟนี้ตอบ matched=true พร้อมบิล
 */
data class SiteMatchReply<T>(
    val serverId: Long,
    val siteName: String,
    val match: T?,
    val externalSite: String? = null
)

/**
 * ตัดสินผลจาก "ทุกเซิร์ฟ" หลังรอครบแล้วเท่านั้น (แทนของเดิมที่ coroutine ตัวสุดท้ายที่เขียนตัวแปรชนะ)
 *  - 0 เซิร์ฟตรง  → ไม่มี winner (ทาง orphan เดิม) — ถ้ามี external_site เก็บไว้เป็น hint
 *  - 1 เซิร์ฟตรง  → [winner] = อนุมัติที่เซิร์ฟนั้น (ทางเดิม)
 *  - ≥2 เซิร์ฟตรง → [isConflict] = ห้ามอนุมัติที่ไหนเลย
 * ลำดับใน [matches] = ลำดับเซิร์ฟที่ส่งเข้ามา (deterministic)
 */
data class SiteMatchDecision<T>(
    val matches: List<SiteMatchReply<T>>,
    val externalSite: String? = null,
    val externalSiteFromServerId: Long? = null
) {
    val winner: SiteMatchReply<T>? get() = matches.singleOrNull()
    val isConflict: Boolean get() = matches.size >= 2

    companion object {
        fun <T> decide(replies: List<SiteMatchReply<T>>): SiteMatchDecision<T> {
            val matched = replies.filter { it.match != null }.distinctBy { it.serverId }
            // hint นับเฉพาะคำตอบที่ "ไม่ match" — external_site ไม่เคยทำให้เกิดการอนุมัติ
            val hint = replies.firstOrNull { it.match == null && SiteNames.clean(it.externalSite) != null }
            return SiteMatchDecision(
                matches = matched,
                externalSite = hint?.externalSite?.let { SiteNames.clean(it) },
                externalSiteFromServerId = hint?.serverId
            )
        }
    }
}

object SiteNames {
    private const val MAX_LEN = 60

    /** ตัดช่องว่าง/ขึ้นบรรทัดซ้อน + จำกัดความยาว — null ถ้าว่าง */
    fun clean(raw: String?): String? =
        raw?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LEN)

    /** ลำดับชื่อเว็บตามสัญญา: server_name → website_name (จากเซิร์ฟ) → ชื่อในเครื่อง */
    fun resolve(serverName: String?, websiteName: String?, fallback: String?): String? =
        clean(serverName) ?: clean(websiteName) ?: clean(fallback)

    /**
     * host แบบ ASCII ตัวเล็ก ตัด www. — รองรับทั้ง URL และชื่อโดเมนเปล่า รวมโดเมนไทย
     * (จันทรา.online ⇄ xn--82c4af5bzdj.online) — null ถ้าไม่ใช่รูปโดเมน
     * แยก host เองแทน java.net.URI เพราะ URI.getHost() คืน null เมื่อ host เป็นอักษรไทย
     */
    fun hostOf(urlOrHost: String?): String? {
        val raw = urlOrHost?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val host = raw.substringAfter("://", raw)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .trim()
            .trimEnd('.')
        if (!host.contains('.') || host.contains(' ')) return null
        return try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase().removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * หาเซิร์ฟในเครื่องที่ตรงกับชื่อเว็บจาก external_site
     *  1) ชื่อตรงกัน (displayName / siteName / name) ไม่สนตัวพิมพ์
     *  2) ชื่อเป็นโดเมน → host ตรงกับ baseUrl ของเซิร์ฟ
     * ไม่เจอ = null (เก็บแค่ชื่อไว้แสดง)
     */
    fun serverIdForSite(site: String?, servers: List<ServerConfig>): Long? {
        val wanted = clean(site)?.lowercase() ?: return null
        servers.firstOrNull { s ->
            listOfNotNull(s.displayName(), s.siteName, s.name)
                .any { clean(it)?.lowercase() == wanted }
        }?.let { return it.id }
        val wantedHost = hostOf(site) ?: return null
        return servers.firstOrNull { hostOf(it.baseUrl) == wantedHost }?.id
    }
}
