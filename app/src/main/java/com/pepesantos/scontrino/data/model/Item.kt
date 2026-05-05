package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity

@Entity
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val price: Double,
    val quantity: Int = 1,
    val receiptId: Int,
    val categoryId: Int
)