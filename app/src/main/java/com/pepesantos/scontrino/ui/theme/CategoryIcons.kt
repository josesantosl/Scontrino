package com.pepesantos.scontrino.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher

data class DefaultCategoryStyle(val iconName: String, val color: Long)

object CategoryIcons {

    /**
     * Icon and color for each default category, keyed by its stable [Category.label].
     * Also used to seed the database (insertion order defines the category ids).
     *
     * Default categories resolve their icon/color from here instead of the values stored in the
     * database, so restyling one only requires editing this map: existing installs pick it up
     * without a Room migration, and stale/unknown names stored by older versions no longer
     * show the fallback icon.
     */
    val defaults: Map<String, DefaultCategoryStyle> = linkedMapOf(
        "other" to DefaultCategoryStyle("MoreHoriz", 0xFF94A3B8),
        "vegetables_fruit" to DefaultCategoryStyle("Apple", 0xFFFF6F00),
        "plant_protein" to DefaultCategoryStyle("Eco", 0xFF8BC34A),
        "animal_protein" to DefaultCategoryStyle("SetMeal", 0xFFFF5722),
        "dairy_alternatives" to DefaultCategoryStyle("LocalDrink", 0xFF03A9F4),
        "grains_pasta_bread" to DefaultCategoryStyle("Spa", 0xFFFFB300),
        "snacks_sweets" to DefaultCategoryStyle("Cookie", 0xFFE91E63),
        "beverages" to DefaultCategoryStyle("Bottle", 0xFF795548),
        "canned_sauces" to DefaultCategoryStyle("SoupKitchen", 0xFFFF9800),
        "oils_condiments" to DefaultCategoryStyle("Opacity", 0xFFCDDC39),
        "cleaning" to DefaultCategoryStyle("CleaningServices", 0xFF00BCD4),
        "personal_hygiene" to DefaultCategoryStyle("Soap", 0xFF9C27B0),
        "pets" to DefaultCategoryStyle("Pets", 0xFF607D8B),
        "pharmacy" to DefaultCategoryStyle("LocalPharmacy", 0xFFF44336),
        "transport" to DefaultCategoryStyle("Train", 0xFF2196F3),
    )

    /**
     * Resolves the icon for a category: default categories use [defaults] by label,
     * custom categories use their stored [iconName].
     */
    @Composable
    fun forCategory(label: String, iconName: String?): ImageVector =
        getIcon(defaults[label]?.iconName ?: iconName ?: "Help")

    /**
     * Resolves the color for a category: default categories use [defaults] by label,
     * custom categories use their stored [color].
     */
    fun colorFor(label: String, color: Long): Color = Color(defaults[label]?.color ?: color)

    @Composable
    fun getIcon(name: String): ImageVector {
        return when (name) {
            // Custom drawables (Material Symbols)
            "Apple" -> ImageVector.vectorResource(R.drawable.ic_apple)
            "Bottle" -> ImageVector.vectorResource(R.drawable.ic_bottle)

            "MoreHoriz" -> Icons.Default.MoreHoriz
            "Eco" -> Icons.Default.Eco
            "SetMeal" -> Icons.Default.SetMeal
            "Spa" -> Icons.Default.Spa
            "LocalDrink" -> Icons.Default.LocalDrink
            "Cookie" -> Icons.Default.Cookie
            "SoupKitchen" -> Icons.Default.SoupKitchen
            "Opacity" -> Icons.Default.Opacity
            "CleaningServices" -> Icons.Default.CleaningServices
            "Soap" -> Icons.Default.Soap
            "Pets" -> Icons.Default.Pets
            "LocalPharmacy" -> Icons.Default.LocalPharmacy
            "Train" -> Icons.Default.Train

            // Legacy names still stored in existing databases
            "Grass" -> Icons.Default.Grass
            "RiceBowl" -> Icons.Default.RiceBowl
            "LocalCafe" -> Icons.Default.LocalCafe
            "Inventory2" -> Icons.Default.Inventory2

            else -> Icons.Default.Help
        }
    }
}

/**
 * Icon for this category (see [CategoryIcons.forCategory]).
 */
@Composable
fun Category.icon(): ImageVector = CategoryIcons.forCategory(label, iconName)

/**
 * Color for this category (see [CategoryIcons.colorFor]).
 */
fun Category.displayColor(): Color = CategoryIcons.colorFor(label, color)

/**
 * Returns the localized display name for a category label,
 * or the label itself if no string resource is found (e.g., custom categories).
 */
@Composable
fun getCategoryDisplayName(label: String): String {
    val resId = CategoryMatcher.getCategoryStringRes(label)
    return resId?.let { stringResource(it) } ?: label
}

/**
 * Extension on Category to get its localized display name.
 */
@Composable
fun Category.displayName(): String {
    val resId = getStringRes()
    return resId?.let { stringResource(it) } ?: label
}
