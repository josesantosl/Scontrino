package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import com.pepesantos.scontrino.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val settingsRepository: SettingsRepository = mock {
        on { paymentApp } doReturn MutableStateFlow("")
        on { paymentAppName } doReturn MutableStateFlow("")
        on { paymentAppColor } doReturn MutableStateFlow(null)
        on { paymentAppPosition } doReturn MutableStateFlow(0)
    }
    private lateinit var viewModel: WalletViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        
        // Mock initial load
        whenever(loyaltyCardRepository.getAll()).thenReturn(emptyList())
        whenever(loyaltyCardRepository.nextPosition()).thenReturn(3)
        
        viewModel = WalletViewModel(loyaltyCardRepository, storeRepository, settingsRepository)
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
        verify(loyaltyCardRepository).insert(argThat { storeId == 1 && this.cardNumber == cardNumber && position == 3 })
    }

    @Test
    fun `updateCard with another store's name moves the card there and recolours that store`() = runTest {
        val card = LoyaltyCard(id = 7, storeId = 1, cardNumber = "111", position = 2)
        val lidl = Store(2, "Lidl", color = 0xFF000000)
        whenever(storeRepository.search("lidl")).thenReturn(listOf(lidl))

        viewModel.updateCard(card, " lidl ", "222", 0xFF112233L)
        advanceUntilIdle()

        verify(storeRepository).update(lidl.copy(color = 0xFF112233L))
        verify(loyaltyCardRepository).update(card.copy(storeId = 2, cardNumber = "222"))
        verify(storeRepository, never()).getById(any())
    }

    @Test
    fun `updateCard with a new name renames the card's own store`() = runTest {
        val card = LoyaltyCard(id = 7, storeId = 1, cardNumber = "111")
        val bennet = Store(1, "Benet", color = 0xFF000000)
        whenever(storeRepository.search("Bennet")).thenReturn(listOf(bennet))
        whenever(storeRepository.getById(1)).thenReturn(bennet)

        viewModel.updateCard(card, "Bennet", "111", 0xFF000000)
        advanceUntilIdle()

        verify(storeRepository).update(bennet.copy(name = "Bennet"))
        verify(loyaltyCardRepository).update(card)
    }

    @Test
    fun `saveOrder stores each entry's index as its position`() = runTest {
        val a = LoyaltyCardWithStore(LoyaltyCard(1, 1, "1", position = 0), "A", null)
        val b = LoyaltyCardWithStore(LoyaltyCard(2, 2, "2", position = 1), "B", null)
        whenever(loyaltyCardRepository.getAll()).thenReturn(listOf(a, b))
        viewModel.loadCards()
        advanceUntilIdle()

        viewModel.saveOrder(listOf(WalletEntry.Card(b), WalletEntry.PaymentApp, WalletEntry.Card(a)))
        advanceUntilIdle()

        verify(loyaltyCardRepository).setPositions(mapOf(2 to 0, 1 to 2))
        verify(settingsRepository).setPaymentAppPosition(1)
        assertEquals(listOf(2, 1), viewModel.cards.value.map { it.card.id })
    }

    @Test
    fun `loadStores updates stores state flow`() = runTest {
        val stores = listOf(Store(1, "Bennet"), Store(2, "Lidl"))
        whenever(storeRepository.getAll()).thenReturn(stores)

        viewModel.loadStores()
        advanceUntilIdle()

        assertEquals(stores, viewModel.stores.value)
    }
}
