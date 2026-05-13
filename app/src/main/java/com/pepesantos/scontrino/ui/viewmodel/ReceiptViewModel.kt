package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.Receipt
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.pepesantos.scontrino.data.model.ItemEntry

class ReceiptViewModel(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _receipts = MutableStateFlow<List<Receipt>>(emptyList())
    val receipts: StateFlow<List<Receipt>> = _receipts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadReceipts()
    }

    fun loadReceipts() {
        viewModelScope.launch {
            _isLoading.value = true
            _receipts.value = receiptRepository.getAll()
            _isLoading.value = false
        }
    }

    fun saveReceipt(
        storeName: String,
        date: Long,
        note: String?,
        items: List<ItemEntry>
    ) {
        viewModelScope.launch {
            // 1. Obtener o crear la tienda
            val storeResults = storeRepository.search(storeName)
            val store = storeResults.firstOrNull { it.name.equals(storeName, ignoreCase = true) }
                ?: run {
                    val id = storeRepository.insert(
                        com.pepesantos.scontrino.data.model.Store(name = storeName)
                    )
                    com.pepesantos.scontrino.data.model.Store(id = id.toInt(), name = storeName)
                }

            // 2. Calcular total
            val total = items.sumOf {
                (it.price.toDoubleOrNull() ?: 0.0) * it.quantity
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
                .filter { it.name.isNotBlank() && it.price.isNotBlank() }
                .map { entry ->
                    val product = productRepository.getOrCreate(entry.name)
                    Item(
                        productId = product.id,
                        price = entry.price.toDoubleOrNull() ?: 0.0,
                        quantity = entry.quantity,
                        receiptId = 0 // se asigna en el repository
                    )
                }

            // 5. Insertar todo
            receiptRepository.insert(receipt, roomItems)

            // 6. Recargar lista
            loadReceipts()
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            receiptRepository.delete(receipt)
            loadReceipts()
        }
    }
}