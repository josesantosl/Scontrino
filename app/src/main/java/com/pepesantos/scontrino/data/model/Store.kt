package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity

@Entity
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Long? = null,
    /**
     * VAT number printed on this store's receipts (Italian P.IVA). Learned when a
     * scanned receipt is saved, and used to recognise the store when its name is
     * not found in the receipt text.
     */
    val vatNumber: String? = null,
)
