package com.thaiprompt.smschecker

import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.domain.parser.BankSmsParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BankSmsParser.
 * Tests SMS parsing for all supported Thai banks.
 */
class BankSmsParserTest {

    private lateinit var parser: BankSmsParser

    @Before
    fun setUp() {
        parser = BankSmsParser()
    }

    // ==========================================
    // Bank Identification Tests
    // ==========================================

    @Test
    fun `identifyBank returns KBANK for KBANK sender`() {
        assertEquals("KBANK", parser.identifyBank("KBANK"))
        assertEquals("KBANK", parser.identifyBank("KBank"))
        assertEquals("KBANK", parser.identifyBank("KPlus"))
    }

    @Test
    fun `identifyBank returns SCB for SCB sender`() {
        assertEquals("SCB", parser.identifyBank("SCB"))
        assertEquals("SCB", parser.identifyBank("SCBeasy"))
        assertEquals("SCB", parser.identifyBank("SCBEASY"))
    }

    @Test
    fun `identifyBank returns KTB for Krungthai sender`() {
        assertEquals("KTB", parser.identifyBank("KTB"))
        assertEquals("KTB", parser.identifyBank("Krungthai"))
    }

    @Test
    fun `identifyBank returns BBL for Bangkok Bank sender`() {
        assertEquals("BBL", parser.identifyBank("BBL"))
        assertEquals("BBL", parser.identifyBank("Bangkok Bank"))
    }

    @Test
    fun `identifyBank returns GSB for MyMo sender`() {
        assertEquals("GSB", parser.identifyBank("GSB"))
        assertEquals("GSB", parser.identifyBank("MyMo"))
    }

    @Test
    fun `identifyBank returns BAY for Krungsri sender`() {
        assertEquals("BAY", parser.identifyBank("BAY"))
        assertEquals("BAY", parser.identifyBank("KMA"))
        assertEquals("BAY", parser.identifyBank("Krungsri"))
    }

    @Test
    fun `identifyBank returns TTB for ttb sender`() {
        assertEquals("TTB", parser.identifyBank("TTB"))
        assertEquals("TTB", parser.identifyBank("ttb"))
        assertEquals("TTB", parser.identifyBank("TMB"))
    }

    @Test
    fun `identifyBank returns null for unknown sender`() {
        assertNull(parser.identifyBank("UnknownBank"))
        assertNull(parser.identifyBank("SPAM"))
        assertNull(parser.identifyBank("12345"))
    }

    // ==========================================
    // Credit (Money In) Parsing Tests
    // ==========================================

