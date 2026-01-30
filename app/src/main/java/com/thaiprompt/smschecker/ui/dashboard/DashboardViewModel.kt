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
            repository.getTotalCredit(todayStart).collect { total ->
                _state.update { it.copy(todayCredit = total) }
            }
        }

        // Collect today's debit total
        viewModelScope.launch {
            repository.getTotalDebit(todayStart).collect { total ->
                _state.update { it.copy(todayDebit = total) }
            }
        }

        // Collect unsynced count
        viewModelScope.launch {
            repository.getUnsyncedCount().collect { count ->
                _state.update { it.copy(unsyncedCount = count) }
            }
        }

        // Collect recent transactions
        viewModelScope.launch {
            repository.getAllTransactions().collect { transactions ->
                _state.update {
                    it.copy(
                        recentTransactions = transactions.take(10),
                        isLoading = false
                    )
                }
            }
        }

        // Check monitoring status
        _state.update { it.copy(isMonitoring = secureStorage.isMonitoringEnabled()) }
    }

    fun toggleMonitoring() {
        val newValue = !_state.value.isMonitoring
        secureStorage.setMonitoringEnabled(newValue)
        _state.update { it.copy(isMonitoring = newValue) }
    }

    fun syncAll() {
        viewModelScope.launch {
            repository.syncAllUnsynced()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                orderRepository.fetchOrders()
                loadOrderStats()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadOrderStats() {
        viewModelScope.launch {
            orderRepository.getPendingReviewCount().collect { count ->
                _state.update { it.copy(pendingApprovalCount = count) }
            }
        }
        viewModelScope.launch {
            orderRepository.getOfflineQueueCount().collect { count ->
                _state.update { it.copy(offlineQueueCount = count) }
            }
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
