package com.thaiprompt.smschecker

import com.thaiprompt.smschecker.util.ServerLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ป้าย "บิลนี้มาจากเว็บไหน" บนการ์ดบิล — แปลง ServerConfig.baseUrl เป็นโดเมนที่คนอ่านออก
 */
class ServerLabelTest {

    @Test
    fun `plain https url gives its domain`() {
        assertEquals("thaiprompt.online", ServerLabel.host("https://thaiprompt.online"))
        assertEquals("thaiprompt.online", ServerLabel.host("https://thaiprompt.online/"))
    }

    @Test
    fun `www port path query and userinfo are stripped`() {
        assertEquals("thaiprompt.online", ServerLabel.host("https://www.thaiprompt.online:8443/api/v1?x=1#top"))
        assertEquals("example.com", ServerLabel.host("http://user:pass@example.com/"))
    }

    @Test
    fun `url without a scheme still works`() {
        assertEquals("thaiprompt.online", ServerLabel.host("  thaiprompt.online/api  "))
    }

    @Test
    fun `punycode host is shown in thai`() {
        val puny = java.net.IDN.toASCII("จันทรา.online")
        assertEquals("จันทรา.online", ServerLabel.host("https://$puny/"))
    }

    @Test
    fun `thai host stays thai and uppercase is folded`() {
        assertEquals("จันทรา.online", ServerLabel.host("https://จันทรา.online"))
        assertEquals("thaiprompt.online", ServerLabel.host("HTTPS://ThaiPrompt.Online"))
    }

    @Test
    fun `blank or broken input gives null`() {
        assertNull(ServerLabel.host(null))
        assertNull(ServerLabel.host(""))
        assertNull(ServerLabel.host("   "))
        assertNull(ServerLabel.host("https:///path"))
    }

    @Test
    fun `name that is just the domain is not repeated`() {
        assertTrue(ServerLabel.sameSite("จันทรา.online", "จันทรา.online"))
        assertTrue(ServerLabel.sameSite("www.Thaiprompt.online", "thaiprompt.online"))
        assertFalse(ServerLabel.sameSite("Thaiprompt", "thaiprompt.online"))
        assertFalse(ServerLabel.sameSite("แม่หมอจันทรา", "จันทรา.online"))
        assertFalse(ServerLabel.sameSite(null, "จันทรา.online"))
        assertFalse(ServerLabel.sameSite("จันทรา.online", null))
    }
}
