package com.thaiprompt.smschecker.ui.smsmatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TransactionFilter {
    ALL, CREDIT, DEBIT
}

data class SmsHistoryState(
    val transactions: List<BankTransaction> = emptyList(),
    val filter: TransactionFilter = TransactionFilter.ALL,
    val totalDetected: Int = 0,
    val totalSynced: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class SmsMatcherViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _state = MutableStateFlow(SmsHistoryState())
    val state: StateFlow<SmsHistoryState> = _state.asStateFlow()

    init {
        loadTransactions()
        loadCounts()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            transactionDao.getRecentTransactions()
                .catch { /* ignore errors */ }
                .collect { transactions ->
                    val filtered = applyFilter(transactions, _state.value.filter)
                    _state.update {
                        it.copy(
                            transactions = filtered,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun loadCounts() {
        viewModelScope.launch {
            transactionDao.getTotalCount()
                .catch { emit(0) }
                .collect { count ->
                    _state.update { it.copy(totalDetected = count) }
                }
        }
        viewModelScope.launch {
            transactionDao.getSyncedCount()
                .catch { emit(0) }
                .collect { count ->
                    _state.update { it.copy(totalSynced = count) }
                }
        }
    }

    fun setFilter(filter: TransactionFilter) {
        _state.update { it.copy(filter = filter) }
        // Re-collect with new filter
        viewModelScope.launch {
            transactionDao.getRecentTransactions()
                .catch { /* ignore */ }
                .first()
                .let { transactions ->
                    _state.update {
                        it.copy(transactions = applyFilter(transactions, filter))
                    }
                }
        }
    }

    private fun applyFilter(
        transactions: List<BankTransaction>,
        filter: TransactionFilter
    ): List<BankTransaction> {
        return when (filter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.CREDIT -> transactions.filter { it.type == TransactionType.CREDIT }
            TransactionFilter.DEBIT -> transactions.filter { it.type == TransactionType.DEBIT }
        }
    }
}
