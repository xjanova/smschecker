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
 * Step 4c: Direct SMS read test — bypass SmsInboxScanner entirely.
 * Read SMS inbox directly via ContentResolver to find exact crash point.
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
        Log.d(TAG, "SmsHistoryViewModel init — Step 4c (direct SMS read test)")
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
                Log.d(TAG, "=== DIRECT SMS READ TEST ===")

                // Step A: Just read SMS inbox via ContentResolver (no parsing)
                val messages = mutableListOf<Triple<String, String, Long>>() // sender, body, timestamp
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
                        while (it.moveToNext() && count < 10) { // only 10 messages!
                            val address = it.getString(addressIdx) ?: "null"
                            val body = it.getString(bodyIdx) ?: "null"
                            val date = it.getLong(dateIdx)
                            messages.add(Triple(address, body, date))
                            count++
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Step A FAILED: ${t.javaClass.name}: ${t.message}", t)
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                scanError = "อ่าน SMS ไม่ได้: ${t.javaClass.simpleName}: ${t.message}"
                            )
                        }
                    }
                    return@launch
                }

                Log.d(TAG, "Step A OK: read ${messages.size} messages")
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(scanProgress = messages.size, scanTotal = messages.size) }
                }

                // Step B: Count how many are from banks (just identifyBank, no parse)
                var bankCount = 0
                try {
                    val parser = smsInboxScanner // just to test that it's accessible
                    Log.d(TAG, "Step B: smsInboxScanner accessible OK")
                    // We won't call scanner here — just count messages
                    bankCount = messages.size
                } catch (t: Throwable) {
                    Log.e(TAG, "Step B FAILED: ${t.javaClass.name}: ${t.message}", t)
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                scanError = "Step B: ${t.javaClass.simpleName}: ${t.message}"
                            )
                        }
                    }
                    return@launch
                }

                Log.d(TAG, "Step B OK")

                // Step C: Done — report success with count
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
                Log.d(TAG, "=== DIRECT READ TEST COMPLETE: ${messages.size} messages read ===")

            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "OUTER CATCH: ${t.javaClass.name}: ${t.message}", t)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            scanError = "OUTER: ${t.javaClass.simpleName}: ${t.message}"
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
