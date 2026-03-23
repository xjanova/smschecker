package com.thaiprompt.smschecker.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.MatchHistoryDao
import com.thaiprompt.smschecker.data.db.MatchSummary
import com.thaiprompt.smschecker.data.model.MatchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchHistoryState(
    val history: List<MatchHistory> = emptyList(),
    val summary: MatchSummary? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class MatchHistoryViewModel @Inject constructor(
    private val matchHistoryDao: MatchHistoryDao
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
        private const val TAG = "MatchHistoryVM"
    }

    private val _state = MutableStateFlow(MatchHistoryState())
    val state: StateFlow<MatchHistoryState> = _state.asStateFlow()

    private var countJob: Job? = null

    init {
        loadInitialPage()
        loadSummary()
    }

    private fun loadInitialPage() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val history = matchHistoryDao.getHistoryPaged(PAGE_SIZE, 0)
                val totalCount = matchHistoryDao.getTotalCountOnce()
                _state.update {
                    it.copy(
                        history = history,
                        isLoading = false,
                        hasMorePages = history.size >= PAGE_SIZE && history.size < totalCount,
                        totalCount = totalCount,
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
                val offset = s.history.size
                val more = matchHistoryDao.getHistoryPaged(PAGE_SIZE, offset)
                _state.update {
                    val combined = it.history + more
                    it.copy(
                        history = combined,
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

    private fun loadSummary() {
        viewModelScope.launch {
            try {
                val since = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                val summary = matchHistoryDao.getSummary(since)
                _state.update { it.copy(summary = summary) }
            } catch (e: Exception) {
                Log.e(TAG, "loadSummary failed", e)
            }
        }

        countJob?.cancel()
        countJob = viewModelScope.launch {
            try {
                matchHistoryDao.getSuccessCount().collect { count ->
                    _state.update { it.copy(successCount = count) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadCount failed", e)
            }
        }
    }

    fun refresh() {
        loadInitialPage()
        loadSummary()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
