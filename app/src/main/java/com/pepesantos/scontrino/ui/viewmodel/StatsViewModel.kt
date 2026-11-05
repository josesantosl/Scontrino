package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.Receipt
import com.pepesantos.scontrino.data.model.StoreSpending
import com.pepesantos.scontrino.data.model.TopCategorySpending
import com.pepesantos.scontrino.data.model.TopProductSpending
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TimePeriod {
    THIS_MONTH,
    THREE_MONTHS,
    THIS_YEAR
}

enum class TopMode {
    BY_PRODUCT,
    BY_CATEGORY
}

data class DailySpendingPoint(
    val label: String,
    val amount: Double
)

data class StatsState(
    val selectedPeriod: TimePeriod = TimePeriod.THIS_MONTH,
    val topMode: TopMode = TopMode.BY_PRODUCT,
    val totalSpent: Double = 0.0,
    val totalPreviousPeriodSpent: Double = 0.0,
    val percentageChange: Double? = null,
    val storeSpendings: List<StoreSpending> = emptyList(),
    val dailySpendings: List<DailySpendingPoint> = emptyList(),
    val topProducts: List<TopProductSpending> = emptyList(),
    val topCategories: List<TopCategorySpending> = emptyList(),
    val isLoading: Boolean = false,
    val totalThisMonth: Double = 0.0,
    val totalLastMonth: Double = 0.0
)

class StatsViewModel(
    private val receiptRepository: ReceiptRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(StatsState())
    val stats: StateFlow<StatsState> = _stats

    init {
        loadStats(TimePeriod.THIS_MONTH)
    }

    fun selectPeriod(period: TimePeriod) {
        if (_stats.value.selectedPeriod != period) {
            loadStats(period)
        }
    }

    fun selectTopMode(mode: TopMode) {
        _stats.value = _stats.value.copy(topMode = mode)
    }

    fun loadStats(period: TimePeriod = _stats.value.selectedPeriod) {
        viewModelScope.launch {
            _stats.value = _stats.value.copy(selectedPeriod = period, isLoading = true)

            val (start, end) = getPeriodRange(period)
            val (prevStart, prevEnd) = getPreviousPeriodRange(period)

            val currentTotal = receiptRepository.getTotalSpentInRange(start, end) ?: 0.0
            val prevTotal = receiptRepository.getTotalSpentInRange(prevStart, prevEnd) ?: 0.0

            val percentageChange = if (prevTotal > 0.0) {
                ((currentTotal - prevTotal) / prevTotal) * 100.0
            } else null

            val stores = receiptRepository.getStoreSpendingInRange(start, end)
            val topProducts = itemRepository.getTopProductsSpendingInRange(start, end)
            val topCategories = itemRepository.getTopCategoriesSpendingInRange(start, end)

            val receipts = receiptRepository.getReceiptsInRangeAsc(start, end)
            val dailyPoints = aggregateSpendingPoints(receipts, period)

            _stats.value = StatsState(
                selectedPeriod = period,
                topMode = _stats.value.topMode,
                totalSpent = currentTotal,
                totalPreviousPeriodSpent = prevTotal,
                percentageChange = percentageChange,
                storeSpendings = stores,
                dailySpendings = dailyPoints,
                topProducts = topProducts,
                topCategories = topCategories,
                isLoading = false,
                totalThisMonth = currentTotal,
                totalLastMonth = prevTotal
            )
        }
    }

    private fun getPeriodRange(period: TimePeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis

        val start = when (period) {
            TimePeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            TimePeriod.THREE_MONTHS -> {
                cal.add(Calendar.MONTH, -2)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            TimePeriod.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
        return Pair(start, end)
    }

    private fun getPreviousPeriodRange(period: TimePeriod): Pair<Long, Long> {
        val (startCurrent, endCurrent) = getPeriodRange(period)
        val duration = endCurrent - startCurrent
        val prevEnd = startCurrent - 1
        val prevStart = prevEnd - duration
        return Pair(prevStart, prevEnd)
    }

    private fun aggregateSpendingPoints(receipts: List<Receipt>, period: TimePeriod): List<DailySpendingPoint> {
        if (receipts.isEmpty()) return emptyList()

        val dateFormat = when (period) {
            TimePeriod.THIS_MONTH -> SimpleDateFormat("dd/MM", Locale.getDefault())
            TimePeriod.THREE_MONTHS -> SimpleDateFormat("dd/MM", Locale.getDefault())
            TimePeriod.THIS_YEAR -> SimpleDateFormat("MMM", Locale.getDefault())
        }

        val grouped = receipts.groupBy { receipt ->
            dateFormat.format(Date(receipt.date))
        }

        return grouped.map { (label, list) ->
            DailySpendingPoint(label = label, amount = list.sumOf { it.total })
        }
    }
}
