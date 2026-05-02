package com.thaiprompt.smschecker.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.DailyIncomeExpense
import com.thaiprompt.smschecker.data.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * ช่วงเวลาสำหรับกราฟรายได้
 *  WEEK  — 7 วันย้อนหลัง, aggregate รายวัน
 *  MONTH — 30 วันย้อนหลัง, aggregate รายวัน
 *  YEAR  — 12 เดือนย้อนหลัง, aggregate รายเดือน
 */
enum class RevenuePeriod(val days: Int, val isMonthly: Boolean) {
    WEEK(7, false),
    MONTH(30, false),
    YEAR(365, true)
}

data class RevenueDetailState(
    val period: RevenuePeriod = RevenuePeriod.WEEK,
    val data: List<DailyIncomeExpense> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val avgIncomePerBucket: Double = 0.0,
    val avgExpensePerBucket: Double = 0.0,
    val maxIncome: Double = 0.0,
    val maxExpense: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class RevenueDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _state = MutableStateFlow(RevenueDetailState())
    val state: StateFlow<RevenueDetailState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadData(RevenuePeriod.WEEK)
    }

    fun setPeriod(period: RevenuePeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, isLoading = true) }
        loadData(period)
    }

    private fun loadData(period: RevenuePeriod) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val data = if (period.isMonthly) {
                    loadMonthlyData(months = 12)
                } else {
                    loadDailyData(days = period.days)
                }

                val totalIncome = data.sumOf { it.credit }
                val totalExpense = data.sumOf { it.debit }
                val nonZero = data.size.coerceAtLeast(1)

                _state.update {
                    it.copy(
                        data = data,
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        avgIncomePerBucket = totalIncome / nonZero,
                        avgExpensePerBucket = totalExpense / nonZero,
                        maxIncome = data.maxOfOrNull { d -> d.credit } ?: 0.0,
                        maxExpense = data.maxOfOrNull { d -> d.debit } ?: 0.0,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadData($period) failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * ย้อนหลัง N วัน aggregate รายวัน — เติม 0 สำหรับวันที่ไม่มีรายการ
     */
    private suspend fun loadDailyData(days: Int): List<DailyIncomeExpense> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }
        val since = cal.timeInMillis
        val raw = transactionDao.getDailyIncomeExpense(since).associateBy { it.date }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableListOf<DailyIncomeExpense>()
        repeat(days) {
            val key = sdf.format(cal.time)
            result.add(raw[key] ?: DailyIncomeExpense(date = key, credit = 0.0, debit = 0.0))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /**
     * ย้อนหลัง N เดือน aggregate รายเดือน — เติม 0 สำหรับเดือนที่ไม่มีรายการ
     */
    private suspend fun loadMonthlyData(months: Int): List<DailyIncomeExpense> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(months - 1))
        }
        val since = cal.timeInMillis
        val raw = transactionDao.getMonthlyIncomeExpense(since).associateBy { it.date }
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val result = mutableListOf<DailyIncomeExpense>()
        repeat(months) {
            val key = sdf.format(cal.time)
            result.add(raw[key] ?: DailyIncomeExpense(date = key, credit = 0.0, debit = 0.0))
            cal.add(Calendar.MONTH, 1)
        }
        return result
    }

    companion object {
        private const val TAG = "RevenueDetailVM"
    }
}
