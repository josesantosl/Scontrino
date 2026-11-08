package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings: language and currency.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    val language: StateFlow<String> = settingsRepository.language
    val currency: StateFlow<String> = settingsRepository.currency

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
}
