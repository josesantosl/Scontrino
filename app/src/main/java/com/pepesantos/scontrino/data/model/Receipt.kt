package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Embedded

@Entity(
    foreignKeys = [
        ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val total: Double,
    val storeId: Int,
    val note: String? = null,
)
data class ReceiptWithStoreName(
    @Embedded val receipt: Receipt,
    val storeName: String,
    val storeColor: Long? = null
)