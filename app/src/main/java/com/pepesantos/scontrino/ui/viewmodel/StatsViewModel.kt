package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class StatsState(
    val totalThisMonth: Double = 0.0,
    val totalLastMonth: Double = 0.0,
    val spendingByCategory: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false
)

class StatsViewModel(
    private val receiptRepository: ReceiptRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(StatsState())
    val stats: StateFlow<StatsState> = _stats

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _stats.value = _stats.value.copy(isLoading = true)

            val (startOfMonth, endOfMonth) = currentMonthRange()
            val (startOfLastMonth, endOfLastMonth) = lastMonthRange()

            val totalThisMonth = receiptRepository.getTotalSpentInRange(startOfMonth, endOfMonth) ?: 0.0
            val totalLastMonth = receiptRepository.getTotalSpentInRange(startOfLastMonth, endOfLastMonth) ?: 0.0

            _stats.value = StatsState(
                totalThisMonth = totalThisMonth,
                totalLastMonth = totalLastMonth,
                isLoading = false
            )
        }
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val start = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val end = calendar.timeInMillis
        return Pair(start, end)
    }

    private fun lastMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val start = calendar.timeInMillis
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val end = calendar.timeInMillis
        return Pair(start, end)
    }
}