package com.thaiprompt.smschecker.domain.parser

import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import com.thaiprompt.smschecker.data.model.TransactionType

/**
 * Parses Thai bank SMS messages to extract transaction details.
 *
 * Supported banks: KBANK, SCB, KTB, BBL, GSB, BAY, TTB, PromptPay
 * Detects: amount, transaction type (credit/debit), account number, reference
 */
class BankSmsParser {

    data class ParseResult(
        val bank: String,
        val type: TransactionType,
        val amount: String,
        val accountNumber: String,
        val senderOrReceiver: String,
        val referenceNumber: String
    )

    private var customRules: List<SmsSenderRule> = emptyList()

    fun setCustomRules(rules: List<SmsSenderRule>) {
        this.customRules = rules
    }

    companion object {
        // Known bank SMS sender addresses
        private val BANK_SENDERS = mapOf(
            "KBANK" to listOf("KBANK", "KBank", "K-Bank", "KPlus"),
            "SCB" to listOf("SCB", "SCBeasy", "SCBEasy", "SCBEASY"),
            "KTB" to listOf("KTB", "Krungthai", "KTB-BANK"),
            "BBL" to listOf("BBL", "Bangkok Bank", "BualuangiBanking"),
            "GSB" to listOf("GSB", "MyMo", "MYMO"),
            "BAY" to listOf("BAY", "KMA", "Krungsri"),
            "TTB" to listOf("TTB", "ttb", "TMB", "Thanachart", "ttbbank"),
            "PROMPTPAY" to listOf("PromptPay", "PROMPTPAY"),
        )

        // Credit patterns (เงินเข้า)
        private val CREDIT_PATTERNS = listOf(
            // KBANK patterns
            Regex("""(?:รับโอน|เงินเข้า|โอนเข้า|Received|Transfer In|CR|credit|PromptPay\s*รับ)[\s:]*(?:THB\s*|฿\s*|บ\.?\s*)?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // Amount first then action
            Regex("""(?:THB\s*|฿\s*)?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:บ\.?|บาท|THB)?\s*(?:เข้า|รับ|CR|credit|Received)""", RegexOption.IGNORE_CASE),
            // English pattern
            Regex("""(?:Deposit|received|Transfer\s*In)\s*(?:THB\s*|฿\s*)?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        )

        // Debit patterns (เงินออก)
        private val DEBIT_PATTERNS = listOf(
            Regex("""(?:โอนออก|โอนเงิน|จ่าย|ชำระ|ถอน|Transfer Out|DR|debit|Payment|Paid|หัก)[\s:]*(?:THB\s*|฿\s*|บ\.?\s*)?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:THB\s*|฿\s*)?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:บ\.?|บาท|THB)?\s*(?:ออก|จ่าย|หัก|DR|debit|Paid)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Withdraw|Payment|Purchase)\s*(?:THB\s*|฿\s*)?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        )

        // Account number patterns
        private val ACCOUNT_PATTERNS = listOf(
            Regex("""(?:บช|a/c|acct?|account|บัญชี)[\s.:]*[Xx*]*(\d{3,4})""", RegexOption.IGNORE_CASE),
            Regex("""[Xx*]+(\d{4})\b"""),
            Regex("""(\d{3})-\d-\d{5}-(\d)"""), // Thai bank account format
        )

        // Reference number patterns
        private val REF_PATTERNS = listOf(
            Regex("""(?:Ref|อ้างอิง|ref\.?no\.?|รหัส)[\s.:]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:เลขที่|No\.?)[\s.:]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
        )

        // Sender/Receiver name patterns
        private val NAME_PATTERNS = listOf(
            Regex("""(?:จาก|from|ผู้โอน)[\s:]*(.+?)(?:\s*(?:บช|a/c|Ref|เวลา|\d{2}[:/]\d{2}|$))""", RegexOption.IGNORE_CASE),
            Regex("""(?:ไปยัง|ถึง|to|ผู้รับ)[\s:]*(.+?)(?:\s*(?:บช|a/c|Ref|เวลา|\d{2}[:/]\d{2}|$))""", RegexOption.IGNORE_CASE),
        )
    }

