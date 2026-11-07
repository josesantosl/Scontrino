package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.PastPurchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseHistoryTest {

    private val history = PurchaseHistory(
        listOf(
            PastPurchase("Latte intero", price = 1.35, weight = null, date = 300L),
            PastPurchase("Latte intero", price = 1.29, weight = 1.0, date = 200L),
            PastPurchase("Latte intero", price = 1.19, weight = 0.5, date = 100L),
            PastPurchase("Pane", price = 2.10, weight = 500.0, date = 250L),
        )
    )

    @Test
    fun `last purchase ignores case and spaces`() {
        val last = history.lastPurchase("  LATTE intero ", date = null)
        assertEquals(LastPurchase(price = 1.35, date = 300L, weight = 1.0), last)
    }

    @Test
    fun `last purchase without weight takes the latest known weight`() {
        assertEquals(1.0, history.lastPurchase("Latte intero", date = null)?.weight)
    }

    @Test
    fun `last purchase only looks at receipts up to the date`() {
        assertEquals(LastPurchase(1.29, 200L, 1.0), history.lastPurchase("Latte intero", date = 200L))
        assertEquals(LastPurchase(1.19, 100L, 0.5), history.lastPurchase("Latte intero", date = 150L))
        assertNull(history.lastPurchase("Latte intero", date = 50L))
    }

    @Test
    fun `unknown or blank names have no history`() {
        assertNull(history.lastPurchase("Burro", date = null))
        assertNull(history.lastPurchase("", date = null))
    }

    @Test
    fun `empty price and weight are prefilled`() {
        val last = LastPurchase(price = 2.10, date = 250L, weight = 500.0)
        val (entry, auto) = ItemEntry(name = "Pane").prefilledWith(last, AutoFilled())
        assertEquals(2.10, entry.price, 0.0)
        assertEquals(500.0, entry.weight)
        assertEquals(AutoFilled(price = true, weight = true), auto)
    }

    @Test
    fun `typed values are never overwritten`() {
        val last = LastPurchase(price = 2.10, date = 250L, weight = 500.0)
        val typed = ItemEntry(name = "Pane", price = 1.99, weight = 400.0)
        val (entry, auto) = typed.prefilledWith(last, AutoFilled())
        assertEquals(typed, entry)
        assertEquals(AutoFilled(), auto)
    }

    @Test
    fun `prefilled values follow the name and are cleared when it has no history`() {
        val pane = LastPurchase(price = 2.10, date = 250L, weight = 500.0)
        val (first, auto) = ItemEntry(name = "Pane").prefilledWith(pane, AutoFilled())

        val latte = LastPurchase(price = 1.35, date = 300L, weight = null)
        val (second, secondAuto) = first.copy(name = "Latte").prefilledWith(latte, auto)
        assertEquals(1.35, second.price, 0.0)
        assertNull(second.weight)
        assertEquals(AutoFilled(price = true, weight = false), secondAuto)

        val (third, thirdAuto) = second.copy(name = "Latte di soia").prefilledWith(null, secondAuto)
        assertEquals(0.0, third.price, 0.0)
        assertNull(third.weight)
        assertEquals(AutoFilled(), thirdAuto)
    }

    @Test
    fun `only the value edited by the user stays`() {
        val last = LastPurchase(price = 2.10, date = 250L, weight = 500.0)
        val edited = ItemEntry(name = "Pane", price = 2.30, weight = 500.0)
        val (entry, auto) = edited.prefilledWith(null, AutoFilled(price = false, weight = true))
        assertEquals(2.30, entry.price, 0.0)
        assertNull(entry.weight)
        assertEquals(AutoFilled(), auto)
        // And with history, the price the user typed is kept while the weight is refreshed.
        val (again, _) = edited.prefilledWith(last.copy(weight = 750.0), AutoFilled(weight = true))
        assertEquals(ItemEntry(name = "Pane", price = 2.30, weight = 750.0), again)
    }
}
