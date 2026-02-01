package com.thaiprompt.smschecker.ui.smshistory

import android.util.Log
import androidx.lifecycle.ViewModel
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    val isLoading: Boolean = false,
    val editingTransaction: BankTransaction? = null,
    val isSaving: Boolean = false,
    // Scanning state — disabled for now (debugging crash)
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val scanFoundCount: Int = 0,
    val scanComplete: Boolean = false,
    val scanError: String? = null
)

/**
 * Minimal ViewModel — NO dependencies except Hilt injection.
 * This is to isolate the crash: if this still crashes, the problem
 * is NOT in ViewModel dependencies but somewhere else (navigation, Screen, etc.)
 */
@HiltViewModel
class SmsHistoryViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SmsHistoryUiState())
    val state: StateFlow<SmsHistoryUiState> = _state.asStateFlow()

    init {
        Log.d(TAG, "SmsHistoryViewModel init — MINIMAL VERSION (no deps)")
    }

    fun startInboxScan() {
        Log.d(TAG, "startInboxScan called — skipped (minimal version)")
    }

    fun setFilter(filter: HistoryFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun startEditing(transaction: BankTransaction) {
        _state.update { it.copy(editingTransaction = transaction) }
    }

    fun cancelEditing() {
        _state.update { it.copy(editingTransaction = null) }
    }

    fun saveEdit(bank: String, type: TransactionType, amount: String) {
        Log.d(TAG, "saveEdit called — skipped (minimal version)")
    }
}
