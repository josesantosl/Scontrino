package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ProductUsage
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings: language, currency and product aliases.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val productRepository: ProductRepository,
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    val language: StateFlow<String> = settingsRepository.language
    val currency: StateFlow<String> = settingsRepository.currency

    private val _aliasRows = MutableStateFlow<List<ProductUsage>>(emptyList())
    /** Products bought so far, in the order of the aliases screen: see [sortForAliases]. */
    val aliasRows: StateFlow<List<ProductUsage>> = _aliasRows.asStateFlow()

    fun setLanguage(tag: String) = settingsRepository.setLanguage(tag)

    /** Whether changing the currency has to ask what to do with the existing receipts. */
    suspend fun hasReceipts(): Boolean = receiptRepository.count() > 0

    /**
     * Changes the default currency (new receipts and statistics) and, with
     * [applyToExistingReceipts], also the currency of every receipt already saved.
     */
    fun setCurrency(currency: String, applyToExistingReceipts: Boolean) {
        settingsRepository.setCurrency(currency)
        if (applyToExistingReceipts) {
            viewModelScope.launch { receiptRepository.setCurrencyForAll(currency) }
        }
    }

    fun loadAliases() {
        viewModelScope.launch {
            _aliasRows.value = sortForAliases(productRepository.getUsage())
        }
    }

    fun setAlias(product: Product, alias: String?) {
        viewModelScope.launch {
            productRepository.setAlias(product.id, alias)
            _aliasRows.value = sortForAliases(productRepository.getUsage())
        }
    }

    companion object {
        /**
         * Products still without alias first, most bought first; the ones that already have
         * an alias at the end, grouped by alias.
         */
        fun sortForAliases(rows: List<ProductUsage>): List<ProductUsage> =
            rows.sortedWith(
                compareBy<ProductUsage> { it.product.alias != null }
                    .thenBy { it.product.alias?.lowercase().orEmpty() }
                    .thenByDescending { it.purchaseCount }
                    .thenBy { it.product.name.lowercase() }
            )
    }
}
