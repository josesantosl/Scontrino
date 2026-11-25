package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import com.pepesantos.scontrino.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing loyalty cards and ensuring they are correctly linked to stores.
 */
class WalletViewModel(
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val storeRepository: StoreRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Package of the app opened from the top of the wallet to pay, empty = none. */
    val paymentApp: StateFlow<String> = settingsRepository.paymentApp

    /** Name chosen for the payment app card, empty = the app's own label. */
    val paymentAppName: StateFlow<String> = settingsRepository.paymentAppName

    /** Colour of the payment app card, null = default. */
    val paymentAppColor: StateFlow<Long?> = settingsRepository.paymentAppColor

    /** Place of the payment app card among the loyalty cards (see [WalletEntry.merge]). */
    val paymentAppPosition: StateFlow<Int> = settingsRepository.paymentAppPosition

    fun setPaymentAppCard(name: String, color: Long?) =
        settingsRepository.setPaymentAppCard(name.trim(), color)

    /** Forgets the payment app (removed from the wallet, or uninstalled): "None" in settings. */
    fun clearPaymentApp() = settingsRepository.setPaymentApp("")

    private val _cards = MutableStateFlow<List<LoyaltyCardWithStore>>(emptyList())
    val cards: StateFlow<List<LoyaltyCardWithStore>> = _cards

    private val _stores = MutableStateFlow<List<Store>>(emptyList())
    val stores: StateFlow<List<Store>> = _stores

    init {
        loadCards()
        loadStores()
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
     * Fetches all available stores for autocomplete.
     */
    fun loadStores() {
        viewModelScope.launch {
            _stores.value = storeRepository.getAll()
        }
    }

    /**
     * Saves a new loyalty card. 
     * Automatically links it to an existing store or creates a new one with the chosen color.
     */
    fun saveCard(name: String, number: String, color: Long) {
        val storeName = name.trim()
        val cardNumber = number.trim()
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

            // 3. Persist the loyalty card, at the end of the wallet (after the payment app too)
            val afterPaymentApp = if (paymentApp.value.isEmpty()) 0 else paymentAppPosition.value + 1
            loyaltyCardRepository.insert(
                LoyaltyCard(
                    storeId = store.id,
                    cardNumber = cardNumber,
                    position = maxOf(loyaltyCardRepository.nextPosition(), afterPaymentApp)
                )
            )
            loadCards()
            loadStores()
        }
    }

    /**
     * Deletes a loyalty card and refreshes the wallet view.
     */
    fun deleteCard(card: LoyaltyCard) {
        viewModelScope.launch {
            loyaltyCardRepository.delete(card)
            loadCards()
            loadStores()
        }
    }

    /**
     * Edits a card. The name and colour belong to the store: a name of another existing store
     * moves the card there (that store takes [color], so its other cards change colour too);
     * any other name renames the card's own store, on every receipt as well (typo fixes).
     */
    fun updateCard(card: LoyaltyCard, name: String, number: String, color: Long) {
        val storeName = name.trim()
        viewModelScope.launch {
            val other = storeRepository.search(storeName)
                .firstOrNull { it.name.equals(storeName, ignoreCase = true) && it.id != card.storeId }
            val storeId = if (other != null) {
                if (other.color != color) storeRepository.update(other.copy(color = color))
                other.id
            } else {
                storeRepository.getById(card.storeId)?.let { store ->
                    if (store.name != storeName || store.color != color) {
                        storeRepository.update(store.copy(name = storeName, color = color))
                    }
                }
                card.storeId
            }
            loyaltyCardRepository.update(card.copy(storeId = storeId, cardNumber = number.trim()))
            loadCards()
            loadStores()
        }
    }

    /**
     * Saves the order left by a drag and drop: each entry's index becomes its position. The
     * list is updated at once, so a second drag never sees the old order.
     */
    fun saveOrder(entries: List<WalletEntry>) {
        val positions = entries.withIndex()
            .mapNotNull { (index, entry) -> (entry as? WalletEntry.Card)?.let { it.card.card.id to index } }
            .toMap()
        _cards.value = _cards.value
            .map { it.copy(card = it.card.copy(position = positions[it.card.id] ?: it.card.position)) }
            .sortedBy { it.card.position }
        entries.indexOf(WalletEntry.PaymentApp).takeIf { it >= 0 }?.let(settingsRepository::setPaymentAppPosition)
        viewModelScope.launch { loyaltyCardRepository.setPositions(positions) }
    }
}
