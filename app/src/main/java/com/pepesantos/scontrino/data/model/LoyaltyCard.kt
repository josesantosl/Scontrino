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
    /**
     * Place in the wallet chosen by the user (ascending; ties by store name). It shares its
     * numbering with the payment app card (SettingsRepository.paymentAppPosition).
     */
    val position: Int = 0,
)

data class LoyaltyCardWithStore(
    @Embedded val card: LoyaltyCard,
    val storeName: String,
    val storeColor: Long?
)
