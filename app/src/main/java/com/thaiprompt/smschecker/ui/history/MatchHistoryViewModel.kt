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
    val successCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class MatchHistoryViewModel @Inject constructor(
    private val matchHistoryDao: MatchHistoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(MatchHistoryState())
    val state: StateFlow<MatchHistoryState> = _state.asStateFlow()

    private var historyJob: Job? = null
    private var countJob: Job? = null

    init {
        loadHistory()
        loadSummary()
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try {
                matchHistoryDao.getRecentHistory(100).collect { history ->
                    _state.update { it.copy(history = history, isLoading = false, error = null) }
                }
            } catch (e: Exception) {
                Log.e("MatchHistoryVM", "Error loading history", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadSummary() {
        viewModelScope.launch {
            try {
                // Get summary for last 7 days
                val since = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                val summary = matchHistoryDao.getSummary(since)
                _state.update { it.copy(summary = summary) }
            } catch (e: Exception) {
                Log.e("MatchHistoryVM", "Error loading summary", e)
            }
        }

        countJob?.cancel()
        countJob = viewModelScope.launch {
            try {
                matchHistoryDao.getSuccessCount().collect { count ->
                    _state.update { it.copy(successCount = count) }
                }
            } catch (e: Exception) {
                Log.e("MatchHistoryVM", "Error loading count", e)
            }
        }
    }

    fun refresh() {
        loadHistory()
        loadSummary()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
