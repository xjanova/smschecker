package com.thaiprompt.smschecker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.DashboardStats
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerHealth(
    val serverName: String,
    val isReachable: Boolean,
    val latencyMs: Long = 0
)

data class DashboardState(
    val todayCredit: Double = 0.0,
    val todayDebit: Double = 0.0,
    val unsyncedCount: Int = 0,
    val recentTransactions: List<BankTransaction> = emptyList(),
    val isMonitoring: Boolean = true,
    val isLoading: Boolean = true,
    val pendingApprovalCount: Int = 0,
    val orderStats: DashboardStats = DashboardStats(),
    val offlineQueueCount: Int = 0,
    val serverHealthList: List<ServerHealth> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val orderRepository: OrderRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadDashboardData()
        loadOrderStats()
    }

    private fun loadDashboardData() {
        val todayStart = getTodayStart()

        // Collect today's credit total
        viewModelScope.launch {
            try {
                repository.getTotalCredit(todayStart).collect { total ->
                    _state.update { it.copy(todayCredit = total) }
                }
            } catch (_: Exception) { }
        }

        // Collect today's debit total
        viewModelScope.launch {
            try {
                repository.getTotalDebit(todayStart).collect { total ->
                    _state.update { it.copy(todayDebit = total) }
                }
            } catch (_: Exception) { }
        }

        // Collect unsynced count
        viewModelScope.launch {
            try {
                repository.getUnsyncedCount().collect { count ->
                    _state.update { it.copy(unsyncedCount = count) }
                }
            } catch (_: Exception) { }
        }

        // Collect recent transactions
        viewModelScope.launch {
            try {
                repository.getAllTransactions().collect { transactions ->
                    _state.update {
                        it.copy(
                            recentTransactions = transactions.take(10),
                            isLoading = false
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }

        // Check monitoring status
        try {
            _state.update { it.copy(isMonitoring = secureStorage.isMonitoringEnabled()) }
        } catch (_: Exception) { }
    }

    fun toggleMonitoring() {
        try {
            val newValue = !_state.value.isMonitoring
            secureStorage.setMonitoringEnabled(newValue)
            _state.update { it.copy(isMonitoring = newValue) }
        } catch (_: Exception) { }
    }

    fun syncAll() {
        viewModelScope.launch {
            try {
                repository.syncAllUnsynced()
            } catch (_: Exception) { }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                try {
                    orderRepository.fetchOrders()
                } catch (_: Exception) {
                    // Server unreachable or not configured
                }
                loadOrderStats()
            } catch (_: Exception) {
                // Prevent crash on refresh
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadOrderStats() {
        viewModelScope.launch {
            try {
                orderRepository.getPendingReviewCount().collect { count ->
                    _state.update { it.copy(pendingApprovalCount = count) }
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                val stats = orderRepository.fetchDashboardStats()
                _state.update { it.copy(orderStats = stats) }
            } catch (_: Exception) {
                // Stats fetch failed, keep defaults
            }
        }
    }

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
