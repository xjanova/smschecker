package com.thaiprompt.smschecker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class TransactionListState(
    val transactions: List<BankTransaction> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionListState())
    val state: StateFlow<TransactionListState> = _state.asStateFlow()

    init {
        repository.getAllTransactions()
            .onEach { transactions ->
                _state.update {
                    it.copy(
                        transactions = transactions,
                        isLoading = false
                    )
                }
            }
            .catch { _ ->
                _state.update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }
}
