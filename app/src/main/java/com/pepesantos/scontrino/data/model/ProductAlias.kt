package com.pepesantos.scontrino.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * An alias: the real product behind several receipt names ([Product.alias] holds its
 * [name]). Its category is the category of the whole group: products with this alias
 * mirror it in [Product.categoryId], so every query keeps reading the product's category.
 */
@Entity(
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"])]
)
data class ProductAlias(
    @PrimaryKey val name: String,
    val categoryId: Int = 1,
)
