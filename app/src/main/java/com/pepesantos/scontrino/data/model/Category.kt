package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val iconName: String,
    val color: Long,
    val isCustom: Boolean = false,
)
