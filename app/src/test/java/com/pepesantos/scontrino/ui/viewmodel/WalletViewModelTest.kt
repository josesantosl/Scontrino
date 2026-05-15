package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val loyaltyCardRepository: LoyaltyCardRepository = mock()
    private val storeRepository: StoreRepository = mock()
    private lateinit var viewModel: WalletViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        
        // Mock initial load
        whenever(loyaltyCardRepository.getAll()).thenReturn(emptyList())
        
        viewModel = WalletViewModel(loyaltyCardRepository, storeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadCards updates cards state flow`() = runTest {
        val cards = listOf(
            LoyaltyCardWithStore(LoyaltyCard(1, 1, "123"), "Store 1", 0xFF000000)
        )
        whenever(loyaltyCardRepository.getAll()).thenReturn(cards)

        viewModel.loadCards()
        advanceUntilIdle()

        assertEquals(cards, viewModel.cards.value)
    }

    @Test
    fun `saveCard creates store if not exists and saves card`() = runTest {
        val storeName = "New Store"
        val cardNumber = "999"
        val color = 0xFF112233L
        
        whenever(storeRepository.search(storeName)).thenReturn(emptyList())
        whenever(storeRepository.insert(any())).thenReturn(1L)
        whenever(loyaltyCardRepository.getAll()).thenReturn(emptyList())

        viewModel.saveCard(storeName, cardNumber, color)
        advanceUntilIdle()

        verify(storeRepository).insert(argThat { name == storeName && this.color == color })
        verify(loyaltyCardRepository).insert(argThat { storeId == 1 && this.cardNumber == cardNumber })
    }
}
