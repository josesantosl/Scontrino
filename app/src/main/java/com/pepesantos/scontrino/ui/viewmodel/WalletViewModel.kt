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

/**
 * ViewModel for managing loyalty cards and ensuring they are correctly linked to stores.
 */
class WalletViewModel(
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val storeRepository: StoreRepository
) : ViewModel() {

    private val _cards = MutableStateFlow<List<LoyaltyCardWithStore>>(emptyList())
    val cards: StateFlow<List<LoyaltyCardWithStore>> = _cards

    init {
        loadCards()
    }

    /**
     * Fetches all loyalty cards with their associated store information.
     */
    fun loadCards() {
        viewModelScope.launch {
            _cards.value = loyaltyCardRepository.getAll()
        }
    }

    /**
     * Saves a new loyalty card. 
     * Automatically links it to an existing store or creates a new one with the chosen color.
     */
    fun saveCard(storeName: String, cardNumber: String, color: Long) {
        viewModelScope.launch {
            // 1. Search for store by name (case-insensitive)
            val storeResults = storeRepository.search(storeName)
            val store = storeResults.firstOrNull { it.name.equals(storeName, ignoreCase = true) }
                ?: run {
                    // Create new store if not found
                    val id = storeRepository.insert(Store(name = storeName, color = color))
                    Store(id = id.toInt(), name = storeName, color = color)
                }
            
            // 2. Synchronize store color with the chosen card color
            if (store.color != color) {
                storeRepository.update(store.copy(color = color))
            }

            // 3. Persist the loyalty card
            loyaltyCardRepository.insert(
                LoyaltyCard(storeId = store.id, cardNumber = cardNumber)
            )
            loadCards()
        }
    }

    /**
     * Deletes a loyalty card and refreshes the wallet view.
     */
    fun deleteCard(card: LoyaltyCard) {
        viewModelScope.launch {
            loyaltyCardRepository.delete(card)
            loadCards()
        }
    }

    /**
     * Updates an existing loyalty card's details and its associated store's name/color.
     */
    fun updateCard(card: LoyaltyCard, storeName: String, color: Long) {
        viewModelScope.launch {
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
