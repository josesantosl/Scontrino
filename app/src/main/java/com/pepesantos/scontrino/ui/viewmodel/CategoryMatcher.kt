package com.pepesantos.scontrino.ui.viewmodel

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
        val lowerName = name.lowercase()
        for ((keywords, label) in keywordToLabel) {
            if (keywords.any { lowerName.contains(it) }) {
                return label
            }
        }
        return null
    }
}
