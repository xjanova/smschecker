package com.thaiprompt.smschecker.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thaiprompt.smschecker.data.db.DailyApprovedByServer
import com.thaiprompt.smschecker.data.db.DailyIncomeExpense
import com.thaiprompt.smschecker.data.db.OrderApprovalDao
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
 *  WEEK   — 7 วันย้อนหลัง, รายวัน
 *  MONTH  — 30 วันย้อนหลัง, รายวัน
 *  YEAR   — 12 เดือนย้อนหลัง, รายเดือน
 *  CUSTOM — ผู้ใช้กำหนด, auto-aggregate รายวัน/รายเดือนตามความยาวช่วง
 */
enum class RevenuePeriod {
    WEEK, MONTH, YEAR, CUSTOM
}

/**
 * series หนึ่งเส้นในกราฟ — abstract (ไม่มี Color)
 * Screen รับไปแล้วจับคู่กับ palette เอง
 */
data class RevenueSeries(
    val key: String,            // unique (e.g. "server:5", "refund", "bank-credit")
    val name: String,
    val total: Double,
    val data: List<Double>      // align กับ buckets
)

data class RevenueDetailState(
    val period: RevenuePeriod = RevenuePeriod.WEEK,
    val customStartMs: Long? = null,
    val customEndMs: Long? = null,
    val isMonthly: Boolean = false,             // true = aggregate รายเดือน

    // Buckets อัพเดททุกครั้งที่โหลดข้อมูลใหม่ — ทุก series ใช้ index นี้
    val buckets: List<String> = emptyList(),

    // กราฟบิล
    val billServerSeries: List<RevenueSeries> = emptyList(),
    val billRefundSeries: RevenueSeries? = null,    // อาจ null ถ้าทุกค่าเป็น 0
    val billIncomeTotal: Double = 0.0,
    val billRefundTotal: Double = 0.0,

    // กราฟธนาคาร
    val bankCreditSeries: RevenueSeries? = null,
    val bankDebitSeries: RevenueSeries? = null,
    val bankCreditTotal: Double = 0.0,
    val bankDebitTotal: Double = 0.0,

    val isLoading: Boolean = true
)

