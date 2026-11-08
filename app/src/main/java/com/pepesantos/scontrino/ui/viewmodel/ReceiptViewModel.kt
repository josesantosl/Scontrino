package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.Receipt
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.data.repository.CategoryRepository
import com.pepesantos.scontrino.ocr.draft.ReceiptDraft
import com.pepesantos.scontrino.ocr.draft.ReceiptDraftBuilder
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt

/**
 * ViewModel responsible for managing receipts, their items, and related store/category data.
 */
class ReceiptViewModel(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    /** Currency chosen in the settings, given to new receipts. */
    private val currencyProvider: () -> String = { "EUR" },
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    private val _receipts = MutableStateFlow<List<ReceiptWithStoreName>>(emptyList())
    val receipts: StateFlow<List<ReceiptWithStoreName>> = _receipts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _storeNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val storeNames: StateFlow<Map<Int, String>> = _storeNames

    private val _selectedReceipt = MutableStateFlow<ReceiptWithStoreName?>(null)
    val selectedReceipt: StateFlow<ReceiptWithStoreName?> = _selectedReceipt

    private val _selectedItems = MutableStateFlow<List<ItemEntry>>(emptyList())
    val selectedItems: StateFlow<List<ItemEntry>> = _selectedItems

    private val _purchaseHistory = MutableStateFlow(PurchaseHistory.EMPTY)
    /** Past purchases in the store of the receipt being edited, see [loadPurchaseHistory]. */
    val purchaseHistory: StateFlow<PurchaseHistory> = _purchaseHistory
    private var purchaseHistoryJob: Job? = null

    init {
        loadReceipts()
        loadCategories()
        loadStoresAndProducts()
    }
    
    /**
     * Loads all available categories from the database.
     */
    private fun loadCategories() {
        viewModelScope.launch {
            _categories.value = categoryRepository.getAll()
        }
    }

    /**
     * Loads all available stores and products from the database for autocomplete suggestions.
     */
    fun loadStoresAndProducts() {
        viewModelScope.launch {
            _stores.value = storeRepository.getAll()
            _products.value = productRepository.getAll()
        }
    }

    /**
     * Fetches all receipts from the repository and sorts them chronologically (newest first).
     */
    fun loadReceipts() {
        viewModelScope.launch {
            _isLoading.value = true
            val receipts = receiptRepository.getAll()
            // Sort by date descending and ID descending to ensure consistent and correct order
            _receipts.value = receipts.sortedWith(
                compareByDescending<ReceiptWithStoreName> { it.receipt.date }
                    .thenByDescending { it.receipt.id }
            )

            // Cache store names for quick lookup in the UI
            val stores = storeRepository.getAll()
            _storeNames.value = stores.associate { it.id to it.name }

            _isLoading.value = false
        }
    }

    /**
     * Loads what was bought before in the store called [storeName], to prefill prices and
     * weights and show how they changed. [excludeReceiptId] is the receipt being edited
     * (or -1), so that it is not compared with itself.
     */
    fun loadPurchaseHistory(storeName: String, excludeReceiptId: Int) {
        val trimmedStoreName = storeName.trim()
        // Only the latest store name matters: drop a lookup still running for an older one.
        purchaseHistoryJob?.cancel()
        purchaseHistoryJob = viewModelScope.launch {
            _purchaseHistory.value = if (trimmedStoreName.isEmpty()) {
                PurchaseHistory.EMPTY
            } else {
                PurchaseHistory(itemRepository.getPurchasesInStore(trimmedStoreName, excludeReceiptId))
            }
        }
    }

    /**
     * Builds the prefilled data for the new receipt screen from a receipt read by
     * OCR, using the stores, products and categories already loaded.
     */
    fun createDraft(parsed: ParsedReceipt): ReceiptDraft =
        ReceiptDraftBuilder.build(parsed, _stores.value, _products.value, _categories.value)

    /**
     * Saves a new receipt with its associated items.
     * Automatically matches or creates the store and products.
     *
     * @param storeName Name of the store (will be trimmed and matched case-insensitively).
     * @param date Timestamp of the receipt.
     * @param note Optional text note.
     * @param items List of items to be saved.
     * @param storeVatNumber VAT number read from a scanned receipt; remembered on the
     * store (if it has none yet) to recognise it on future scans.
     */
    fun saveReceipt(
        storeName: String,
        date: Long,
        note: String?,
        items: List<ItemEntry>,
        storeVatNumber: String? = null,
    ) {
        val trimmedStoreName = storeName.trim()
        viewModelScope.launch {
            // 1. Get or create store - Prioritizes stores that already have a color (loyalty card holders)
            val storeResults = storeRepository.search(trimmedStoreName)
            val store = storeResults
                .filter { it.name.equals(trimmedStoreName, ignoreCase = true) }
                .sortedByDescending { it.color != null }
                .firstOrNull()
                ?: run {
                    val id = storeRepository.insert(Store(name = trimmedStoreName))
                    Store(id = id.toInt(), name = trimmedStoreName)
                }
            if (storeVatNumber != null && store.vatNumber == null) {
                storeRepository.update(store.copy(vatNumber = storeVatNumber))
            }

            // 2. Calculate total based on unit prices and quantities
            val total = items.sumOf { it.price * it.quantity }

            // 3. Create receipt entity
            val receipt = Receipt(
                date = date,
                total = total,
                storeId = store.id,
                note = note?.ifBlank { null },
                currency = currencyProvider(),
            )

            // 4. Resolve products and build item entities
            val roomItems = toRoomItems(items, receiptId = 0) // Assigned in the repository level

            // 5. Atomic insertion
            receiptRepository.insert(receipt, roomItems)

            // 6. Refresh UI
            loadReceipts()
            loadStoresAndProducts()
        }
    }

    /**
     * Finds or creates the product of every named entry, saving the alias and category
     * given to it in the receipt screen (a category change on a product with alias moves
     * the whole group), and builds the items of receipt [receiptId].
     */
    private suspend fun toRoomItems(items: List<ItemEntry>, receiptId: Int): List<Item> =
        items
            .filter { it.name.isNotBlank() }
            .map { entry ->
                // A null alias means "not touched here": never wipe an alias set elsewhere.
                val product = productRepository.getOrCreate(entry.name, entry.categoryId, entry.alias)
                Item(
                    productId = product.id,
                    price = entry.price,
                    quantity = entry.quantity,
                    receiptId = receiptId,
                    weight = entry.weight
                )
            }

    /**
     * Loads a specific receipt and its items into the "selected" states for editing.
     */
    fun loadReceiptById(id: Int) {
        viewModelScope.launch {
            _selectedItems.value = emptyList() // Clear state to avoid visual glitches
            _selectedReceipt.value = receiptRepository.getById(id)
            _selectedItems.value = itemRepository.getItemEntriesByReceipt(id)
        }
    }

    /**
     * Deletes a receipt from the database and refreshes the list.
     */
    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            receiptRepository.delete(receipt)
            loadReceipts()
        }
    }

    /**
     * Updates an existing receipt and replaces its items.
     */
    fun updateReceipt(
        receipt: Receipt,
        storeName: String,
        date: Long,
        note: String?,
        items: List<ItemEntry>
    ) {
        val trimmedStoreName = storeName.trim()
        viewModelScope.launch {
            // 1. Resolve store with color priority
            val storeResults = storeRepository.search(trimmedStoreName)
            val store = storeResults
                .filter { it.name.equals(trimmedStoreName, ignoreCase = true) }
                .sortedByDescending { it.color != null }
                .firstOrNull()
                ?: run {
                    val id = storeRepository.insert(Store(name = trimmedStoreName))
                    Store(id = id.toInt(), name = trimmedStoreName)
                }

            val total = items.sumOf { it.price * it.quantity }

            val updatedReceipt = receipt.copy(
                date = date,
                total = total,
                storeId = store.id,
                note = note?.ifBlank { null }
            )
            receiptRepository.update(updatedReceipt)

            // Delete old items and insert new ones to handle removals/additions simply
            itemRepository.deleteByReceipt(receipt.id)
            itemRepository.insertAll(toRoomItems(items, receipt.id))

            loadReceipts()
            loadStoresAndProducts()
        }
    }
}
