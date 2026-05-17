package com.pepesantos.scontrino.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {
    fun getIcon(name: String): ImageVector {
        return when (name) {
            "MoreHoriz" -> Icons.Default.MoreHoriz
            "Grass" -> Icons.Default.Grass
            "Spa" -> Icons.Default.Spa
            "SetMeal" -> Icons.Default.SetMeal
            "LocalDrink" -> Icons.Default.LocalDrink
            "RiceBowl" -> Icons.Default.RiceBowl
            "Cookie" -> Icons.Default.Cookie
            "LocalCafe" -> Icons.Default.LocalCafe
            "Inventory2" -> Icons.Default.Inventory2
            "Opacity" -> Icons.Default.Opacity
            "CleaningServices" -> Icons.Default.CleaningServices
            "Soap" -> Icons.Default.Soap
            "Pets" -> Icons.Default.Pets
            "LocalPharmacy" -> Icons.Default.LocalPharmacy
            "Train" -> Icons.Default.Train
            else -> Icons.Default.Help
        }
    }
}
