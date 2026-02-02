package com.thaiprompt.smschecker.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.model.ApprovalStatus
import com.thaiprompt.smschecker.data.model.OrderApproval
import com.thaiprompt.smschecker.data.model.ServerConfig
import com.thaiprompt.smschecker.data.repository.OrderRepository
import com.thaiprompt.smschecker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val error: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Track jobs to cancel before re-launching (prevents duplicate collectors)
    private var ordersJob: Job? = null
    private var pendingCountJob: Job? = null
    private var offlineQueueJob: Job? = null
    private var serversJob: Job? = null

    init {
        loadOrders()
        loadCounts()
        loadServers()
        // Fetch orders from server on init
        refresh()
    }

    private fun loadOrders() {
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            try {
                orderRepository.getFilteredOrders(
                    status = _state.value.statusFilter,
                    serverId = _state.value.serverFilter,
                    startTime = _state.value.dateFrom,
                    endTime = _state.value.dateTo
                ).collect { orders ->
                    _state.update { it.copy(orders = orders, isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadCounts() {
        pendingCountJob?.cancel()
        offlineQueueJob?.cancel()
        pendingCountJob = viewModelScope.launch {
            try {
                orderRepository.getPendingReviewCount().collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            } catch (e: Exception) { }
        }
        offlineQueueJob = viewModelScope.launch {
            try {
                orderRepository.getOfflineQueueCount().collect { count ->
                    _state.update { it.copy(offlineQueueCount = count) }
                }
            } catch (e: Exception) { }
        }
    }

    private fun loadServers() {
        serversJob?.cancel()
        serversJob = viewModelScope.launch {
            try {
                transactionRepository.getAllServerConfigs().collect { servers ->
                    _state.update { it.copy(servers = servers) }
                }
            } catch (e: Exception) { }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                try {
                    orderRepository.fetchOrders()
                } catch (e: Exception) {
                    // Server unreachable or not configured
                }
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
            } catch (e: Exception) { }
        }
    }

    fun rejectOrder(order: OrderApproval) {
        viewModelScope.launch {
            try {
                orderRepository.rejectOrder(order)
            } catch (e: Exception) { }
        }
    }

    fun bulkApproveAll() {
        viewModelScope.launch {
            try {
                val pending = _state.value.orders.filter { it.approvalStatus == ApprovalStatus.PENDING_REVIEW }
                for (order in pending) {
                    orderRepository.approveOrder(order)
                }
            } catch (e: Exception) { }
        }
    }
}
