package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WalletViewModel(
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val storeRepository: StoreRepository
) : ViewModel() {

    private val _cards = MutableStateFlow<List<LoyaltyCardWithStore>>(emptyList())
    val cards: StateFlow<List<LoyaltyCardWithStore>> = _cards

    init {
        loadCards()
    }

    fun loadCards() {
        viewModelScope.launch {
            _cards.value = loyaltyCardRepository.getAll()
        }
    }

    fun saveCard(storeName: String, cardNumber: String, color: Long) {
        viewModelScope.launch {
            // 1. Obtener o crear la tienda
            val storeResults = storeRepository.search(storeName)
            val store = storeResults.firstOrNull { it.name.equals(storeName, ignoreCase = true) }
                ?: run {
                    val id = storeRepository.insert(Store(name = storeName, color = color))
                    Store(id = id.toInt(), name = storeName, color = color)
                }
            
            // 2. Actualizar color de la tienda si es necesario (ya que ahora la tarjeta manda el color)
            if (store.color != color) {
                storeRepository.update(store.copy(color = color))
            }

            // 3. Crear la tarjeta de fidelidad
            loyaltyCardRepository.insert(
                LoyaltyCard(storeId = store.id, cardNumber = cardNumber)
            )
            loadCards()
        }
    }

    fun deleteCard(card: LoyaltyCard) {
        viewModelScope.launch {
            loyaltyCardRepository.delete(card)
            loadCards()
        }
    }

    fun updateCard(card: LoyaltyCard, storeName: String, color: Long) {
        viewModelScope.launch {
            // En una app real, actualizaríamos la relación con la tienda o su nombre/color
            // Por ahora simplificamos: actualizamos el color de la tienda vinculada
            val cardWithStore = cards.value.firstOrNull { it.card.id == card.id }
            cardWithStore?.let {
                val store = storeRepository.getById(it.card.storeId)
                store?.let { s ->
                    storeRepository.update(s.copy(name = storeName, color = color))
                }
            }
            loyaltyCardRepository.update(card)
            loadCards()
        }
    }
}
