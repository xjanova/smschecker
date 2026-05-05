package com.thaiprompt.smschecker.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.DailyIncomeExpense
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
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
import kotlin.math.max

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
    val dailyIncomeExpense: List<DailyIncomeExpense> = emptyList(),
    val showReportDialog: Boolean = false,
    val selectedTransaction: BankTransaction? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val orderRepository: OrderRepository,
    private val secureStorage: SecureStorage,
    private val transactionDao: TransactionDao,
    private val orderApprovalDao: OrderApprovalDao,
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

        // รายรับ = บิล approved (ไม่เกี่ยว SMS ธนาคาร)
        // รายจ่าย = net จาก SMS ธนาคารทั้งหมด = max(0, bankDEBIT - bankCREDIT) ไม่ยุ่งกับบิล
        // เช่น โอนเข้าตัวเอง 200 (CREDIT 200) + ถอน 500 (DEBIT 500) → expense = 300
        viewModelScope.launch {
            try {
                kotlinx.coroutines.flow.combine(
                    orderApprovalDao.getApprovedAmountFlow(todayStart),
                    repository.getTotalCredit(todayStart),
                    repository.getTotalDebit(todayStart)
                ) { billIncome, bankCredit, bankDebit ->
                    val netExpense = max(0.0, bankDebit - bankCredit)
                    billIncome to netExpense
                }.collect { (income, expense) ->
                    _state.update { it.copy(todayCredit = income, todayDebit = expense) }
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

        // Collect recent transactions + today count + 7-day income/expense
        viewModelScope.launch {
            try {
                repository.getAllTransactions().collect { transactions ->
                    val todayCount = transactions.count { it.timestamp >= todayStart }
                    val daily = try {
                        loadDailyIncomeExpense(days = 7)
                    } catch (e: Exception) {
                        Log.w(TAG, "loadDailyIncomeExpense failed", e)
                        emptyList()
                    }
                    _state.update {
                        it.copy(
                            recentTransactions = transactions.take(10),
                            todayTransactionCount = todayCount,
                            dailyIncomeExpense = daily,
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

    /**
     * โหลดยอดรายวัน ย้อนหลัง N วัน — เติม 0 สำหรับวันที่ไม่มีรายการ
     * - credit (เส้นเขียว) = ยอดบิลที่อนุมัติแล้ว (ไม่ใช่ CREDIT จากธนาคาร)
     * - debit  (เส้นแดง)  = max(0, bankDEBIT - bankCREDIT) net จาก SMS ธนาคาร ไม่ยุ่งกับบิล
     */
    private suspend fun loadDailyIncomeExpense(days: Int = 7): List<DailyIncomeExpense> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        }
        val since = cal.timeInMillis

        val billByDate = orderApprovalDao.getDailyApprovedAmount(since).associateBy { it.date }
        val bankByDate = transactionDao.getDailyIncomeExpense(since).associateBy { it.date }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val result = mutableListOf<DailyIncomeExpense>()
        repeat(days) {
            val key = sdf.format(cal.time)
            val income = billByDate[key]?.amount ?: 0.0
            val bankCredit = bankByDate[key]?.credit ?: 0.0
            val bankDebit = bankByDate[key]?.debit ?: 0.0
            val expense = max(0.0, bankDebit - bankCredit)
            result.add(DailyIncomeExpense(date = key, credit = income, debit = expense))
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return result
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
                    deviceId = secureStorage.getDeviceId(),
                    appVersion = com.thaiprompt.smschecker.BuildConfig.VERSION_NAME
                )

                reportRepository.insertReport(report)
                Log.d(TAG, "Report submitted: $issueType for transaction ${transaction.id}")

                hideReportDialog()

                // ส่งไป server (xman4289.com) ทันที
                try {
                    val (success, failed) = reportRepository.syncReportsToBackend()
                    Log.d(TAG, "Report sync: success=$success, failed=$failed")
                } catch (e: Exception) {
                    Log.w(TAG, "Report sync failed, will retry in background", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit report", e)
            }
        }
    }
}
