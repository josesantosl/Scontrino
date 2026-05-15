package com.pepesantos.scontrino.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Embedded

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Store::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LoyaltyCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storeId: Int,
    val cardNumber: String,
)

data class LoyaltyCardWithStore(
    @Embedded val card: LoyaltyCard,
    val storeName: String,
    val storeColor: Long?
)
