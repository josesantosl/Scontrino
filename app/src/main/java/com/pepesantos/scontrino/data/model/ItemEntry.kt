package com.pepesantos.scontrino.data.model

data class ItemEntry(
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val categoryId: Int = 0
)
