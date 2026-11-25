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
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(ReceiptViewModel::class.java) ->
                ReceiptViewModel(
                    receiptRepository, storeRepository, productRepository, itemRepository, categoryRepository,
                    currencyProvider = { settingsRepository.currency.value },
                )
            modelClass.isAssignableFrom(WalletViewModel::class.java) ->
                WalletViewModel(loyaltyCardRepository, storeRepository, settingsRepository)
            modelClass.isAssignableFrom(StatsViewModel::class.java) ->
                StatsViewModel(receiptRepository, itemRepository, settingsRepository)
            modelClass.isAssignableFrom(PriceComparisonViewModel::class.java) ->
                PriceComparisonViewModel(itemRepository)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(settingsRepository, productRepository, receiptRepository, categoryRepository)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return modelClass.cast(viewModel)!!
    }
}
