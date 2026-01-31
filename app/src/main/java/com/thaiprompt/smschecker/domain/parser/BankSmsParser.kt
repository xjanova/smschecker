package com.thaiprompt.smschecker.domain.parser

import android.util.Log
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import com.thaiprompt.smschecker.data.model.TransactionType

/**
 * Production-grade Thai bank SMS & notification parser.
 *
 * Covers real-world message formats from all major Thai banks:
 * KBANK, SCB, KTB, BBL, GSB, BAY, TTB, PromptPay, CIMB, KKP, LH, TISCO, UOB, ICBC
 *
 * Design principles:
 * - Multi-layer parsing: bank-specific patterns first, generic fallback second
 * - Handles both Thai and English SMS variants
 * - Handles push notification format (title + body combined)
 * - Amount regex covers: 1,500.00 / 1500.00 / 1,500 / 1500 / ฿1,500 / THB 1,500.00
 * - Account patterns cover: บ/ชX1234 / บช.X7868 / xxx1234 / a/c *1234 / บัญชี X1234
 * - Logs failed parses for debugging without crashing
 */
class BankSmsParser {

    companion object {
        private const val TAG = "BankSmsParser"

        // =====================================================================
        // AMOUNT REGEX - shared across all patterns
        // Matches: 1,500.00 | 1500.00 | 1,500 | 500 | 0.50
        // =====================================================================
        private const val AMT = """(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""

        // Optional currency prefix/suffix
        private const val CUR_PRE = """(?:THB\s*|฿\s*|Bt\s*|บ\.?\s*)?"""
        private const val CUR_SUF = """(?:\s*บ\.?|\s*บาท|\s*THB|\s*Bt)?"""

        // =====================================================================
        // SENDER ADDRESS DATABASE
        // Key = bank code, Value = known sender addresses (SMS shortcodes + app names)
        // Uses contains() matching so "KBANK-NOTIF" matches "KBANK"
        // =====================================================================
        private val BANK_SENDERS = mapOf(
            "KBANK" to listOf(
                "KBANK", "KBank", "K-Bank", "KPlus", "K PLUS", "K-Plus",
                "KBankLive", "KASIKORN", "kasikornbank"
            ),
            "SCB" to listOf(
                "SCB", "SCBeasy", "SCBEasy", "SCBEASY", "SCB EASY",
                "SCBConnect", "SCB Connect", "SICCO" // SCB credit card
            ),
            "KTB" to listOf(
                "KTB", "Krungthai", "KTB-BANK", "KTB BANK",
                "KrightaiNEXT", "Krungthai NEXT", "KTBNEXT"
            ),
            "BBL" to listOf(
                "BBL", "BangkokBank", "Bangkok Bank", "BualuangiBanking",
                "Bualuang", "BualuangMB", "BBLM"
            ),
            "GSB" to listOf(
                "GSB", "MyMo", "MYMO", "GovSavings", "ออมสิน"
            ),
            "BAY" to listOf(
                "BAY", "KMA", "Krungsri", "KRUNGSRI", "KrungsriOnline",
                "Krungsri Online"
            ),
            "TTB" to listOf(
                "TTB", "ttb", "TMB", "Thanachart", "ttbbank", "ttb bank",
                "ttbtouch", "ttb touch", "TBANK"
            ),
            "PROMPTPAY" to listOf(
                "PromptPay", "PROMPTPAY", "พร้อมเพย์"
            ),
            "CIMB" to listOf(
                "CIMB", "CIMBThai", "CIMB THAI"
            ),
            "KKP" to listOf(
                "KKP", "KiatlungkarnFinance", "เกียรตินาคินภัทร"
            ),
            "LH" to listOf(
                "LHBank", "LHBANK", "LH BANK", "แลนด์แอนด์เฮ้าส์"
            ),
            "TISCO" to listOf(
                "TISCO", "TISCOBank"
            ),
            "UOB" to listOf(
                "UOB", "UOBT", "UOBThailand"
            ),
            "ICBC" to listOf(
                "ICBC", "ICBCThai"
            ),
        )

        // =====================================================================
        // CREDIT (เงินเข้า) PATTERNS - ordered from most specific to generic
        // =====================================================================
        private val CREDIT_PATTERNS: List<Regex> by lazy { listOf(
            // === BBL format: "ฝาก/โอนเงินเข้าบ/ชX1234 ผ่านMB 5,000.00บ ใช้ได้15,000.00บ" ===
            Regex("""(?:ฝาก|โอนเงินเข้า|โอน(?:เข้า)?)\s*(?:บ/ช|บช\.?)\s*[Xx]?\d*\s*(?:ผ่าน\w+\s+)?$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // === KBANK format: "เงิน11,111.00บ.เข้าบช.X7868 ส่งจาก..." ===
            Regex("""เงิน\s*$AMT\s*(?:บ\.?|บาท)?\s*เข้า""", RegexOption.IGNORE_CASE),

            // === Generic Thai credit keywords ===
            // "รับโอน 5,000.00 บาท" / "เงินเข้า ฿5,000" / "โอนเข้า THB 5,000.00"
            Regex("""(?:รับโอน|เงินเข้า|โอนเข้า|ยอดเงินเข้า|รับเงิน|เข้าบัญชี|เข้าบ/ช|ฝากเงิน|PromptPay\s*รับ)[\s:]*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "5,000.00 บ. เข้าบช" / "5,000.00บาท เข้า" / "5,000 เข้า"
            Regex("""$CUR_PRE$AMT\s*$CUR_SUF\s*(?:เข้า(?:บ[/.]?ช|บัญชี)?|รับ)""", RegexOption.IGNORE_CASE),

            // === English patterns ===
            // "Received THB 5,000.00" / "Transfer In Bt 5,000.00" / "Deposit 5,000.00"
            Regex("""(?:Received?|Transfer\s*(?:In|to\s*(?:your|a/?c))|Deposit(?:ed)?|CR|Credit(?:ed)?|Incoming)\s*:?\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "THB 5,000.00 received" / "5,000.00 THB credited"
            Regex("""$CUR_PRE$AMT\s*$CUR_SUF\s*(?:received|credited|deposited|transfer(?:red)?\s*in)""", RegexOption.IGNORE_CASE),

            // "Deposit/transfer to your account X1234 of Bt 5,000.00" (BBL English)
            Regex("""(?:Deposit|[Tt]ransfer)\s*(?:to|into)\s*(?:your\s*)?(?:account|a/?c)\s*\w*\s*(?:of\s*)?$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // KTB English: "Transfer 17,175.00 THB to KTB-XXXXXX67569 was successful"
            // This is actually a DEBIT for the sender, but incoming for receiver — handled by context
            Regex("""(?:received?|incoming)\s+$CUR_PRE$AMT\s*$CUR_SUF""", RegexOption.IGNORE_CASE),

            // Notification format: title might be "รับเงิน" or "เงินเข้า" then body has amount
            Regex("""(?:รับเงิน|Money\s*In|เงินเข้า)\s*\n?\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "PromptPay: โอนเข้า 500.00 บาท"
            Regex("""PromptPay\s*:?\s*(?:โอนเข้า|รับ|เข้า)\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // DEBIT (เงินออก) PATTERNS - ordered from most specific to generic
        // =====================================================================
        private val DEBIT_PATTERNS: List<Regex> by lazy { listOf(
            // === BBL format: "ถอน/โอน/จ่ายเงินจากบ/ชX1234 ผ่านMB 5,000.00บ ใช้ได้15,000.00บ" ===
            Regex("""(?:ถอน|โอน|จ่ายเงิน)(?:จาก|ออก)?\s*(?:บ/ช|บช\.?)\s*[Xx]?\d*\s*(?:ผ่าน\w+\s+)?$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "เงิน5,000.00บ.ออกจากบช."
            Regex("""เงิน\s*$AMT\s*(?:บ\.?|บาท)?\s*ออก""", RegexOption.IGNORE_CASE),

            // === Generic Thai debit keywords ===
            // "โอนออก 5,000 บาท" / "จ่ายเงิน ฿5,000" / "ถอนเงิน THB 5,000"
            Regex("""(?:โอนออก|โอนเงิน|จ่ายเงิน|จ่าย|ชำระ(?:เงิน)?|ถอน(?:เงิน)?|หักเงิน|หักบ[/.]?ช|ตัดเงิน|ซื้อ|Transfer\s*Out|Payment|หัก)[\s:]*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "5,000.00บ.จ่ายจาก" / "5,000 หัก"
            Regex("""$CUR_PRE$AMT\s*$CUR_SUF\s*(?:ออก|จ่าย|หัก|ถอน|ชำระ|ตัด)""", RegexOption.IGNORE_CASE),

            // === English patterns ===
            // "Withdrawal THB 5,000.00" / "Payment 5,000.00" / "Purchase Bt 1,500"
            Regex("""(?:Withdraw(?:al|n)?|Payment|Purchase|Paid|DR|Debit(?:ed)?|Transfer(?:red)?\s*(?:Out|from)|Outgoing|Spend(?:ing)?)[\s:]*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "THB 5,000.00 paid" / "5,000.00 withdrawn"
            Regex("""$CUR_PRE$AMT\s*$CUR_SUF\s*(?:paid|withdrawn|debited|transfer(?:red)?\s*out|spent)""", RegexOption.IGNORE_CASE),

            // BBL English: "Withdrawal/transfer/payment from your account X1234 of Bt 5,000.00"
            Regex("""(?:Withdrawal|Transfer|Payment)\s*(?:from|of)\s*(?:your\s*)?(?:account|a/?c)\s*\w*\s*(?:of\s*)?$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // KTB English: "Transfer 17,175.00 THB ... was successful"
            Regex("""Transfer\s+$AMT\s*(?:THB|Bt)?\s*(?:to|from)""", RegexOption.IGNORE_CASE),

            // Notification format: "โอนเงิน" / "จ่ายเงิน" / "Money Out"
            Regex("""(?:โอนเงิน|จ่ายเงิน|Money\s*Out|เงินออก)\s*\n?\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // "PromptPay: โอนออก 500.00 บาท"
            Regex("""PromptPay\s*:?\s*(?:โอน(?:ออก)?|จ่าย|ชำระ)\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),

            // EDC / card payment: "ใช้จ่ายบัตร 1,500.00"
            Regex("""(?:ใช้จ่าย(?:บัตร)?|card\s*spend(?:ing)?)\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // ACCOUNT NUMBER PATTERNS
        // BBL: "บ/ชX1234" / KBANK: "บช.X7868" / Generic: "xxx1234" / "a/c *1234"
        // =====================================================================
        private val ACCOUNT_PATTERNS: List<Regex> by lazy { listOf(
            // Thai: "บ/ชX1234" "บ/ช X1234" "บช.X7868" "บช. 1234" "บัญชี X1234"
            Regex("""(?:บ/ช|บช\.?|บัญชี)\s*[Xx]?\s*(\d{3,10})""", RegexOption.IGNORE_CASE),
            // English: "a/c *1234" "acct 1234" "account X1234"
            Regex("""(?:a/?c|acct?|account)\s*[*Xx#]?\s*(\d{3,10})""", RegexOption.IGNORE_CASE),
            // Masked: "xxx1234" "XXX-1234" "***1234" "XXXXXX67569"
            Regex("""[Xx*]{2,}[- ]?(\d{3,6})"""),
            // Thai bank format: "123-4-56789-0" → capture last meaningful digits
            Regex("""(\d{3})-\d-\d{5}-\d"""),
            // "ending 1234" / "ลงท้าย 1234"
            Regex("""(?:ending|ลงท้าย|last\s*\d?\s*digit)\s*(\d{3,4})""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // BALANCE PATTERNS - extract remaining balance if present
        // "ใช้ได้15,000.00บ" / "คงเหลือ 15,000.00" / "Balance: 15,000.00"
        // =====================================================================
        private val BALANCE_PATTERNS: List<Regex> by lazy { listOf(
            Regex("""(?:ใช้ได้|คงเหลือ|ยอดคงเหลือ|เหลือ|Bal(?:ance)?|Avail(?:able)?)\s*:?\s*$CUR_PRE$AMT""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // REFERENCE NUMBER PATTERNS
        // =====================================================================
        private val REF_PATTERNS: List<Regex> by lazy { listOf(
            Regex("""(?:Ref|อ้างอิง|ref\.?\s*no\.?|รหัสอ้างอิง|Trans\.?\s*ID|ID)[\s.:]*([A-Za-z0-9]{4,20})""", RegexOption.IGNORE_CASE),
            Regex("""(?:เลขที่|No\.?|หมายเลข)[\s.:]*([A-Za-z0-9]{4,20})""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // SENDER / RECEIVER NAME PATTERNS
        // =====================================================================
        private val NAME_PATTERNS: List<Regex> by lazy { listOf(
            // Thai: "จาก นายสมชาย" / "ส่งจาก UOBT PIB" / "ผู้โอน: นายก"
            Regex("""(?:ส่งจาก|จาก|from|ผู้โอน|ผู้ส่ง)\s*:?\s*(.+?)(?:\s*(?:บ/ช|บช|a/?c|Ref|อ้างอิง|เวลา|เมื่อ|\d{2}[:/]\d{2}|\n|$))""", RegexOption.IGNORE_CASE),
            // Thai: "ไปยัง นายสมชาย" / "ถึง ..." / "ผู้รับ: ..."
            Regex("""(?:ไปยัง|ถึง|to|ผู้รับ|ส่งให้|ให้)\s*:?\s*(.+?)(?:\s*(?:บ/ช|บช|a/?c|Ref|อ้างอิง|เวลา|เมื่อ|\d{2}[:/]\d{2}|\n|$))""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // CHANNEL PATTERNS - where the transaction happened
        // "ผ่านMB" / "via ATM" / "ผ่าน K PLUS"
        // =====================================================================
        private val CHANNEL_PATTERNS: List<Regex> by lazy { listOf(
            Regex("""(?:ผ่าน|via|through|ช่องทาง)\s*(\w[\w\s]{0,20})""", RegexOption.IGNORE_CASE),
        )}

        // =====================================================================
        // CREDIT KEYWORDS for disambiguation when both credit and debit match
        // =====================================================================
        private val CREDIT_KEYWORDS = listOf(
            "รับโอน", "เงินเข้า", "โอนเข้า", "ยอดเงินเข้า", "รับเงิน",
            "เข้าบัญชี", "เข้าบ/ช", "ฝากเงิน", "ฝาก/โอนเงินเข้า",
            "Received", "Transfer In", "Deposit", "CR", "Credit",
            "Incoming", "Money In", "รับ", "PromptPay รับ"
        )

        private val DEBIT_KEYWORDS = listOf(
            "โอนออก", "โอนเงิน", "จ่ายเงิน", "จ่าย", "ชำระเงิน", "ชำระ",
            "ถอนเงิน", "ถอน", "หักเงิน", "หักบ/ช", "หัก", "ตัดเงิน",
            "ถอน/โอน/จ่ายเงิน", "เงินออก", "ซื้อ", "ใช้จ่าย",
            "Withdrawal", "Transfer Out", "Payment", "Paid",
            "DR", "Debit", "Purchase", "Money Out", "Outgoing", "Spend"
        )

        // =====================================================================
        // FINANCIAL CONTENT HEURISTIC KEYWORDS (for SmsInboxScanner)
        // =====================================================================
        val FINANCIAL_KEYWORDS_TH = listOf(
            "เงินเข้า", "เงินออก", "โอนเงิน", "รับโอน", "โอนเข้า", "โอนออก",
            "ชำระเงิน", "จ่ายเงิน", "ยอดเงิน", "คงเหลือ", "บัญชี",
            "ถอนเงิน", "ฝากเงิน", "พร้อมเพย์", "รับเงิน", "หักเงิน",
            "ใช้ได้", "บ/ช", "บช.", "ใช้จ่าย", "หักบัญชี",
            "ยอดใช้จ่าย", "วงเงิน", "ผ่อนชำระ", "ค่าธรรมเนียม"
        )

        val FINANCIAL_KEYWORDS_EN = listOf(
            "Transfer", "Received", "Payment", "Balance", "Account",
            "Deposit", "Withdraw", "Credit", "Debit", "THB",
            "Transaction", "a/c", "Paid", "Purchase", "Incoming",
            "Outgoing", "Spend", "Available", "PromptPay"
        )

        // Cached regex for looksLikeFinancialMessage (ป้องกันสร้างใหม่ทุกครั้ง)
        private val AMOUNT_HEURISTIC_PATTERN: Regex by lazy {
            Regex("""\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?""")
        }
    }

    // === Instance state ===

    data class ParseResult(
        val bank: String,
        val type: TransactionType,
        val amount: String,
        val accountNumber: String,
        val senderOrReceiver: String,
        val referenceNumber: String,
        val balance: String = "",
        val channel: String = ""
    )

    private var customRules: List<SmsSenderRule> = emptyList()

    fun setCustomRules(rules: List<SmsSenderRule>) {
        this.customRules = rules
    }

    // =====================================================================
    // PUBLIC API
    // =====================================================================

    /**
     * Identifies the bank from the SMS sender address.
     * Custom rules are checked first, then hardcoded bank senders.
     * Returns null if not a recognized bank.
     */
    fun identifyBank(senderAddress: String): String? {
        val normalizedSender = senderAddress.trim()

        // Check custom rules first (highest priority)
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
     * Check if an SMS/notification is a bank transaction message.
     * Uses the full pattern library to determine if this is parseable.
     */
    fun isBankTransactionSms(senderAddress: String, message: String): Boolean {
        if (identifyBank(senderAddress) == null) return false
        return tryParseCredit(message) != null || tryParseDebit(message) != null
    }

    /**
     * Parse the SMS/notification and extract transaction details.
     * Returns null if the message is not a bank transaction message.
     * Logs failed parses for debugging.
     */
    fun parse(senderAddress: String, message: String, timestamp: Long): BankTransaction? {
        val bank = identifyBank(senderAddress) ?: return null
        val parseResult = parseMessage(bank, message)

        if (parseResult == null) {
            Log.w(TAG, "PARSE_FAIL bank=$bank sender=$senderAddress msg=${message.take(80)}")
            return null
        }

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

    /**
     * Detect if a message body looks like a financial message (heuristic).
     * Used by SmsInboxScanner for unknown senders.
     */
    fun looksLikeFinancialMessage(body: String): Boolean {
        // เช็ค keyword ก่อน (เร็วกว่า regex) — ถ้าไม่มี keyword ไม่ต้องเช็ค amount
        val hasKeyword = FINANCIAL_KEYWORDS_TH.any { body.contains(it, ignoreCase = true) } ||
                FINANCIAL_KEYWORDS_EN.any { body.contains(it, ignoreCase = true) }
        if (!hasKeyword) return false
        return AMOUNT_HEURISTIC_PATTERN.containsMatchIn(body)
    }

    // =====================================================================
    // INTERNAL PARSING
    // =====================================================================

    private fun parseMessage(bank: String, message: String): ParseResult? {
        val creditAmount = tryParseCredit(message)
        val debitAmount = tryParseDebit(message)

        // Determine transaction type
        val (type, amount) = when {
            creditAmount != null && debitAmount != null -> {
                // Both matched — disambiguate by keyword position
                val creditIdx = findFirstKeywordIndex(message, CREDIT_KEYWORDS)
                val debitIdx = findFirstKeywordIndex(message, DEBIT_KEYWORDS)
                when {
                    creditIdx != -1 && (debitIdx == -1 || creditIdx < debitIdx) ->
                        TransactionType.CREDIT to creditAmount
                    debitIdx != -1 ->
                        TransactionType.DEBIT to debitAmount
                    else ->
                        // Fallback: if we can't decide, use credit (money in is more important to detect)
                        TransactionType.CREDIT to creditAmount
                }
            }
            creditAmount != null -> TransactionType.CREDIT to creditAmount
            debitAmount != null -> TransactionType.DEBIT to debitAmount
            else -> return null
        }

        // Validate amount is reasonable (not 0, not absurdly large)
        val normalizedAmount = normalizeAmount(amount)
        val amountDouble = normalizedAmount.toDoubleOrNull() ?: return null
        if (amountDouble <= 0.0 || amountDouble > 999_999_999.99) {
            Log.w(TAG, "Amount out of range: $normalizedAmount")
            return null
        }

        return ParseResult(
            bank = bank,
            type = type,
            amount = normalizedAmount,
            accountNumber = extractAccountNumber(message),
            senderOrReceiver = extractName(message),
            referenceNumber = extractReference(message),
            balance = extractBalance(message),
            channel = extractChannel(message)
        )
    }

    private fun tryParseCredit(message: String): String? {
        for (pattern in CREDIT_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null && match.groupValues.size > 1) {
                    val amount = match.groupValues[1]
                    if (amount.isNotBlank() && amount.any { it.isDigit() }) {
                        return amount
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Regex error in credit pattern", e)
            }
        }
        return null
    }

    private fun tryParseDebit(message: String): String? {
        for (pattern in DEBIT_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null && match.groupValues.size > 1) {
                    val amount = match.groupValues[1]
                    if (amount.isNotBlank() && amount.any { it.isDigit() }) {
                        return amount
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Regex error in debit pattern", e)
            }
        }
        return null
    }

    private fun extractAccountNumber(message: String): String {
        for (pattern in ACCOUNT_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null) {
                    val digits = match.groupValues
                        .drop(1) // skip group 0
                        .firstOrNull { it.isNotEmpty() && it.all { c -> c.isDigit() } }
                    if (digits != null) return digits
                }
            } catch (_: Exception) { }
        }
        return ""
    }

    private fun extractBalance(message: String): String {
        for (pattern in BALANCE_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null && match.groupValues.size > 1) {
                    return normalizeAmount(match.groupValues[1])
                }
            } catch (_: Exception) { }
        }
        return ""
    }

    private fun extractReference(message: String): String {
        for (pattern in REF_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            } catch (_: Exception) { }
        }
        return ""
    }

    private fun extractName(message: String): String {
        for (pattern in NAME_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    if (name.isNotBlank() && name.length > 1) {
                        return name.take(100)
                    }
                }
            } catch (_: Exception) { }
        }
        return ""
    }

    private fun extractChannel(message: String): String {
        for (pattern in CHANNEL_PATTERNS) {
            try {
                val match = pattern.find(message)
                if (match != null) {
                    return match.groupValues[1].trim().take(30)
                }
            } catch (_: Exception) { }
        }
        return ""
    }

    // =====================================================================
    // UTILITIES
    // =====================================================================

    private fun normalizeAmount(amountStr: String): String {
        val cleaned = amountStr.replace(",", "").trim()
        if (cleaned.isEmpty()) return "0.00"
        return if (cleaned.contains(".")) {
            val parts = cleaned.split(".")
            "${parts[0]}.${parts.getOrElse(1) { "00" }.padEnd(2, '0').take(2)}"
        } else {
            "$cleaned.00"
        }
    }

    private fun findFirstKeywordIndex(message: String, keywords: List<String>): Int {
        val lowerMessage = message.lowercase()
        return keywords
            .map { lowerMessage.indexOf(it.lowercase()) }
            .filter { it >= 0 }
            .minOrNull() ?: -1
    }
}
