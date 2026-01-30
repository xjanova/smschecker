package com.thaiprompt.smschecker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import com.thaiprompt.smschecker.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerHealth(
    val serverId: Long,
    val serverName: String,
    val isReachable: Boolean,
    val latencyMs: Long? = null,
    val lastChecked: Long = System.currentTimeMillis()
)

data class DashboardState(
    val todayCredit: Double = 0.0,
    val todayDebit: Double = 0.0,
    val unsyncedCount: Int = 0,
    val recentTransactions: List<BankTransaction> = emptyList(),
    val isMonitoring: Boolean = true,
    val isLoading: Boolean = true,
    val serverHealthList: List<ServerHealth> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadDashboardData()
        checkServerHealth()
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

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.syncAllUnsynced()
                checkServerHealth()
                // Small delay so user sees the refresh indicator
                delay(500)
            } finally {
                _isRefreshing.value = false
            }
        }
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

    fun checkServerHealth() {
        viewModelScope.launch {
            try {
                val healthResults = repository.checkAllServersHealth()
                _state.update { it.copy(serverHealthList = healthResults) }
            } catch (_: Exception) {
                // Silently fail - health check is non-critical
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
