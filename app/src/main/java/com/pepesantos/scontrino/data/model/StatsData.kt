package com.pepesantos.scontrino.data.model

data class StoreSpending(
    val storeName: String,
    val storeColor: Long?,
    val totalSpent: Double
)

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
