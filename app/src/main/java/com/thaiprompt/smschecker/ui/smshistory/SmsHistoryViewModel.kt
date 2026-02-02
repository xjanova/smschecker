package com.thaiprompt.smschecker.ui.smshistory

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.domain.scanner.SmsInboxScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SmsHistoryVM"

enum class HistoryFilter {
    ALL, CREDIT, DEBIT
}

data class SmsHistoryUiState(
    val transactions: List<BankTransaction> = emptyList(),
    val allTransactions: List<BankTransaction> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val totalDetected: Int = 0,
    val totalSynced: Int = 0,
    val isLoading: Boolean = true,
    val editingTransaction: BankTransaction? = null,
    val isSaving: Boolean = false,
    // Scanning state
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val scanFoundCount: Int = 0,
    val scanComplete: Boolean = false,
    val scanError: String? = null
)

/**
 * ViewModel for SMS History screen with improved error handling and permission checks.
 */
@HiltViewModel
class SmsHistoryViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val repository: TransactionRepository,
    private val smsInboxScanner: SmsInboxScanner,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SmsHistoryUiState())
    val state: StateFlow<SmsHistoryUiState> = _state.asStateFlow()

    private var transactionsJob: Job? = null
    private var scanJob: Job? = null

    init {
        Log.d(TAG, "SmsHistoryViewModel initialized")
        loadTransactions()
        loadCounts()
    }

    fun startInboxScan() {
        if (_state.value.isScanning) return
        scanJob?.cancel()

        _state.update {
            it.copy(
                isScanning = true,
                scanProgress = 0,
                scanTotal = 0,
                scanFoundCount = 0,
                scanComplete = false,
                scanError = null
            )
        }

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== START INBOX SCAN ===")

                // Check SMS permission first
                if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                    appContext.checkSelfPermission(android.Manifest.permission.READ_SMS)) {
                    Log.e(TAG, "READ_SMS permission not granted")
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                scanError = "ไม่มีสิทธิ์อ่าน SMS กรุณาให้สิทธิ์ในการตั้งค่า"
                            )
                        }
                    }
                    return@launch
                }

                // Read SMS inbox
                val messages = mutableListOf<Triple<String, String, Long>>()
                try {
                    val uri = Uri.parse("content://sms/inbox")
                    val cursor = appContext.contentResolver.query(
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
                        while (it.moveToNext() && count < 200) {
                            val address = it.getString(addressIdx) ?: continue
                            val body = it.getString(bodyIdx) ?: continue
                            val date = it.getLong(dateIdx)
                            messages.add(Triple(address, body, date))
                            count++

                            // Report progress every 20 messages
                            if (count % 20 == 0) {
                                withContext(Dispatchers.Main) {
                                    _state.update { it.copy(scanProgress = count) }
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Successfully read ${messages.size} SMS messages")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException reading SMS", e)
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                scanError = "ไม่สามารถอ่าน SMS ได้: ไม่มีสิทธิ์"
                            )
                        }
                    }
                    return@launch
                } catch (t: Throwable) {
                    Log.e(TAG, "Error reading SMS inbox", t)
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                scanError = "เกิดข้อผิดพลาด: ${t.message}"
                            )
                        }
                    }
                    return@launch
                }

                // Scan complete
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            scanComplete = true,
                            scanFoundCount = messages.size,
                            scanProgress = messages.size,
                            scanTotal = messages.size
                        )
                    }
                }
                Log.d(TAG, "=== SCAN COMPLETE: ${messages.size} messages ===")

            } catch (e: CancellationException) {
                Log.d(TAG, "Scan cancelled")
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error during scan", t)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            scanError = "เกิดข้อผิดพลาดที่ไม่คาดคิด: ${t.message}"
                        )
                    }
                }
            }
        }
    }

    private fun loadTransactions() {
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            try {
                transactionDao.getRecentTransactions()
                    .catch { e ->
                        Log.e(TAG, "getRecentTransactions flow error", e)
                        emit(emptyList())
                    }
                    .collect { transactions ->
                        val filtered = applyFilter(transactions, _state.value.filter)
                        _state.update {
                            it.copy(
                                allTransactions = transactions,
                                transactions = filtered,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadTransactions failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadCounts() {
        viewModelScope.launch {
            try {
                transactionDao.getTotalCount()
                    .catch { e ->
                        Log.e(TAG, "getTotalCount flow error", e)
                        emit(0)
                    }
                    .collect { count ->
                        _state.update { it.copy(totalDetected = count) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadCounts totalCount failed", e)
            }
        }
        viewModelScope.launch {
            try {
                transactionDao.getSyncedCount()
                    .catch { e ->
                        Log.e(TAG, "getSyncedCount flow error", e)
                        emit(0)
                    }
                    .collect { count ->
                        _state.update { it.copy(totalSynced = count) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadCounts syncedCount failed", e)
            }
        }
    }

    fun setFilter(filter: HistoryFilter) {
        val filtered = applyFilter(_state.value.allTransactions, filter)
        _state.update { it.copy(filter = filter, transactions = filtered) }
    }

    fun startEditing(transaction: BankTransaction) {
        _state.update { it.copy(editingTransaction = transaction) }
    }

    fun cancelEditing() {
        _state.update { it.copy(editingTransaction = null) }
    }

    fun saveEdit(bank: String, type: TransactionType, amount: String) {
        val tx = _state.value.editingTransaction ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                transactionDao.updateTransaction(
                    id = tx.id,
                    bank = bank,
                    type = type,
                    amount = amount
                )
                _state.update { it.copy(editingTransaction = null, isSaving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "saveEdit failed", e)
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun applyFilter(
        transactions: List<BankTransaction>,
        filter: HistoryFilter
    ): List<BankTransaction> {
        return when (filter) {
            HistoryFilter.ALL -> transactions
            HistoryFilter.CREDIT -> transactions.filter { it.type == TransactionType.CREDIT }
            HistoryFilter.DEBIT -> transactions.filter { it.type == TransactionType.DEBIT }
        }
    }
}
