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

    init {
        loadReceipts()
        loadCategories()
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            _categories.value = categoryRepository.getAll()
        }
    }
    private val _storeNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val storeNames: StateFlow<Map<Int, String>> = _storeNames

    fun loadReceipts() {
        viewModelScope.launch {
            _isLoading.value = true
            val receipts = receiptRepository.getAll()
            // Ordenamos por fecha descendente y luego por ID descendente para que el último añadido esté arriba
            _receipts.value = receipts.sortedWith(
                compareByDescending<ReceiptWithStoreName> { it.receipt.date }
                    .thenByDescending { it.receipt.id }
            )

            // Cargar nombres de tiendas
            val stores = storeRepository.getAll()
            _storeNames.value = stores.associate { it.id to it.name }

            _isLoading.value = false
        }
    }

    fun saveReceipt(
        storeName: String,
        date: Long,
        note: String?,
        items: List<ItemEntry>
    ) {
        val trimmedStoreName = storeName.trim()
        viewModelScope.launch {
            // 1. Obtener o crear la tienda - Preferimos una que ya tenga color si hay duplicados
            val storeResults = storeRepository.search(trimmedStoreName)
            val store = storeResults
                .filter { it.name.equals(trimmedStoreName, ignoreCase = true) }
                .sortedByDescending { it.color != null }
                .firstOrNull()
                ?: run {
                    val id = storeRepository.insert(
                        com.pepesantos.scontrino.data.model.Store(name = trimmedStoreName)
                    )
                    com.pepesantos.scontrino.data.model.Store(id = id.toInt(), name = trimmedStoreName)
                }

            // 2. Calcular total
            val total = items.sumOf {
                it.price * it.quantity
            }

            // 3. Crear receipt
            val receipt = Receipt(
                date = date,
                total = total,
                storeId = store.id,
                note = note?.ifBlank { null }
            )

            // 4. Obtener o crear productos y construir items
            val roomItems = items
                .filter { it.name.isNotBlank() && it.price != 0.0 }
                .map { entry ->
                    val product = productRepository.getOrCreate(entry.name, entry.categoryId)
                    Item(
                        productId = product.id,
                        price = entry.price,
                        quantity = entry.quantity,
                        receiptId = 0 // se asigna en el repository
                    )
                }

            // 5. insert all
            receiptRepository.insert(receipt, roomItems)

            // 6. reload list
            loadReceipts()
        }
    }
    private val _selectedReceipt = MutableStateFlow<ReceiptWithStoreName?>(null)
    val selectedReceipt: StateFlow<ReceiptWithStoreName?> = _selectedReceipt

    private val _selectedItems = MutableStateFlow<List<ItemEntry>>(emptyList())
    val selectedItems: StateFlow<List<ItemEntry>> = _selectedItems

    fun loadReceiptById(id: Int) {
        viewModelScope.launch {
            _selectedItems.value = emptyList()
            _selectedReceipt.value = receiptRepository.getById(id)
            _selectedItems.value = itemRepository.getItemEntriesByReceipt(id)
        }
    }
    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            receiptRepository.delete(receipt)
            loadReceipts()
        }
    }
    fun updateReceipt(
        receipt: Receipt,
        storeName: String,
        date: Long,
        note: String?,
        items: List<ItemEntry>
    ) {
        val trimmedStoreName = storeName.trim()
        viewModelScope.launch {
            // 1. Obtener o crear la tienda - Preferimos una que ya tenga color
            val storeResults = storeRepository.search(trimmedStoreName)
            val store = storeResults
                .filter { it.name.equals(trimmedStoreName, ignoreCase = true) }
                .sortedByDescending { it.color != null }
                .firstOrNull()
                ?: run {
                    val id = storeRepository.insert(
                        Store(name = trimmedStoreName)
                    )
                    Store(id = id.toInt(), name = trimmedStoreName)
                }

            // 2. Calcular nuevo total
            val total = items.sumOf {
                it.price * it.quantity
            }

            // 3. Actualizar receipt
            val updatedReceipt = receipt.copy(
                date = date,
                total = total,
                storeId = store.id,
                note = note?.ifBlank { null }
            )
            receiptRepository.update(updatedReceipt)

            // 4. Borrar items anteriores y reinsertar
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

            // 5. Recargar lista
            loadReceipts()
        }
    }
}
