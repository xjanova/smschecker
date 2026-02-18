package com.thaiprompt.smschecker.ui.orders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersState(
    val orders: List<OrderApproval> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val statusFilter: ApprovalStatus? = null,
    val serverFilter: Long? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val pendingCount: Int = 0,
    val offlineQueueCount: Int = 0,
    val servers: List<ServerConfig> = emptyList(),
    val error: String? = null,
    val actionResult: ActionResult? = null,
    val searchQuery: String = "",
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val totalCount: Int = 0
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val orderNumber: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
    }

    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Track jobs to cancel before re-launching (prevents duplicate collectors)
    private var ordersJob: Job? = null
    private var pendingCountJob: Job? = null
    private var offlineQueueJob: Job? = null
    private var serversJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var searchJob: Job? = null

    private val _searchQuery = MutableStateFlow("")

    init {
        try {
            loadOrders()
            loadCounts()
            loadServers()
            // Fetch orders from server on init
            refresh()
            // Start auto-refresh every 30 seconds
            startAutoRefresh()
        } catch (e: Exception) {
            Log.e("OrdersViewModel", "Error initializing OrdersViewModel", e)
            _state.update { it.copy(isLoading = false, error = "Failed to initialize: ${e.message}") }
        }
    }

    /**
     * Auto-refresh orders every 30 seconds to ensure orders are always up-to-date
     * even if FCM push notification doesn't arrive
     */
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                try {
                    delay(30_000L) // 30 seconds
                    Log.d("OrdersViewModel", "Auto-refreshing orders...")
                    orderRepository.fetchOrders()
                    loadOrders()
                } catch (e: Exception) {
                    Log.w("OrdersViewModel", "Auto-refresh failed: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        ordersJob?.cancel()
        pendingCountJob?.cancel()
        offlineQueueJob?.cancel()
        serversJob?.cancel()
        searchJob?.cancel()
    }

    private fun loadOrders() {
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, hasMorePages = true) }
            try {
                val search = _searchQuery.value.trim()
                val s = _state.value
                val orders = orderRepository.getFilteredOrdersPaged(
                    status = s.statusFilter,
                    serverId = s.serverFilter,
                    startTime = s.dateFrom,
                    endTime = s.dateTo,
                    search = search.takeIf { it.isNotEmpty() },
                    limit = PAGE_SIZE,
                    offset = 0
                )
                val totalCount = orderRepository.getFilteredOrdersCount(
                    status = s.statusFilter,
                    serverId = s.serverFilter,
                    startTime = s.dateFrom,
                    endTime = s.dateTo,
                    search = search.takeIf { it.isNotEmpty() }
                )
                _state.update {
                    it.copy(
                        orders = orders,
                        isLoading = false,
                        error = null,
                        hasMorePages = orders.size >= PAGE_SIZE && orders.size < totalCount,
                        totalCount = totalCount,
                        searchQuery = search
                    )
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error loading orders", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMoreOrders() {
        val currentState = _state.value
        if (currentState.isLoadingMore || !currentState.hasMorePages || currentState.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val search = _searchQuery.value.trim()
                val currentOffset = currentState.orders.size
                val moreOrders = orderRepository.getFilteredOrdersPaged(
                    status = currentState.statusFilter,
                    serverId = currentState.serverFilter,
                    startTime = currentState.dateFrom,
                    endTime = currentState.dateTo,
                    search = search.takeIf { it.isNotEmpty() },
                    limit = PAGE_SIZE,
                    offset = currentOffset
                )
                _state.update {
                    val combined = it.orders + moreOrders
                    it.copy(
                        orders = combined,
                        isLoadingMore = false,
                        hasMorePages = moreOrders.size >= PAGE_SIZE && combined.size < it.totalCount
                    )
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error loading more orders", e)
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400L)
            loadOrders()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _state.update { it.copy(searchQuery = "") }
        searchJob?.cancel()
        loadOrders()
    }

    private fun loadCounts() {
        pendingCountJob?.cancel()
        offlineQueueJob?.cancel()
        pendingCountJob = viewModelScope.launch {
            try {
                orderRepository.getPendingReviewCount().collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error loading pending count", e)
            }
        }
        offlineQueueJob = viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error loading offline queue count", e)
            }
        }
    }

    private fun loadServers() {
        serversJob?.cancel()
        serversJob = viewModelScope.launch {
            try {
                transactionRepository.getAllServerConfigs().collect { servers ->
                    _state.update { it.copy(servers = servers) }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error loading servers", e)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // ดึงข้อมูลล่าสุดจากเซิร์ฟเวอร์
                try {
                    orderRepository.fetchOrders()
                } catch (e: Exception) {
                    Log.w("OrdersViewModel", "Server fetch failed", e)
                }
                // ทำความสะอาดบิลหมดอายุ (ย้ายไปถังขยะ)
                try {
                    orderRepository.cleanupExpiredOrders()
                } catch (e: Exception) {
                    Log.w("OrdersViewModel", "Cleanup failed", e)
                }
                loadOrders()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setStatusFilter(status: ApprovalStatus?) {
        _state.update { it.copy(statusFilter = status, isLoading = true) }
        loadOrders()
    }

    fun setServerFilter(serverId: Long?) {
        _state.update { it.copy(serverFilter = serverId, isLoading = true) }
        loadOrders()
    }

    fun setDateRange(from: Long, to: Long) {
        _state.update { it.copy(dateFrom = from, dateTo = to, isLoading = true) }
        loadOrders()
    }

    fun clearDateRange() {
        _state.update { it.copy(dateFrom = null, dateTo = null, isLoading = true) }
        loadOrders()
    }

    fun approveOrder(order: OrderApproval) {
        viewModelScope.launch {
            try {
                orderRepository.approveOrder(order)
                _state.update { it.copy(
                    actionResult = ActionResult(
                        success = true,
                        message = "Approved",
                        orderNumber = order.orderNumber
                    )
                ) }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error approving order ${order.id}", e)
                _state.update { it.copy(
                    actionResult = ActionResult(
                        success = false,
                        message = e.message ?: "Approve failed",
                        orderNumber = order.orderNumber
                    )
                ) }
            }
        }
    }

    fun rejectOrder(order: OrderApproval) {
        viewModelScope.launch {
            try {
                orderRepository.rejectOrder(order)
                _state.update { it.copy(
                    actionResult = ActionResult(
                        success = true,
                        message = "Rejected",
                        orderNumber = order.orderNumber
                    )
                ) }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error rejecting order ${order.id}", e)
                _state.update { it.copy(
                    actionResult = ActionResult(
                        success = false,
                        message = e.message ?: "Reject failed",
                        orderNumber = order.orderNumber
                    )
                ) }
            }
        }
    }

    fun clearActionResult() {
        _state.update { it.copy(actionResult = null) }
    }

    fun bulkApproveAll() {
        viewModelScope.launch {
            try {
                val pending = _state.value.orders.filter { it.approvalStatus == ApprovalStatus.PENDING_REVIEW }
                for (order in pending) {
                    orderRepository.approveOrder(order)
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "Error bulk approving orders", e)
            }
        }
    }
}
