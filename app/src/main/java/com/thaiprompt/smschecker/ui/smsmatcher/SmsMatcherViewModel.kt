package com.thaiprompt.smschecker.ui.smsmatcher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.SmsSenderRuleDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.SmsSenderRule
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.domain.scanner.DetectionMethod
import com.thaiprompt.smschecker.domain.scanner.ScannedSms
import com.thaiprompt.smschecker.domain.scanner.SmsInboxScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderMatch(
    val order: OrderApproval,
    val transaction: BankTransaction
)

data class SmsMatcherState(
    val rules: List<SmsSenderRule> = emptyList(),
    val detectedBankSms: List<ScannedSms> = emptyList(),
    val unknownFinancialSms: List<ScannedSms> = emptyList(),
    val orderMatches: List<OrderMatch> = emptyList(),
    val isScanning: Boolean = false,
    val scanCount: Int = 0,
    val showAddDialog: Boolean = false,
    val selectedSender: String = "",
    val selectedSample: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class SmsMatcherViewModel @Inject constructor(
    private val smsSenderRuleDao: SmsSenderRuleDao,
    private val smsInboxScanner: SmsInboxScanner,
    private val orderRepository: OrderRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SmsMatcherVM"
    }

    private val _state = MutableStateFlow(SmsMatcherState())
    val state: StateFlow<SmsMatcherState> = _state.asStateFlow()

    // Prevent concurrent scan calls
    private var scanJob: Job? = null

    init {
        Log.d(TAG, "ViewModel init started")
        try {
            loadRules()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start loadRules", e)
        }
        try {
            scanInbox()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scanInbox", e)
        }
        Log.d(TAG, "ViewModel init completed")
    }

    private fun loadRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsSenderRuleDao.getAllRules().collect { rules ->
                    _state.update { it.copy(rules = rules) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rules", e)
            }
        }
    }

    fun scanInbox() {
        // Cancel any in-progress scan to prevent concurrent scans
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isScanning = true, errorMessage = null) }
            try {
                Log.d(TAG, "Starting inbox scan...")
                val scanned = try {
                    smsInboxScanner.scanInbox()
                } catch (e: SecurityException) {
                    Log.w(TAG, "SMS permission denied", e)
                    _state.update {
                        it.copy(isScanning = false, scanCount = 0, errorMessage = "SMS permission required")
                    }
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "Inbox scan failed", e)
                    _state.update {
                        it.copy(isScanning = false, scanCount = 0, errorMessage = "Scan failed: ${e.message}")
                    }
                    return@launch
                }

                Log.d(TAG, "Scanned ${scanned.size} messages")

                val detected = scanned.filter {
                    it.detectionMethod == DetectionMethod.AUTO_DETECTED ||
                    it.detectionMethod == DetectionMethod.CUSTOM_RULE
                }
                val unknown = scanned.filter {
                    it.detectionMethod == DetectionMethod.UNKNOWN
                }

                // Try to match credit transactions with pending orders
                val matches = mutableListOf<OrderMatch>()
                val creditTransactions = detected.mapNotNull { it.parsedTransaction }
                    .filter { it.type == TransactionType.CREDIT }

                // Get all orders safely
                var orders: List<OrderApproval> = emptyList()
                try {
                    orderRepository.fetchOrders()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch orders from server", e)
                }

                try {
                    orders = orderRepository.getAllOrders().first()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load local orders", e)
                }

                for (tx in creditTransactions) {
                    try {
                        val amountDouble = tx.amount.toDoubleOrNull() ?: continue
                        // Find orders with matching amount (within 0.01 tolerance)
                        val matchingOrder = orders.find { order ->
                            kotlin.math.abs(order.amount - amountDouble) < 0.01
                        }
                        if (matchingOrder != null) {
                            matches.add(OrderMatch(order = matchingOrder, transaction = tx))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error matching transaction", e)
                    }
                }

                _state.update {
                    it.copy(
                        detectedBankSms = detected,
                        unknownFinancialSms = unknown,
                        orderMatches = matches,
                        isScanning = false,
                        scanCount = scanned.size
                    )
                }
                Log.d(TAG, "Scan complete: ${detected.size} detected, ${unknown.size} unknown, ${matches.size} matches")
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning inbox", e)
                _state.update { it.copy(isScanning = false, scanCount = 0, errorMessage = "Error: ${e.message}") }
            }
        }
    }

    fun showAddRuleDialog(sender: String, sampleMessage: String) {
        _state.update {
            it.copy(
                showAddDialog = true,
                selectedSender = sender,
                selectedSample = sampleMessage
            )
        }
    }

    fun hideAddRuleDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun addRule(senderAddress: String, bankCode: String, sampleMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rule = SmsSenderRule(
                    senderAddress = senderAddress,
                    bankCode = bankCode,
                    sampleMessage = sampleMessage
                )
                smsSenderRuleDao.insert(rule)
                _state.update { it.copy(showAddDialog = false) }
                // Re-scan after adding rule
                scanInbox()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding rule", e)
            }
        }
    }

    fun toggleRule(rule: SmsSenderRule) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsSenderRuleDao.update(rule.copy(isActive = !rule.isActive))
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling rule", e)
            }
        }
    }

    fun deleteRule(rule: SmsSenderRule) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                smsSenderRuleDao.delete(rule)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting rule", e)
            }
        }
    }
}
