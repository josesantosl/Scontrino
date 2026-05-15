package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository

class ViewModelFactory(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val itemRepository: ItemRepository,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ReceiptViewModel::class.java) ->
                ReceiptViewModel(receiptRepository, storeRepository, productRepository, itemRepository) as T
            modelClass.isAssignableFrom(WalletViewModel::class.java) ->
                WalletViewModel(loyaltyCardRepository) as T
            modelClass.isAssignableFrom(StatsViewModel::class.java) ->
                StatsViewModel(receiptRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}