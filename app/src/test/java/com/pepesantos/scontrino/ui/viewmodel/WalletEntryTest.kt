package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletEntryTest {

    private fun card(id: Int, position: Int) =
        LoyaltyCardWithStore(LoyaltyCard(id = id, storeId = id, cardNumber = "$id", position = position), "S$id", null)

    private fun keys(entries: List<WalletEntry>) = entries.map { it.key }

    @Test
    fun `payment app at the default position goes first, even when every card is at 0`() {
        val entries = WalletEntry.merge(listOf(card(1, 0), card(2, 0)), hasPaymentApp = true, paymentPosition = 0)
        assertEquals(listOf("payment-app", "card-1", "card-2"), keys(entries))
    }

    @Test
    fun `payment app goes before the first card at or after its position`() {
        val entries = WalletEntry.merge(listOf(card(1, 0), card(2, 2)), hasPaymentApp = true, paymentPosition = 1)
        assertEquals(listOf("card-1", "payment-app", "card-2"), keys(entries))
    }

    @Test
    fun `payment app past the last card goes last`() {
        val entries = WalletEntry.merge(listOf(card(1, 0)), hasPaymentApp = true, paymentPosition = 9)
        assertEquals(listOf("card-1", "payment-app"), keys(entries))
    }

    @Test
    fun `no payment app, only cards`() {
        val entries = WalletEntry.merge(listOf(card(1, 0)), hasPaymentApp = false, paymentPosition = 0)
        assertEquals(listOf("card-1"), keys(entries))
    }
}
