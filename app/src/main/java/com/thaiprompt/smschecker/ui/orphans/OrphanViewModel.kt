package com.thaiprompt.smschecker.ui.orphans

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val pendingCount: Int = 0,
    val showPendingOnly: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class OrphanViewModel @Inject constructor(
    private val orphanRepository: OrphanTransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrphanState())
    val state: StateFlow<OrphanState> = _state.asStateFlow()

    private var orphansJob: Job? = null
    private var countJob: Job? = null

    init {
        loadOrphans()
        loadCount()
    }

    private fun loadOrphans() {
        orphansJob?.cancel()
        orphansJob = viewModelScope.launch {
            try {
                val flow = if (_state.value.showPendingOnly) {
                    orphanRepository.getPendingOrphans()
                } else {
                    orphanRepository.getAllOrphans()
                }

                flow.collect { orphans ->
                    _state.update { it.copy(orphans = orphans, isLoading = false, error = null) }
                }
            } catch (e: Exception) {
                Log.e("OrphanViewModel", "Error loading orphans", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
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
                Log.e("OrphanViewModel", "Error loading count", e)
            }
        }
    }

    fun toggleFilter() {
        _state.update { it.copy(showPendingOnly = !it.showPendingOnly) }
        loadOrphans()
    }

    /**
     * ยกเลิก/ลบ orphan transaction
     * ผู้ใช้ไม่ต้องการติดตามยอดนี้แล้ว
     */
    fun dismissOrphan(orphan: OrphanTransaction) {
        viewModelScope.launch {
            try {
                orphanRepository.markAsIgnored(orphan.id, "Dismissed by user")
                Log.i("OrphanViewModel", "Dismissed orphan: ${orphan.id}")
            } catch (e: Exception) {
                Log.e("OrphanViewModel", "Error dismissing orphan", e)
                _state.update { it.copy(error = "Failed to dismiss: ${e.message}") }
            }
        }
    }

    /**
     * ลบ orphan ออกจากระบบถาวร
     */
    fun deleteOrphan(orphan: OrphanTransaction) {
        viewModelScope.launch {
            try {
                orphanRepository.delete(orphan)
                Log.i("OrphanViewModel", "Deleted orphan: ${orphan.id}")
            } catch (e: Exception) {
                Log.e("OrphanViewModel", "Error deleting orphan", e)
                _state.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
