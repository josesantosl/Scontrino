package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Embedded
import androidx.room.Index

@Entity(
    foreignKeys = [
        ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("storeId")],
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    /** Amount paid: the items plus [tax]. */
    val total: Double,
    val storeId: Int,
    val note: String? = null,
    /** ISO 4217 code (EUR, USD, CHF...) or a custom symbol chosen by the user. */
    val currency: String = "EUR",
    /**
     * Tax added on top of the items, where prices are shown without it (US sales
     * tax). 0 where prices already include VAT. Item prices never include it, so
     * product, category and price comparisons use shelf prices.
     */
    val tax: Double = 0.0,
)
data class ReceiptWithStoreName(
    @Embedded val receipt: Receipt,
    val storeName: String,
    val storeColor: Long? = null
)
