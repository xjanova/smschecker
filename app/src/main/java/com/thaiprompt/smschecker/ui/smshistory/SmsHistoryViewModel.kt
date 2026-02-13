package com.thaiprompt.smschecker.ui.smshistory

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.MisclassificationIssueType
import com.thaiprompt.smschecker.data.model.MisclassificationReport
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.MisclassificationReportRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val showReportDialog: Boolean = false,
    val selectedTransaction: BankTransaction? = null
)

/**
 * ViewModel for SMS History screen with improved error handling and permission checks.
 */
@HiltViewModel
class SmsHistoryViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val repository: TransactionRepository,
    private val reportRepository: MisclassificationReportRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SmsHistoryUiState())
    val state: StateFlow<SmsHistoryUiState> = _state.asStateFlow()

    private var transactionsJob: Job? = null

    init {
        Log.d(TAG, "SmsHistoryViewModel initialized - Real-time detection only")
        loadTransactions()
        loadCounts()
    }

    // Removed manual inbox scanning - app now uses real-time SMS detection only
    // via SmsProcessingService which handles:
    // - Automatic detection of new SMS
    // - Bank identification and parsing
    // - Order matching
    // - Database storage
    // - Server synchronization

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

    // ═══════════════════════════════════════
    // MISCLASSIFICATION REPORTING
    // ═══════════════════════════════════════

    fun showReportDialog(transaction: BankTransaction) {
        _state.update {
            it.copy(
                showReportDialog = true,
                selectedTransaction = transaction
            )
        }
    }

    fun hideReportDialog() {
        _state.update {
            it.copy(
                showReportDialog = false,
                selectedTransaction = null
            )
        }
    }

    fun submitReport(issueType: MisclassificationIssueType) {
        viewModelScope.launch {
            try {
                val transaction = _state.value.selectedTransaction ?: return@launch

                val report = MisclassificationReport(
                    transactionId = transaction.id,
                    bank = transaction.bank,
                    rawMessage = transaction.rawMessage,
                    senderAddress = transaction.senderAddress,
                    timestamp = transaction.timestamp,
                    detectedType = transaction.type,
                    detectedAmount = transaction.amount,
                    issueType = issueType,
                    correctType = if (issueType == MisclassificationIssueType.WRONG_TRANSACTION_TYPE) {
                        // สลับประเภท
                        if (transaction.type == TransactionType.CREDIT) TransactionType.DEBIT else TransactionType.CREDIT
                    } else null,
                    deviceId = android.provider.Settings.Secure.getString(
                        appContext.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ),
                    appVersion = "1.0.0" // TODO: Get from BuildConfig
                )

                reportRepository.insertReport(report)
                Log.d(TAG, "Report submitted: $issueType for transaction ${transaction.id}")

                hideReportDialog()

                // TODO: อาจจะส่งไปยัง server สำหรับวิเคราะห์ต่อ
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit report", e)
            }
        }
    }
}
