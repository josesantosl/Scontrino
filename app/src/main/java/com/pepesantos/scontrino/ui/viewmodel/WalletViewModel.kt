package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WalletViewModel(
    private val loyaltyCardRepository: LoyaltyCardRepository
) : ViewModel() {

    private val _cards = MutableStateFlow<List<LoyaltyCard>>(emptyList())
    val cards: StateFlow<List<LoyaltyCard>> = _cards

    init {
        loadCards()
    }

    fun loadCards() {
        viewModelScope.launch {
            _cards.value = loyaltyCardRepository.getAll()
        }
    }

    fun saveCard(name: String, cardNumber: String, color: Long) {
        viewModelScope.launch {
            loyaltyCardRepository.insert(
                LoyaltyCard(name = name, cardNumber = cardNumber, color = color)
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

    fun updateCard(card: LoyaltyCard) {
        viewModelScope.launch {
            loyaltyCardRepository.update(card)
            loadCards()
        }
    }
}