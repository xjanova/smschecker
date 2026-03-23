package com.thaiprompt.smschecker.ui.transactions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionListState(
    val transactions: List<BankTransaction> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val totalCount: Int = 0
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
        private const val TAG = "TransactionListVM"
    }

    private val _state = MutableStateFlow(TransactionListState())
    val state: StateFlow<TransactionListState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadInitialPage()
    }

    private fun loadInitialPage() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val transactions = transactionDao.getTransactionsPaged(PAGE_SIZE, 0)
                val totalCount = transactionDao.getTotalCountOnce()
                _state.update {
                    it.copy(
                        transactions = transactions,
                        isLoading = false,
                        hasMorePages = transactions.size >= PAGE_SIZE && transactions.size < totalCount,
                        totalCount = totalCount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialPage failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMorePages || s.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val offset = s.transactions.size
                val more = transactionDao.getTransactionsPaged(PAGE_SIZE, offset)
                _state.update {
                    val combined = it.transactions + more
                    it.copy(
                        transactions = combined,
                        isLoadingMore = false,
                        hasMorePages = more.size >= PAGE_SIZE && combined.size < it.totalCount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore failed", e)
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        loadInitialPage()
    }
}
