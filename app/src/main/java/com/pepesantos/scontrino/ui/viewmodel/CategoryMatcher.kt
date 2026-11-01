package com.pepesantos.scontrino.ui.viewmodel

import androidx.annotation.StringRes
import com.pepesantos.scontrino.R

/**
 * Utility object for automatic product categorization.
 * Matches keywords found in product names to category labels.
 */
object CategoryMatcher {
    /**
     * Map of keyword lists to category labels.
     * Supports multiple languages and brand names (e.g., 'felix' for pets).
     */
    private val keywordToLabel = mapOf(
        listOf("tofu", "seitan", "tempeh", "soja", "soya", "legumbres", "lentejas", "garbanzos") to "plant_protein",
        listOf("pollo", "chicken", "carne", "meat", "pork", "cerdo", "beef", "ternera", "vaca", "ham", "jamon", "pescado", "fish", "salmon", "atun", "tuna") to "animal_protein",
        listOf("manzana", "apple", "banana", "platano", "pera", "pear", "uva", "grape", "fruit", "fruta", "vegetable", "verdura", "lechuga", "lettuce", "tomate", "tomato") to "vegetables_fruit",
        listOf("leche", "milk", "yogur", "yogurt", "queso", "cheese", "dairy", "lacteo") to "dairy_alternatives",
        listOf("pan", "bread", "pasta", "arroz", "rice", "cereal", "avena", "oats", "harina", "flour") to "grains_pasta_bread",
        listOf("galletas", "cookies", "chocolate", "caramelo", "candy", "snack", "papas", "chips") to "snacks_sweets",
        listOf("agua", "water", "juice", "zumo", "soda", "refresco", "beer", "cerveza", "wine", "vino", "coffee", "cafe", "tea", "te") to "beverages",
        listOf("lata", "can", "sauce", "salsa", "conserva", "tomate frito") to "canned_sauces",
        listOf("aceite", "oil", "vinagre", "vinegar", "sal", "salt", "pimienta", "pepper", "especias", "spices") to "oils_condiments",
        listOf("cleaning", "limpieza", "detergente", "detergent", "soap", "jabon", "lavavajillas") to "cleaning",
        listOf("shampoo", "champu", "gel", "dental", "pasta de dientes", "hygiene", "higiene") to "personal_hygiene",
        listOf("perro", "dog", "gato", "cat", "mascota", "pet", "felix", "whiskas", "purina") to "pets",
        listOf("pharma", "farmacia", "medicina", "medicine", "pill", "pastilla", "aspirina") to "pharmacy",
        listOf("bus", "train", "tren", "metro", "ticket", "billete", "transport", "transporte") to "transport"
    )

    /**
     * Attempts to find a matching category label for a given product name.
     * @param name The name entered by the user.
     * @return The label of the matched category, or null if no keywords match.
     */
    fun detectCategoryLabel(name: String): String? {
        val normalizedName = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        for ((keywords, label) in keywordToLabel) {
            if (keywords.any { keyword ->
                val normalizedKeyword = java.text.Normalizer.normalize(keyword.lowercase(), java.text.Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                if (normalizedKeyword.length <= 2) {
                    Regex("""\b${Regex.escape(normalizedKeyword)}\b""").containsMatchIn(normalizedName)
                } else {
                    normalizedName.contains(normalizedKeyword)
                }
            }) {
                return label
            }
        }
        return null
    }

    /**
     * Returns the string resource ID for a category label, or null if custom / not found.
     */
    @StringRes
    fun getCategoryStringRes(label: String): Int? {
        return when (label) {
            "other" -> R.string.category_other
            "vegetables_fruit" -> R.string.category_vegetables_fruit
            "plant_protein" -> R.string.category_plant_protein
            "animal_protein" -> R.string.category_animal_protein
            "dairy_alternatives" -> R.string.category_dairy_alternatives
            "grains_pasta_bread" -> R.string.category_grains_pasta_bread
            "snacks_sweets" -> R.string.category_snacks_sweets
            "beverages" -> R.string.category_beverages
            "canned_sauces" -> R.string.category_canned_sauces
            "oils_condiments" -> R.string.category_oils_condiments
            "cleaning" -> R.string.category_cleaning
            "personal_hygiene" -> R.string.category_personal_hygiene
            "pets" -> R.string.category_pets
            "pharmacy" -> R.string.category_pharmacy
            "transport" -> R.string.category_transport
            else -> null
        }
    }
}
