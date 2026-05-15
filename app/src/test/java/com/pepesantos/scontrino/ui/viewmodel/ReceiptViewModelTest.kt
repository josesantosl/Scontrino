package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.*
import com.pepesantos.scontrino.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModelTest {

    private val receiptRepository: ReceiptRepository = mock()
    private val storeRepository: StoreRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val itemRepository: ItemRepository = mock()
    private lateinit var viewModel: ReceiptViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        
        whenever(receiptRepository.getAll()).thenReturn(emptyList())
        whenever(storeRepository.getAll()).thenReturn(emptyList())
        
        viewModel = ReceiptViewModel(receiptRepository, storeRepository, productRepository, itemRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadReceipts updates receipts state flow`() = runTest {
        val receipts = listOf(
            ReceiptWithStoreName(Receipt(1, 1000L, 50.0, 1), "Store A", 0xFF000000)
        )
        whenever(receiptRepository.getAll()).thenReturn(receipts)

        viewModel.loadReceipts()
        advanceUntilIdle()

        assertEquals(receipts, viewModel.receipts.value)
    }

    @Test
    fun `saveReceipt creates store and product if they don't exist`() = runTest {
        val storeName = "Market"
        val date = 2000L
        val note = "Test note"
        val items = listOf(
            ItemEntry("Product 1", 10.0, 2, 1)
        )

        whenever(storeRepository.search(storeName)).thenReturn(emptyList())
        whenever(storeRepository.insert(any())).thenReturn(1L)
        
        val product = Product(id = 1, name = "Product 1", categoryId = 1)
        whenever(productRepository.getOrCreate("Product 1")).thenReturn(product)
        
        whenever(receiptRepository.getAll()).thenReturn(emptyList())
        whenever(storeRepository.getAll()).thenReturn(emptyList())

        viewModel.saveReceipt(storeName, date, note, items)
        advanceUntilIdle()

        // Verify receipt insertion
        verify(receiptRepository).insert(argThat { 
            this.storeId == 1 && this.total == 20.0 && this.date == date && this.note == note
        }, any())
    }
}
