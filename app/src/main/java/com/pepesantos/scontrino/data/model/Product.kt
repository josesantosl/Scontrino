package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    foreignKeys = [ ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"])]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val categoryId: Int = 1,
)
