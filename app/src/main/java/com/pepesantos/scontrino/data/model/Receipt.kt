package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity

@Entity
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val total: Double,
    val storeId: Int,
    val note: String? = null,
)
