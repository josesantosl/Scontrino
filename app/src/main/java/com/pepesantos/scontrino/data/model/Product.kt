package com.pepesantos.scontrino.data.model
import androidx.room.PrimaryKey
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    foreignKeys = [ ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"])]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Name as written on the receipt (or typed by the user). */
    val name: String,
    val categoryId: Int = 1,
    /**
     * Common name grouping several products that are the same thing (other brands,
     * other stores' spellings), e.g. "VALSOIA 1LT" and "BIBITA SOIA 1L" -> "latte di soia".
     * Statistics and price comparison group products by `alias ?: name`. Never points to
     * another alias: see [com.pepesantos.scontrino.data.alias.AliasRules].
     */
    val alias: String? = null,
)
