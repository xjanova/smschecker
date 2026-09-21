package com.thaiprompt.smschecker.util

import java.net.IDN

/**
 * 🌐 (2026-09-21) ป้าย "บิลนี้มาจากเว็บไหน" บนการ์ดบิล
 *
 * เครื่องเดียวลงทะเบียนได้หลายเว็บ (Thaiprompt + จันทรา.online ใช้บัญชีธนาคาร/มือถือเดียวกัน)
 * → ป้ายต้องมาจาก ServerConfig ที่ลงทะเบียนไว้ในเครื่องจริง ไม่ใช่ชื่อที่ server ส่งมาอย่างเดียว
 */
object ServerLabel {

    /**
     * โดเมนที่คนอ่านออกจาก baseUrl
     *   "https://xn--...online/"         → "จันทรา.online" (IDN punycode กลับเป็นภาษาไทย)
     *   "www.thaiprompt.online:8443/api" → "thaiprompt.online"
     * อ่านไม่ออก → null
     */
    fun host(baseUrl: String?): String? {
        val raw = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val authority = raw.substringAfter("://", raw)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = authority.substringBefore(':').trimEnd('.').lowercase()
        if (host.isEmpty()) return null
        val unicode = runCatching { IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
        return unicode.removePrefix("www.").takeIf { it.isNotEmpty() }
    }

    /**
     * ชื่อที่ตั้งตอนลงทะเบียน กับโดเมน คือเว็บเดียวกันที่เขียนซ้ำกันไหม
     * (เช่นตั้งชื่อ "จันทรา.online" + โดเมน xn--... → โชว์แค่ครั้งเดียว ไม่ต้อง "จันทรา.online · จันทรา.online")
     */
    fun sameSite(name: String?, host: String?): Boolean {
        if (name.isNullOrBlank() || host.isNullOrBlank()) return false
        val asHost = host(name) ?: return false
        return asHost.equals(host, ignoreCase = true)
    }
}
