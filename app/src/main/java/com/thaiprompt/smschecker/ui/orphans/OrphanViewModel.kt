package com.thaiprompt.smschecker.ui.orphans

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.OrphanTransactionDao
import com.thaiprompt.smschecker.data.model.OrphanStatus
import com.thaiprompt.smschecker.data.model.OrphanTransaction
import com.thaiprompt.smschecker.data.repository.OrphanTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrphanState(
    val orphans: List<OrphanTransaction> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val showPendingOnly: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class OrphanViewModel @Inject constructor(
    private val orphanRepository: OrphanTransactionRepository,
    private val orphanDao: OrphanTransactionDao
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
        private const val TAG = "OrphanViewModel"
    }

    private val _state = MutableStateFlow(OrphanState())
    val state: StateFlow<OrphanState> = _state.asStateFlow()

    private var countJob: Job? = null

    init {
        loadInitialPage()
        loadCount()
    }

    private fun loadInitialPage() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val s = _state.value
                val (orphans, total) = if (s.showPendingOnly) {
                    val items = orphanDao.getOrphansByStatusPaged(OrphanStatus.PENDING, PAGE_SIZE, 0)
                    val count = orphanDao.getCountByStatus(OrphanStatus.PENDING)
                    items to count
                } else {
                    val items = orphanDao.getOrphansPaged(PAGE_SIZE, 0)
                    val count = orphanDao.getTotalCountOnce()
                    items to count
                }
                _state.update {
                    it.copy(
                        orphans = orphans,
                        isLoading = false,
                        hasMorePages = orphans.size >= PAGE_SIZE && orphans.size < total,
                        totalCount = total,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialPage failed", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMorePages || s.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val offset = s.orphans.size
                val more = if (s.showPendingOnly) {
                    orphanDao.getOrphansByStatusPaged(OrphanStatus.PENDING, PAGE_SIZE, offset)
                } else {
                    orphanDao.getOrphansPaged(PAGE_SIZE, offset)
                }
                _state.update {
                    val combined = it.orphans + more
                    it.copy(
                        orphans = combined,
                        isLoadingMore = false,
                        hasMorePages = more.size >= PAGE_SIZE && combined.size < it.totalCount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore failed", e)
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun loadCount() {
        countJob?.cancel()
        countJob = viewModelScope.launch {
            try {
                orphanRepository.getPendingCount().collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadCount failed", e)
            }
        }
    }

    fun toggleFilter() {
        _state.update { it.copy(showPendingOnly = !it.showPendingOnly) }
        loadInitialPage()
    }

    fun dismissOrphan(orphan: OrphanTransaction) {
        viewModelScope.launch {
            try {
                orphanRepository.markAsIgnored(orphan.id, "Dismissed by user")
            } catch (e: Exception) {
                Log.e(TAG, "dismissOrphan failed", e)
                _state.update { it.copy(error = "Failed to dismiss: ${e.message}") }
            }
        }
    }

    fun deleteOrphan(orphan: OrphanTransaction) {
        viewModelScope.launch {
            try {
                orphanRepository.delete(orphan)
            } catch (e: Exception) {
                Log.e(TAG, "deleteOrphan failed", e)
                _state.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