    /**
     * Identifies the bank from the SMS sender address.
     * Returns null if not a recognized bank.
     */
    fun identifyBank(senderAddress: String): String? {
        val normalizedSender = senderAddress.trim()

        // Check custom rules first
        for (rule in customRules) {
            if (rule.isActive && normalizedSender.contains(rule.senderAddress, ignoreCase = true)) {
                return rule.bankCode
            }
        }

        // Fall back to hardcoded bank senders
        for ((bank, senders) in BANK_SENDERS) {
            if (senders.any { normalizedSender.contains(it, ignoreCase = true) }) {
                return bank
            }
        }
        return null
    }

    /**
     * Check if an SMS is a bank transaction message.
     */
    fun isBankTransactionSms(senderAddress: String, message: String): Boolean {
        if (identifyBank(senderAddress) == null) return false
        return tryParseCredit(message) != null || tryParseDebit(message) != null
    }

    /**
     * Parse the SMS message and extract transaction details.
     * Returns null if the message is not a bank transaction SMS.
     */
    fun parse(senderAddress: String, message: String, timestamp: Long): BankTransaction? {
        val bank = identifyBank(senderAddress) ?: return null
        val parseResult = parseMessage(bank, message) ?: return null

        return BankTransaction(
            bank = parseResult.bank,
            type = parseResult.type,
            amount = parseResult.amount,
            accountNumber = parseResult.accountNumber,
            senderOrReceiver = parseResult.senderOrReceiver,
            referenceNumber = parseResult.referenceNumber,
            rawMessage = message,
            senderAddress = senderAddress,
            timestamp = timestamp
        )
    }

    private fun parseMessage(bank: String, message: String): ParseResult? {
        // Try credit first, then debit
        val creditAmount = tryParseCredit(message)
        val debitAmount = tryParseDebit(message)

        // Determine transaction type
        val (type, amount) = when {
            creditAmount != null && debitAmount != null -> {
                // Both matched - use context to decide
                // If credit keywords appear before debit keywords, it's credit
                val creditIdx = findFirstKeywordIndex(message, listOf("รับโอน", "เงินเข้า", "โอนเข้า", "Received", "Transfer In", "CR", "credit", "Deposit"))
                val debitIdx = findFirstKeywordIndex(message, listOf("โอนออก", "จ่าย", "ชำระ", "ถอน", "Transfer Out", "DR", "debit", "Payment", "หัก"))
                if (creditIdx != -1 && (debitIdx == -1 || creditIdx < debitIdx)) {
                    TransactionType.CREDIT to creditAmount
                } else {
                    TransactionType.DEBIT to debitAmount
                }
            }
            creditAmount != null -> TransactionType.CREDIT to creditAmount
            debitAmount != null -> TransactionType.DEBIT to debitAmount
            else -> return null
        }

        val accountNumber = extractAccountNumber(message)
        val referenceNumber = extractReference(message)
        val senderOrReceiver = extractName(message)

        return ParseResult(
            bank = bank,
            type = type,
            amount = normalizeAmount(amount),
            accountNumber = accountNumber,
            senderOrReceiver = senderOrReceiver,
            referenceNumber = referenceNumber
        )
    }

    private fun tryParseCredit(message: String): String? {
        for (pattern in CREDIT_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun tryParseDebit(message: String): String? {
        for (pattern in DEBIT_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractAccountNumber(message: String): String {
        for (pattern in ACCOUNT_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues.last { it.isNotEmpty() && it.all { c -> c.isDigit() } }
            }
        }
        return ""
    }

    private fun extractReference(message: String): String {
        for (pattern in REF_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    private fun extractName(message: String): String {
        for (pattern in NAME_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1].trim().take(100)
            }
        }
        return ""
    }

    private fun normalizeAmount(amountStr: String): String {
        // Remove commas and ensure 2 decimal places
        val cleaned = amountStr.replace(",", "")
        return if (cleaned.contains(".")) {
            val parts = cleaned.split(".")
            "${parts[0]}.${parts[1].padEnd(2, '0').take(2)}"
        } else {
            "$cleaned.00"
        }
    }

    private fun findFirstKeywordIndex(message: String, keywords: List<String>): Int {
        val lowerMessage = message.lowercase()
        return keywords.map { lowerMessage.indexOf(it.lowercase()) }
            .filter { it >= 0 }
            .minOrNull() ?: -1
    }
}
