package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.repository.ReceiptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val receiptRepository: ReceiptRepository = mock()
    private lateinit var viewModel: StatsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        
        whenever(receiptRepository.getTotalSpentInRange(any(), any())).thenReturn(0.0)
        
        viewModel = StatsViewModel(receiptRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadStats updates state with totals from repository`() = runTest {
        whenever(receiptRepository.getTotalSpentInRange(any(), any()))
            .thenReturn(150.0) // This month
            .thenReturn(100.0) // Last month

        viewModel.loadStats()
        advanceUntilIdle()

        val state = viewModel.stats.value
        assertEquals(150.0, state.totalThisMonth, 0.1)
        assertEquals(100.0, state.totalLastMonth, 0.1)
        assertEquals(false, state.isLoading)
    }
}
