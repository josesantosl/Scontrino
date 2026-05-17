package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.Receipt
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.data.repository.CategoryRepository

/**
 * ViewModel responsible for managing receipts, their items, and related store/category data.
 */
class ReceiptViewModel(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

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

    init {
        loadReceipts()
        loadCategories()
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
     * Saves a new receipt with its associated items.
     * Automatically matches or creates the store and products.
     *
     * @param storeName Name of the store (will be trimmed and matched case-insensitively).
     * @param date Timestamp of the receipt.
     * @param note Optional text note.
     * @param items List of items to be saved.
     */
    fun saveReceipt(
        storeName: String,
        date: Long,
        note: String?,
        items: List<ItemEntry>
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

            // 2. Calculate total based on unit prices and quantities
            val total = items.sumOf { it.price * it.quantity }

            // 3. Create receipt entity
            val receipt = Receipt(
                date = date,
                total = total,
                storeId = store.id,
                note = note?.ifBlank { null }
            )

            // 4. Resolve products and build item entities
            val roomItems = items
                .filter { it.name.isNotBlank() && it.price != 0.0 }
                .map { entry ->
                    val product = productRepository.getOrCreate(entry.name, entry.categoryId)
                    Item(
                        productId = product.id,
                        price = entry.price,
                        quantity = entry.quantity,
                        receiptId = 0 // Assigned in the repository level
                    )
                }

            // 5. Atomic insertion
            receiptRepository.insert(receipt, roomItems)

            // 6. Refresh UI
            loadReceipts()
        }
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
            val roomItems = items
                .filter { it.name.isNotBlank() && it.price != 0.0 }
                .map { entry ->
                    val product = productRepository.getOrCreate(entry.name, entry.categoryId)
                    Item(
                        productId = product.id,
                        price = entry.price,
                        quantity = entry.quantity,
                        receiptId = receipt.id
                    )
                }
            itemRepository.insertAll(roomItems)

            loadReceipts()
        }
    }
}
