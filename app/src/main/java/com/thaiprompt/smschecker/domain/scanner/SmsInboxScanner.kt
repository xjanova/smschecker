package com.thaiprompt.smschecker.domain.scanner

import android.app.Application
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.thaiprompt.smschecker.data.db.SmsSenderRuleDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.domain.parser.BankSmsParser
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val detectedBank: String?,       // null if unknown
    val detectionMethod: DetectionMethod,
    val parsedTransaction: BankTransaction? // null if not parseable as transaction
)

enum class DetectionMethod {
    AUTO_DETECTED,   // Matched via BankSmsParser patterns
    CUSTOM_RULE,     // Matched via SmsSenderRule
    UNKNOWN          // Not recognized
}

@Singleton
class SmsInboxScanner @Inject constructor(
    private val application: Application,
    private val parser: BankSmsParser,
    private val smsSenderRuleDao: SmsSenderRuleDao
) {

    companion object {
        private const val TAG = "SmsInboxScanner"
        private const val MAX_SCAN_COUNT = 500
    }

    /**
     * Scans the SMS inbox and returns all messages with bank detection info.
     * Messages that are detected as bank transactions come first.
     */
    suspend fun scanInbox(maxMessages: Int = MAX_SCAN_COUNT): List<ScannedSms> {
        // Load custom rules into parser
        val customRules = smsSenderRuleDao.getActiveRules()
        parser.setCustomRules(customRules)

        val allMessages = readInboxMessages(maxMessages)
        val results = mutableListOf<ScannedSms>()

        for (msg in allMessages) {
            val bank = parser.identifyBank(msg.sender)

            if (bank != null) {
                // Detected as bank SMS
                val isCustomRule = customRules.any {
                    it.isActive && msg.sender.contains(it.senderAddress, ignoreCase = true)
                }

                val parsed = parser.parse(msg.sender, msg.body, msg.timestamp)

                results.add(
                    ScannedSms(
                        sender = msg.sender,
                        body = msg.body,
                        timestamp = msg.timestamp,
                        detectedBank = bank,
                        detectionMethod = if (isCustomRule) DetectionMethod.CUSTOM_RULE else DetectionMethod.AUTO_DETECTED,
                        parsedTransaction = parsed
                    )
                )
            } else {
                // Try to detect if it looks like a bank/financial message even without sender match
                val looksLikeBankSms = detectBankLikeContent(msg.body)
                if (looksLikeBankSms) {
                    results.add(
                        ScannedSms(
                            sender = msg.sender,
                            body = msg.body,
                            timestamp = msg.timestamp,
                            detectedBank = null,
                            detectionMethod = DetectionMethod.UNKNOWN,
                            parsedTransaction = null
                        )
                    )
                }
            }
        }

        // Sort: detected bank SMS first (by timestamp desc), then unknown
        return results.sortedWith(compareBy<ScannedSms> {
            when (it.detectionMethod) {
                DetectionMethod.AUTO_DETECTED -> 0
                DetectionMethod.CUSTOM_RULE -> 1
                DetectionMethod.UNKNOWN -> 2
            }
        }.thenByDescending { it.timestamp })
    }

    /**
     * Scans inbox and returns only parseable bank transactions.
     * These can be directly compared with orders.
     */
    suspend fun scanForTransactions(): List<BankTransaction> {
        val scanned = scanInbox()
        return scanned.mapNotNull { it.parsedTransaction }
    }

    /**
     * Heuristic detection: does the message body look like a bank/financial notification?
     * This catches senders we don't know about yet.
     */
    private fun detectBankLikeContent(body: String): Boolean {
        val financialKeywords = listOf(
            // Thai keywords
            "เงินเข้า", "เงินออก", "โอนเงิน", "รับโอน", "ชำระเงิน",
            "ยอดเงิน", "คงเหลือ", "บัญชี", "ถอนเงิน", "ฝากเงิน",
            "พร้อมเพย์", "PromptPay", "บาท",
            // English keywords
            "Transfer", "Received", "Payment", "Balance", "Account",
            "Deposit", "Withdraw", "Credit", "Debit", "THB"
        )

        val amountPattern = Regex("""\d{1,3}(,\d{3})*(\.\d{1,2})?""")
        val hasAmount = amountPattern.containsMatchIn(body)
        val hasKeyword = financialKeywords.any { body.contains(it, ignoreCase = true) }

        return hasAmount && hasKeyword
    }

    private data class RawSms(val sender: String, val body: String, val timestamp: Long)

    private fun readInboxMessages(maxMessages: Int): List<RawSms> {
        val messages = mutableListOf<RawSms>()
        try {
            val uri = Uri.parse("content://sms/inbox")
            val cursor = application.contentResolver.query(
                uri,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                var count = 0
                while (it.moveToNext() && count < maxMessages) {
                    val address = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: continue
                    val date = it.getLong(dateIdx)
                    messages.add(RawSms(address, body, date))
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox", e)
        }
        return messages
    }
}
