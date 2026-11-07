package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.PastPurchase

/** The previous purchase of a product in a store. */
data class LastPurchase(
    val price: Double,
    val date: Long,
    /** Weight of that purchase or, if it had none, the most recent one known before it. */
    val weight: Double?,
)

/**
 * What was bought in one store, looked up by product name (case and surrounding spaces
 * ignored, the same way products are matched when a receipt is saved).
 */
class PurchaseHistory(purchases: List<PastPurchase>) {

    // Newest first: the DAO already sorts them, sorting again keeps that order on ties.
    private val byName: Map<String, List<PastPurchase>> = purchases
        .sortedByDescending { it.date }
        .groupBy { key(it.productName) }

    /**
     * The latest purchase of [name] on or before [date] (receipt dates are whole days,
     * so another receipt from the same day counts as previous). Null [date]: no limit.
     */
    fun lastPurchase(name: String, date: Long?): LastPurchase? {
        if (name.isBlank()) return null
        val purchases = byName[key(name)] ?: return null
        val candidates = if (date == null) purchases else purchases.filter { it.date <= date }
        val last = candidates.firstOrNull() ?: return null
        val weight = candidates.firstNotNullOfOrNull { purchase -> purchase.weight?.takeIf { it > 0.0 } }
        return LastPurchase(price = last.price, date = last.date, weight = weight)
    }

    companion object {
        val EMPTY = PurchaseHistory(emptyList())

        private fun key(name: String) = name.trim().lowercase()
    }
}

/** Which values of an item row were filled in from history, so they may be replaced again. */
data class AutoFilled(val price: Boolean = false, val weight: Boolean = false)

/**
 * Fills the price and weight of a manually entered item with its [last] purchase.
 * Only empty values, or values filled in before by this same function, are touched: what
 * the user typed is never overwritten. If the new name has no history, previously filled
 * values are cleared, since they belonged to another product.
 */
fun ItemEntry.prefilledWith(last: LastPurchase?, autoFilled: AutoFilled): Pair<ItemEntry, AutoFilled> {
    var price = price
    var priceAuto = false
    if (price == 0.0 || autoFilled.price) {
        if (last != null) {
            price = last.price
            priceAuto = true
        } else if (autoFilled.price) {
            price = 0.0
        }
    }

    var weight = weight
    var weightAuto = false
    if (weight == null || autoFilled.weight) {
        if (last?.weight != null) {
            weight = last.weight
            weightAuto = true
        } else if (autoFilled.weight) {
            weight = null
        }
    }

    return copy(price = price, weight = weight) to AutoFilled(price = priceAuto, weight = weightAuto)
}
