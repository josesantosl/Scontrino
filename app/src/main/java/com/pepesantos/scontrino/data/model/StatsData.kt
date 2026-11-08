package com.pepesantos.scontrino.data.model

import androidx.room.Embedded

data class StoreSpending(
    val storeName: String,
    val storeColor: Long?,
    val totalSpent: Double
)

/** Spending on a product group (its alias, or its own name if it has none). */
data class TopProductSpending(
    val productName: String,
    val totalSpent: Double
)

data class TopCategorySpending(
    val categoryId: Int,
    val categoryLabel: String,
    val categoryIconName: String,
    val categoryColor: Long,
    val totalSpent: Double
)

/**
 * Product group (alias, or product name if it has none) bought at least once, with the
 * number of distinct stores where it was bought.
 */
data class ComparableProduct(
    val name: String,
    val categoryLabel: String,
    val storeCount: Int
)

/** A product with the number of receipt lines it appears in. */
data class ProductUsage(
    @Embedded val product: Product,
    val purchaseCount: Int
)

/** Most recent non-empty weight of a product in a given store. */
data class StoreWeight(
    val storeId: Int,
    val weight: Double,
    val date: Long
)

/** Latest recorded price (and weight, if any) of a product in a given store. */
data class LatestStorePrice(
    val storeId: Int,
    val storeName: String,
    val storeColor: Long?,
    val price: Double,
    val weight: Double?,
    val date: Long
)
