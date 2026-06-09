package com.thaiprompt.smschecker

import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.domain.parser.BankSmsParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Regression tests สำหรับบั๊ก KBANK รูปแบบใหม่ (2026-06)
 *
 * อาการ: KBANK SMS รูปแบบ "X5349 รับโอนจาก X-3516 99.00 คงเหลือ 2,390.30 บ."
 *   parser เผลอจับ "5349" (เลขบัญชีปลายทางของร้าน) เป็นยอดเงิน
 *   เพราะ pattern "amount + รับ" ไปแมตช์ "5349 รับ(โอน)"
 *   → server ได้ยอด 5349 ที่ไม่ตรงบิลใด → บิลไม่ตัด + แอดมินเห็นยอด 5000+ ซ้ำๆ
 *
 * Fix: เพิ่ม pattern จับยอดจริงที่ขนาบระหว่างเลขบัญชีผู้โอนกับ "คงเหลือ"
 *   + กัน "รับ" ไม่ให้แมตช์จุดเริ่มของ "รับโอน"/"รับเงิน"
 */
class BankSmsParserKbankRegressionTest {

    private lateinit var parser: BankSmsParser

    @Before
    fun setUp() {
        parser = BankSmsParser()
    }

    /**
     * SMS จริงจาก prod (transaction id 38) — รูปแบบเต็มพร้อมวันเวลา + "บช" นำหน้า
     * "09/06/69 12:59 บช X-5349 รับโอนจาก X-9409 39.00 คงเหลือ 945.36 บ."
     * ต้องได้ยอด 39.00 (ไม่ใช่ 5349 = บัญชีร้าน) + แยกผู้โอน = X-9409 + ทิศทาง CREDIT
     */
    @Test
    fun `KBANK real prod SMS with date prefix parses 39 not account`() {
        val result = parser.parse(
            "KBank",
            "09/06/69 12:59 บช X-5349 รับโอนจาก X-9409 39.00 คงเหลือ 945.36 บ.",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("KBANK", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("39.00", result?.amount)
        assertNotEquals("5349.00", result?.amount)
        // แยก field: ผู้โอน = X-9409 (ไม่ปนยอด/คงเหลือ)
        assertEquals("X-9409", result?.senderOrReceiver)
    }

    /**
     * เคสบั๊กหลัก: ยอด 99.00 ต้องถูกจับ ไม่ใช่เลขบัญชี 5349
     */
    @Test
    fun `KBANK new format captures real amount not account number (99)`() {
        val result = parser.parse(
            "KBANK",
            "X5349 รับโอนจาก X-3516 99.00 คงเหลือ 2,390.30 บ.",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("KBANK", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("99.00", result?.amount)
        // กันการถอยหลังลงคลอง: ห้ามเป็นเลขบัญชีร้าน (ลงท้าย 5349) เด็ดขาด
        assertNotEquals("5349.00", result?.amount)
    }

    /**
     * เคสเดียวกันแต่มี "บช." นำหน้า + ยอด 39.63
     */
    @Test
    fun `KBANK new format with bch prefix captures 39_63`() {
        val result = parser.parse(
            "KBANK",
            "บช.X5349 รับโอนจาก X-3042 39.63 คงเหลือ 1,083.36 บ.",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("39.63", result?.amount)
        assertNotEquals("5349.00", result?.amount)
    }

    /**
     * ยอดเต็มจำนวน (ไม่มีทศนิยม) ในรูปแบบใหม่ → 39.00
     */
    @Test
    fun `KBANK new format whole-baht amount captures 39`() {
        val result = parser.parse(
            "KBANK",
            "X5349 รับโอนจาก X-9409 39.00 คงเหลือ 812.00 บ.",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("39.00", result?.amount)
    }

    /**
     * รูปแบบ KBANK เดิม "เงิน..บ.เข้าบช.." ต้องยังทำงานปกติ (ไม่ regress)
     */
    @Test
    fun `KBANK old compact format still works`() {
        val result = parser.parse(
            "KBANK",
            "เงิน99.36บ.เข้าบช.X7868 จาก นายเอ",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("99.36", result?.amount)
    }

    /**
     * Collateral guard: SCB ที่มี "คงเหลือ" ต่อท้าย ต้องจับยอดโอน (2,500.50)
     *   ไม่ใช่เลขบัญชี/ยอดคงเหลือ — pattern KBANK ใหม่ต้องไม่ดักผิดธนาคารอื่น
     */
    @Test
    fun `SCB with trailing balance still captures transfer amount`() {
        val result = parser.parse(
            "SCB",
            "SCB: เงินเข้า 2,500.50 บาท บช.xxx9012 คงเหลือ 9,999.00 บาท",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("2500.50", result?.amount)
    }

    /**
     * SMS แจ้งยอดคงเหลือล้วน ๆ (ไม่มี transaction) → ต้อง return null
     */
    @Test
    fun `balance-only KBANK SMS returns null`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: ยอดคงเหลือ 50,000.00 บาท",
            System.currentTimeMillis()
        )

        assertNull(result)
    }
}
