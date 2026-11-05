package com.pepesantos.scontrino.data.model

data class StoreSpending(
    val storeName: String,
    val storeColor: Long?,
    val totalSpent: Double
)

data class TopProductSpending(
    val productId: Int,
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

/** Product that has been bought at least once, with the number of distinct stores where it was bought. */
data class ComparableProduct(
    val productId: Int,
    val name: String,
    val categoryLabel: String,
    val storeCount: Int
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
