package com.thaiprompt.smschecker.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.TransactionDao
import com.thaiprompt.smschecker.data.model.BankTransaction
import com.thaiprompt.smschecker.data.model.DashboardStats
import com.thaiprompt.smschecker.data.model.MisclassificationIssueType
import com.thaiprompt.smschecker.data.model.MisclassificationReport
import com.thaiprompt.smschecker.data.model.TransactionType
import com.thaiprompt.smschecker.data.repository.MisclassificationReportRepository
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
    val neverSynced: Boolean = false,
    val isDeviceInactive: Boolean = false
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
    val serverHealthList: List<ServerHealth> = emptyList(),
    val todayTransactionCount: Int = 0,
    val totalTransactionCount: Int = 0,
    val syncedCount: Int = 0,
    val yesterdayCredit: Double = 0.0,
    val yesterdayDebit: Double = 0.0,
    val showReportDialog: Boolean = false,
    val selectedTransaction: BankTransaction? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val orderRepository: OrderRepository,
    private val secureStorage: SecureStorage,
    private val transactionDao: TransactionDao,
    private val reportRepository: MisclassificationReportRepository
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
            } catch (e: Exception) { }
        }

        // Collect today's debit total
        viewModelScope.launch {
            try {
                repository.getTotalDebit(todayStart).collect { total ->
                    _state.update { it.copy(todayDebit = total) }
                }
            } catch (e: Exception) { }
        }

        // Collect unsynced count
        viewModelScope.launch {
            try {
                repository.getUnsyncedCount().collect { count ->
                    _state.update { it.copy(unsyncedCount = count) }
                }
            } catch (e: Exception) { }
        }

        // Collect total transaction count
        viewModelScope.launch {
            try {
                transactionDao.getTotalCount().collect { count ->
                    _state.update { it.copy(totalTransactionCount = count) }
                }
            } catch (e: Exception) { }
        }

        // Collect synced count
        viewModelScope.launch {
            try {
                transactionDao.getSyncedCount().collect { count ->
                    _state.update { it.copy(syncedCount = count) }
                }
            } catch (e: Exception) { }
        }

        // Collect recent transactions + today count
        viewModelScope.launch {
            try {
                repository.getAllTransactions().collect { transactions ->
                    val todayCount = transactions.count { it.timestamp >= todayStart }
                    _state.update {
                        it.copy(
                            recentTransactions = transactions.take(10),
                            todayTransactionCount = todayCount,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }

        // Check monitoring status
        try {
            _state.update { it.copy(isMonitoring = secureStorage.isMonitoringEnabled()) }
        } catch (e: Exception) { }
    }

    fun toggleMonitoring() {
        try {
            val newValue = !_state.value.isMonitoring
            secureStorage.setMonitoringEnabled(newValue)
            _state.update { it.copy(isMonitoring = newValue) }
        } catch (e: Exception) { }
    }

    fun syncAll() {
        viewModelScope.launch {
            try {
                repository.syncAllUnsynced()
            } catch (e: Exception) { }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                try {
                    orderRepository.fetchOrders()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Exception) { Log.w(TAG, "fetchOrders failed", e) }

                try {
                    repository.syncAllUnsynced()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Exception) { Log.w(TAG, "syncAllUnsynced failed", e) }

                try {
                    loadOrderStats()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Exception) { Log.e(TAG, "loadOrderStats failed", e) }

                try {
                    loadServerHealth()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Exception) { Log.e(TAG, "loadServerHealth failed", e) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation from job.cancel(), rethrow to propagate
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "pendingCountJob failed", e)
            }
        }
        offlineQueueJob = viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "offlineQueueJob failed", e)
            }
        }
        dashboardStatsJob = viewModelScope.launch {
            try {
                val stats = orderRepository.fetchDashboardStats()
                _state.update { it.copy(orderStats = stats) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
                                neverSynced = server.lastSyncStatus == null,
                                isDeviceInactive = server.lastSyncStatus == "failed:device_inactive"
                            )
                        }
                        _state.update { it.copy(serverHealthList = healthList) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error mapping server health", e)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
                        null,
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