    @Test
    fun `parse KBANK credit SMS with Thai keywords`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: รับโอน 500.37 บ. บช xxx1234 จาก นายทดสอบ Ref.ABC123 29/01 14:30",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("KBANK", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("500.37", result?.amount)
    }

    @Test
    fun `parse SCB credit SMS`() {
        val result = parser.parse(
            "SCB",
            "SCB: เงินเข้า THB 2,500.50 บช.xxx9012 จาก บริษัท ทดสอบ Ref No.REF001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("SCB", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("2500.50", result?.amount)
    }

    @Test
    fun `parse KTB credit SMS in English`() {
        val result = parser.parse(
            "KTB",
            "KTB: Transfer In THB 3,000.25 A/C xxx3456 From Test User Ref.TFR001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("KTB", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("3000.25", result?.amount)
    }

    @Test
    fun `parse BBL credit SMS with Received keyword`() {
        val result = parser.parse(
            "BBL",
            "BBL: Received ฿5,000.00 Acct xxx7890 From Company ABC Ref.RCV001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("BBL", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("5000.00", result?.amount)
    }

    @Test
    fun `parse GSB credit SMS`() {
        val result = parser.parse(
            "MyMo",
            "MyMo: รับโอน 1,000.00 บาท บัญชี xxx2345 จาก นายโอน อ้างอิง GSB001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("GSB", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("1000.00", result?.amount)
    }

    @Test
    fun `parse credit SMS with CR keyword`() {
        val result = parser.parse(
            "ttb",
            "ttb: CR THB 900.00 A/C *1234 From Test Ref TTB001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("TTB", result?.bank)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("900.00", result?.amount)
    }

    @Test
    fun `parse credit SMS with Deposit keyword`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: Deposit THB 750.00 A/C xxx5678",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result?.type)
        assertEquals("750.00", result?.amount)
    }

    // ==========================================
    // Debit (Money Out) Parsing Tests
    // ==========================================

    @Test
    fun `parse KBANK debit SMS`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: โอนออก 1,000.00 บ. บช xxx5678 ไปยัง นายรับเงิน Ref.XYZ789",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("KBANK", result?.bank)
        assertEquals(TransactionType.DEBIT, result?.type)
        assertEquals("1000.00", result?.amount)
    }

    @Test
    fun `parse debit SMS with Payment keyword`() {
        val result = parser.parse(
            "SCB",
            "SCB: ชำระ THB 800.00 บช.xxx9012 ไปยัง ร้านค้า Ref No.PAY001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result?.type)
        assertEquals("800.00", result?.amount)
    }

    @Test
    fun `parse debit SMS with DR keyword`() {
        val result = parser.parse(
            "ttb",
            "ttb: DR THB 450.00 A/C *1234 Payment Shop Ref TTB002",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result?.type)
        assertEquals("450.00", result?.amount)
    }

    @Test
    fun `parse debit SMS with Withdraw keyword`() {
        val result = parser.parse(
            "KTB",
            "KTB: Withdraw THB 2,000.00 A/C xxx3456",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result?.type)
        assertEquals("2000.00", result?.amount)
    }

    @Test
    fun `parse debit SMS with Transfer Out keyword`() {
        val result = parser.parse(
            "BBL",
            "BBL: Transfer Out ฿2,000.00 Acct xxx7890 To Receiver Ref.TFR002",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result?.type)
        assertEquals("2000.00", result?.amount)
    }

    // ==========================================
    // Amount Parsing Tests
    // ==========================================

    @Test
    fun `parse amount without decimals`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: รับโอน 500 บ. บช xxx1234",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("500.00", result?.amount)
    }

    @Test
    fun `parse amount with single decimal`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: รับโอน 500.3 บ. บช xxx1234",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        // Amount should be normalized to 2 decimal places
        assertEquals("500.30", result?.amount)
    }

    @Test
    fun `parse amount with comma thousands separator`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: รับโอน 1,500,000.50 บ. บช xxx1234",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("1500000.50", result?.amount)
    }

    @Test
    fun `parse amount with THB prefix`() {
        val result = parser.parse(
            "SCB",
            "SCB: เงินเข้า THB 2,500.50 บช.xxx9012",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("2500.50", result?.amount)
    }

    @Test
    fun `parse amount with baht symbol`() {
        val result = parser.parse(
            "BBL",
            "BBL: Received ฿5,000.00 Acct xxx7890",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("5000.00", result?.amount)
    }

    // ==========================================
    // Non-bank SMS Tests
    // ==========================================

    @Test
    fun `return null for non-bank sender`() {
        val result = parser.parse(
            "SPAM",
            "You won 1,000,000 THB! Click here!",
            System.currentTimeMillis()
        )

        assertNull(result)
    }

    @Test
    fun `return null for bank SMS without transaction keywords`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: ยอดคงเหลือ 50,000.00 บาท",
            System.currentTimeMillis()
        )

        // This is a balance inquiry, not a transaction
        // Should only match if credit/debit keywords are present
        assertNull(result)
    }

    @Test
    fun `isBankTransactionSms returns false for unknown sender`() {
        assertFalse(parser.isBankTransactionSms("UNKNOWN", "Some message with 500.00"))
    }

    @Test
    fun `isBankTransactionSms returns true for valid bank SMS`() {
        assertTrue(parser.isBankTransactionSms("KBANK", "KBANK: รับโอน 500.37 บ. บช xxx1234"))
    }

    // ==========================================
    // Reference Number Tests
    // ==========================================

    @Test
    fun `extract reference number with Ref prefix`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: รับโอน 500.37 บ. บช xxx1234 Ref.ABC123",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("ABC123", result?.referenceNumber)
    }

    @Test
    fun `extract reference with Thai keyword`() {
        val result = parser.parse(
            "MyMo",
            "MyMo: รับโอน 1,000.00 บาท บัญชี xxx2345 อ้างอิง GSB001",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("GSB001", result?.referenceNumber)
    }

    // ==========================================
    // Account Number Tests
    // ==========================================

    @Test
    fun `extract account number with xxx prefix`() {
        val result = parser.parse(
            "KBANK",
            "KBANK: รับโอน 500.37 บ. บช xxx1234",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("1234", result?.accountNumber)
    }

    @Test
    fun `extract account number with asterisk prefix`() {
        val result = parser.parse(
            "ttb",
            "ttb: CR THB 900.00 A/C *5678",
            System.currentTimeMillis()
        )

        assertNotNull(result)
        assertEquals("5678", result?.accountNumber)
    }
}
