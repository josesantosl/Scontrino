package com.pepesantos.scontrino.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class LoyaltyCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val cardNumber: String,
    val color: Long,
)
