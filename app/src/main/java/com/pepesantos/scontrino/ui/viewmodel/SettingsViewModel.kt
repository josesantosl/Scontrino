package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ProductAlias
import com.pepesantos.scontrino.data.model.ProductUsage
import com.pepesantos.scontrino.data.repository.CategoryRepository
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
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val language: StateFlow<String> = settingsRepository.language
    val currency: StateFlow<String> = settingsRepository.currency

    private val _aliasRows = MutableStateFlow<List<ProductUsage>>(emptyList())
    /** Products bought so far, in the order of the aliases screen: see [sortForAliases]. */
    val aliasRows: StateFlow<List<ProductUsage>> = _aliasRows.asStateFlow()

    private val _aliases = MutableStateFlow<List<ProductAlias>>(emptyList())
    /** Aliases with their category. */
    val aliases: StateFlow<List<ProductAlias>> = _aliases.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

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
            _categories.value = categoryRepository.getAll()
            reloadAliases()
        }
    }

    fun setAlias(product: Product, alias: String?) {
        viewModelScope.launch {
            productRepository.setAlias(product.id, alias)
            reloadAliases()
        }
    }

    /** Category of the alias, and so of every product grouped under it. */
    fun setAliasCategory(alias: String, categoryId: Int) {
        viewModelScope.launch {
            productRepository.setAliasCategory(alias, categoryId)
            reloadAliases()
        }
    }

    private suspend fun reloadAliases() {
        _aliasRows.value = sortForAliases(productRepository.getUsage())
        _aliases.value = productRepository.getAliases()
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
