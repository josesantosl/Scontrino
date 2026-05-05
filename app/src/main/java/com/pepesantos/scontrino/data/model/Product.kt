package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity

@Entity
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val categoryId: Int? = null,
    val storeId: Int? = null,
)
