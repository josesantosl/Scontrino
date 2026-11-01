package com.pepesantos.scontrino.data.model

import androidx.annotation.StringRes
import androidx.room.PrimaryKey
import androidx.room.Entity
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val iconName: String,
    val color: Long,
    val isCustom: Boolean = false,
) {
    @StringRes
    fun getStringRes(): Int? = CategoryMatcher.getCategoryStringRes(label)
}
