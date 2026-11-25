package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore

/**
 * One card of the wallet list: a loyalty card or the payment app shortcut. Both are reordered
 * together; their places share one numbering (LoyaltyCard.position and
 * SettingsRepository.paymentAppPosition).
 */
sealed interface WalletEntry {
    /** Stable key for the list and the drag and drop. */
    val key: String

    data class Card(val card: LoyaltyCardWithStore) : WalletEntry {
        override val key: String get() = "card-${card.card.id}"
    }

    /** The app is loaded by the screen (it needs a context), so only its place is known here. */
    data object PaymentApp : WalletEntry {
        override val key: String = "payment-app"
    }

    companion object {
        /**
         * The wallet in order: [cards] already sorted by position, and the payment app (if
         * [hasPaymentApp]) before the first card whose position is not lower than [paymentPosition].
         */
        fun merge(cards: List<LoyaltyCardWithStore>, hasPaymentApp: Boolean, paymentPosition: Int): List<WalletEntry> {
            val entries: MutableList<WalletEntry> = cards.mapTo(mutableListOf()) { Card(it) }
            if (hasPaymentApp) {
                val index = cards.indexOfFirst { it.card.position >= paymentPosition }
                entries.add(if (index == -1) entries.size else index, PaymentApp)
            }
            return entries
        }
    }
}
