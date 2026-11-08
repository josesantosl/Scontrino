package com.pepesantos.scontrino.data.model

data class ItemEntry(
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val categoryId: Int = 1,
    val weight: Double? = null,
    /**
     * Alias of the product called [name], saved on the product with the receipt.
     * Null leaves the product's alias as it is; blank removes it.
     */
    val alias: String? = null,
)
