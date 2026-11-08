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
    private val categoryRepository: CategoryRepository = mock()
    private lateinit var viewModel: ReceiptViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        
        whenever(receiptRepository.getAll()).thenReturn(emptyList())
        whenever(storeRepository.getAll()).thenReturn(emptyList())
        whenever(categoryRepository.getAll()).thenReturn(emptyList())
        
        viewModel = ReceiptViewModel(receiptRepository, storeRepository, productRepository, itemRepository, categoryRepository)
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

    @Test
    fun `saveReceipt saves items with price 0 as shopping list items`() = runTest {
        val storeName = "Supermarket"
        val date = 3000L
        val note = "Shopping list"
        val items = listOf(
            ItemEntry("Apples", 0.0, 1, 1),
            ItemEntry("Milk", 0.0, 2, 1),
            ItemEntry("", 0.0, 1, 1) // Empty item should be filtered out
        )

        whenever(storeRepository.search(storeName)).thenReturn(emptyList())
        whenever(storeRepository.insert(any())).thenReturn(1L)
        
        val productApples = Product(id = 1, name = "Apples", categoryId = 1)
        val productMilk = Product(id = 2, name = "Milk", categoryId = 1)
        whenever(productRepository.getOrCreate("Apples", 1)).thenReturn(productApples)
        whenever(productRepository.getOrCreate("Milk", 1)).thenReturn(productMilk)
        
        whenever(receiptRepository.getAll()).thenReturn(emptyList())
        whenever(storeRepository.getAll()).thenReturn(emptyList())

        viewModel.saveReceipt(storeName, date, note, items)
        advanceUntilIdle()

        // Verify receipt insertion with total 0.0 and 2 items with price 0.0
        verify(receiptRepository).insert(
            argThat { this.total == 0.0 && this.storeId == 1 },
            argThat { size == 2 && all { it.price == 0.0 } }
        )
    }

    @Test
    fun `saveReceipt saves items with optional weight correctly`() = runTest {
        val storeName = "Greengrocer"
        val date = 4000L
        val items = listOf(
            ItemEntry("Bananas", 1.80, 1, 1, weight = 250.0),
            ItemEntry("Soap", 0.90, 1, 1, weight = null)
        )

        whenever(storeRepository.search(storeName)).thenReturn(emptyList())
        whenever(storeRepository.insert(any())).thenReturn(1L)
        
        val productBananas = Product(id = 1, name = "Bananas", categoryId = 1)
        val productSoap = Product(id = 2, name = "Soap", categoryId = 1)
        whenever(productRepository.getOrCreate("Bananas", 1)).thenReturn(productBananas)
        whenever(productRepository.getOrCreate("Soap", 1)).thenReturn(productSoap)
        
        whenever(receiptRepository.getAll()).thenReturn(emptyList())
        whenever(storeRepository.getAll()).thenReturn(emptyList())

        viewModel.saveReceipt(storeName, date, null, items)
        advanceUntilIdle()

        verify(receiptRepository).insert(
            any(),
            argThat {
                size == 2 &&
                first().weight == 250.0 &&
                last().weight == null
            }
        )
    }

    @Test
    fun `saveReceipt saves new aliases, removes blank ones and leaves untouched ones`() = runTest {
        val items = listOf(
            ItemEntry("VALSOIA 1LT", 1.0, alias = "latte di soia"),
            ItemEntry("PANE", 1.0, alias = ""),
            ItemEntry("MELE", 1.0, alias = null),
        )
        whenever(storeRepository.search("Bennet")).thenReturn(emptyList())
        whenever(storeRepository.insert(any())).thenReturn(1L)
        whenever(productRepository.getOrCreate("VALSOIA 1LT", 1)).thenReturn(Product(1, "VALSOIA 1LT"))
        whenever(productRepository.getOrCreate("PANE", 1)).thenReturn(Product(2, "PANE", alias = "Pane"))
        whenever(productRepository.getOrCreate("MELE", 1)).thenReturn(Product(3, "MELE", alias = "Mele"))

        viewModel.saveReceipt("Bennet", 1L, null, items)
        advanceUntilIdle()

        verify(productRepository).setAlias(1, "latte di soia")
        verify(productRepository).setAlias(2, "")
        verify(productRepository, never()).setAlias(eq(3), anyOrNull())
    }

    @Test
    fun `loadStoresAndProducts updates stores and products state flows`() = runTest {
        val testStores = listOf(Store(1, "Mercadona"), Store(2, "Carrefour"))
        val testProducts = listOf(Product(1, "Pan", 1), Product(2, "Leche", 1))

        whenever(storeRepository.getAll()).thenReturn(testStores)
        whenever(productRepository.getAll()).thenReturn(testProducts)

        viewModel.loadStoresAndProducts()
        advanceUntilIdle()

        assertEquals(testStores, viewModel.stores.value)
        assertEquals(testProducts, viewModel.products.value)
    }
}
