package com.thaiprompt.smschecker.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.DashboardStats
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerHealth(
    val serverName: String,
    val isReachable: Boolean,
    val lastSyncAt: Long? = null,
    val neverSynced: Boolean = false
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

    // Track order stats jobs to cancel before re-launching
    private var pendingCountJob: Job? = null
    private var offlineQueueJob: Job? = null
    private var dashboardStatsJob: Job? = null

    init {
        Log.d(TAG, "DashboardViewModel init")
        try { loadDashboardData() } catch (e: Exception) { Log.e(TAG, "loadDashboardData failed", e) }
        try { loadOrderStats() } catch (e: Exception) { Log.e(TAG, "loadOrderStats failed", e) }
        try { loadServerHealth() } catch (e: Exception) { Log.e(TAG, "loadServerHealth failed", e) }
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
                } catch (e: Exception) {
                    Log.w(TAG, "fetchOrders failed", e)
                }
                try {
                    repository.syncAllUnsynced()
                } catch (e: Exception) {
                    Log.w(TAG, "syncAllUnsynced failed", e)
                }
                try {
                    loadOrderStats()
                } catch (e: Exception) {
                    Log.e(TAG, "loadOrderStats failed", e)
                }
                try {
                    loadServerHealth()
                } catch (e: Exception) {
                    Log.e(TAG, "loadServerHealth failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    companion object {
        private const val TAG = "DashboardVM"
    }

    private fun loadOrderStats() {
        // Cancel previous collectors to avoid duplicate coroutines on each refresh
        pendingCountJob?.cancel()
        offlineQueueJob?.cancel()
        dashboardStatsJob?.cancel()

        pendingCountJob = viewModelScope.launch {
            try {
                orderRepository.getPendingReviewCount().collect { count ->
                    _state.update { it.copy(pendingApprovalCount = count) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "pendingCountJob failed", e)
            }
        }
        offlineQueueJob = viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "offlineQueueJob failed", e)
            }
        }
        dashboardStatsJob = viewModelScope.launch {
            try {
                val stats = orderRepository.fetchDashboardStats()
                _state.update { it.copy(orderStats = stats) }
            } catch (e: Exception) {
                Log.w(TAG, "fetchDashboardStats failed", e)
            }
        }
    }

    private var serverHealthJob: Job? = null

    private fun loadServerHealth() {
        serverHealthJob?.cancel()
        serverHealthJob = viewModelScope.launch {
            try {
                repository.getAllServerConfigs().collect { servers ->
                    try {
                        val healthList = servers.map { server ->
                            ServerHealth(
                                serverName = server.name,
                                isReachable = server.lastSyncStatus == "success",
                                lastSyncAt = server.lastSyncAt,
                                neverSynced = server.lastSyncStatus == null
                            )
                        }
                        _state.update { it.copy(serverHealthList = healthList) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error mapping server health", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "serverHealthJob failed", e)
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