@HiltViewModel
class RevenueDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val orderApprovalDao: OrderApprovalDao
) : ViewModel() {

    private val _state = MutableStateFlow(RevenueDetailState())
    val state: StateFlow<RevenueDetailState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadData(RevenuePeriod.WEEK, null, null)
    }

    fun setPeriod(period: RevenuePeriod) {
        if (period == RevenuePeriod.CUSTOM) {
            // ไม่โหลดทันที รอ user เลือกวัน
            _state.update { it.copy(period = period) }
            return
        }
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, isLoading = true) }
        loadData(period, null, null)
    }

    fun setCustomRange(startMs: Long, endMs: Long) {
        if (endMs <= startMs) return
        _state.update {
            it.copy(
                period = RevenuePeriod.CUSTOM,
                customStartMs = startMs,
                customEndMs = endMs,
                isLoading = true
            )
        }
        loadData(RevenuePeriod.CUSTOM, startMs, endMs)
    }

    private fun loadData(period: RevenuePeriod, customStart: Long?, customEnd: Long?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val (startMs, endMs, monthly, buckets) = computeRange(period, customStart, customEnd)

                val billByServer = if (monthly) {
                    orderApprovalDao.getMonthlyApprovedAmountByServer(startMs, endMs)
                } else {
                    orderApprovalDao.getDailyApprovedAmountByServer(startMs, endMs)
                }
                val bankRows = if (monthly) {
                    transactionDao.getMonthlyIncomeExpenseRange(startMs, endMs)
                } else {
                    transactionDao.getDailyIncomeExpenseRange(startMs, endMs)
                }

                val billServerSeries = buildBillServerSeries(billByServer, buckets)
                val billRefundSeries = buildRefundSeries(buckets)  // stub: ทุก 0
                val (creditSeries, debitSeries) = buildBankSeries(bankRows, buckets)

                val billIncomeTotal = billServerSeries.sumOf { it.total }
                val billRefundTotal = billRefundSeries?.total ?: 0.0

                _state.update {
                    it.copy(
                        period = period,
                        customStartMs = customStart,
                        customEndMs = customEnd,
                        isMonthly = monthly,
                        buckets = buckets,
                        billServerSeries = billServerSeries,
                        billRefundSeries = billRefundSeries,
                        billIncomeTotal = billIncomeTotal,
                        billRefundTotal = billRefundTotal,
                        bankCreditSeries = creditSeries,
                        bankDebitSeries = debitSeries,
                        bankCreditTotal = creditSeries?.total ?: 0.0,
                        bankDebitTotal = debitSeries?.total ?: 0.0,
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
     * คำนวณช่วงเวลา + buckets — รองรับ aggregate auto:
     *  - WEEK/MONTH = รายวัน
     *  - YEAR       = รายเดือน
     *  - CUSTOM     = > 60 วัน → รายเดือน, ไม่งั้นรายวัน
     */
    private fun computeRange(
        period: RevenuePeriod,
        customStart: Long?,
        customEnd: Long?
    ): RangeResult {
        return when (period) {
            RevenuePeriod.WEEK -> dailyRange(daysBack = 7)
            RevenuePeriod.MONTH -> dailyRange(daysBack = 30)
            RevenuePeriod.YEAR -> monthlyRange(monthsBack = 12)
            RevenuePeriod.CUSTOM -> {
                val start = customStart ?: return dailyRange(daysBack = 7)
                val end = customEnd ?: return dailyRange(daysBack = 7)
                val days = ((end - start) / (24L * 60 * 60 * 1000)).toInt() + 1
                if (days > 60) customMonthlyRange(start, end) else customDailyRange(start, end)
            }
        }
    }

    private data class RangeResult(
        val startMs: Long,
        val endMs: Long,
        val monthly: Boolean,
        val buckets: List<String>
    )

    private fun dailyRange(daysBack: Int): RangeResult {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(daysBack - 1))
        }
        val start = cal.timeInMillis
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val buckets = mutableListOf<String>()
        repeat(daysBack) {
            buckets.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        // end = start ของวันถัดจากวันสุดท้าย
        return RangeResult(start, cal.timeInMillis, monthly = false, buckets)
    }

    private fun monthlyRange(monthsBack: Int): RangeResult {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(monthsBack - 1))
        }
        val start = cal.timeInMillis
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val buckets = mutableListOf<String>()
        repeat(monthsBack) {
            buckets.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return RangeResult(start, cal.timeInMillis, monthly = true, buckets)
    }

    private fun customDailyRange(startMs: Long, endMs: Long): RangeResult {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val buckets = mutableListOf<String>()
        // walk จนถึง endMs
        while (cal.timeInMillis <= endMs) {
            buckets.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return RangeResult(start, cal.timeInMillis, monthly = false, buckets)
    }

    private fun customMonthlyRange(startMs: Long, endMs: Long): RangeResult {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val buckets = mutableListOf<String>()
        while (cal.timeInMillis <= endMs) {
            buckets.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return RangeResult(start, cal.timeInMillis, monthly = true, buckets)
    }

    /**
     * จัด rows จาก DAO ให้เป็น series ต่อ server
     * - กระจาย amount ลง bucket ตามวันที่
     * - sort ตาม total descending เพื่อให้ palette สีเข้มเป็น server ที่รายได้สูงสุด
     */
    private fun buildBillServerSeries(
        rows: List<DailyApprovedByServer>,
        buckets: List<String>
    ): List<RevenueSeries> {
        val byServer = rows.groupBy { it.serverId }
        val bucketIdx = buckets.withIndex().associate { (i, k) -> k to i }

        val series = byServer.map { (serverId, serverRows) ->
            val data = DoubleArray(buckets.size)
            var total = 0.0
            for (r in serverRows) {
                val i = bucketIdx[r.date] ?: continue
                data[i] += r.amount
                total += r.amount
            }
            val name = serverRows.firstOrNull()?.serverName?.takeIf { it.isNotBlank() }
                ?: "Server #$serverId"
            RevenueSeries(
                key = "server:$serverId",
                name = name,
                total = total,
                data = data.toList()
            )
        }.sortedByDescending { it.total }

        return series
    }

    /**
     * Stub สำหรับ refund — ปัจจุบัน OrderApproval ไม่มี field refund
     * คืน null เพื่อไม่ render เส้นนี้ในกราฟ จนกว่า schema จะเพิ่ม field
     */
    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    private fun buildRefundSeries(buckets: List<String>): RevenueSeries? {
        // TODO: เมื่อ OrderApproval มี refundedAt + refundAmount แล้ว
        //  - query: getDailyRefundAmount(start, end)
        //  - กระจาย ลง bucket เหมือน buildBillServerSeries (ใช้ param buckets)
        return null
    }

    private fun buildBankSeries(
        rows: List<DailyIncomeExpense>,
        buckets: List<String>
    ): Pair<RevenueSeries?, RevenueSeries?> {
        val bucketIdx = buckets.withIndex().associate { (i, k) -> k to i }
        val creditArr = DoubleArray(buckets.size)
        val debitArr = DoubleArray(buckets.size)
        var creditTotal = 0.0
        var debitTotal = 0.0
        for (r in rows) {
            val i = bucketIdx[r.date] ?: continue
            creditArr[i] += r.credit
            debitArr[i] += r.debit
            creditTotal += r.credit
            debitTotal += r.debit
        }
        val credit = RevenueSeries("bank-credit", "เงินเข้า", creditTotal, creditArr.toList())
        val debit = RevenueSeries("bank-debit", "เงินออก", debitTotal, debitArr.toList())
        return credit to debit
    }

    companion object {
        private const val TAG = "RevenueDetailVM"
    }
}
