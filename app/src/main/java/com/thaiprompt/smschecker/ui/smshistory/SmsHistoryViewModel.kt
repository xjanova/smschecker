package com.thaiprompt.smschecker.ui.smshistory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
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
    // Scanning state
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val scanFoundCount: Int = 0,
    val scanComplete: Boolean = false,
    val scanError: String? = null
)

/**
 * Step 2: Add TransactionDao back — load data from Room DB.
 * NO scanner yet. If this crashes → Room/Dao is the problem.
 */
@HiltViewModel
class SmsHistoryViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _state = MutableStateFlow(SmsHistoryUiState())
    val state: StateFlow<SmsHistoryUiState> = _state.asStateFlow()

    private var transactionsJob: Job? = null

    init {
        Log.d(TAG, "SmsHistoryViewModel init — Step 2 (TransactionDao only)")
        loadTransactions()
        loadCounts()
    }

    fun startInboxScan() {
        Log.d(TAG, "startInboxScan called — skipped (scanner not added yet)")
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
