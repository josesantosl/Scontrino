package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pepesantos.scontrino.data.repository.CategoryRepository
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import com.pepesantos.scontrino.data.settings.SettingsRepository

class ViewModelFactory(
    private val receiptRepository: ReceiptRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ReceiptViewModel::class.java) ->
                ReceiptViewModel(
                    receiptRepository, storeRepository, productRepository, itemRepository, categoryRepository,
                    currencyProvider = { settingsRepository.currency.value },
                ) as T
            modelClass.isAssignableFrom(WalletViewModel::class.java) ->
                WalletViewModel(loyaltyCardRepository, storeRepository) as T
            modelClass.isAssignableFrom(StatsViewModel::class.java) ->
                StatsViewModel(receiptRepository, itemRepository) as T
            modelClass.isAssignableFrom(PriceComparisonViewModel::class.java) ->
                PriceComparisonViewModel(itemRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(settingsRepository, productRepository, receiptRepository, categoryRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
