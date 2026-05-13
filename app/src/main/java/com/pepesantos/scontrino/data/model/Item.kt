package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    foreignKeys = [
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Receipt::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val price: Double,
    val quantity: Int = 1,
    val receiptId: Int,
)