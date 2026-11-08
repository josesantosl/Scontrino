package com.pepesantos.scontrino.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher

/**
 * Database v10: the default categories were reorganized by what a product is
 * (animal protein split into meat, seafood and eggs; bread split from pasta and rice;
 * "canned & sauces" dissolved; new ready meals, alcohol, baby and eating out).
 */
internal object DefaultCategoriesV10 {

    /** Old default labels kept under a new name (same row, same id). */
    val renamed = mapOf(
        "animal_protein" to "meat",
        "plant_protein" to "legumes",
        "dairy_alternatives" to "dairy",
        "grains_pasta_bread" to "pasta_rice",
        "oils_condiments" to "condiments",
        "cleaning" to "household",
        "pharmacy" to "health",
    )

    /** Old default category that no longer exists; its products move by name or to condiments. */
    const val REMOVED = "canned_sauces"
    private const val REMOVED_FALLBACK = "condiments"

    /**
     * Categories a product may leave its (already renamed) category for, when the matcher
     * places its name there. Limited to the splits so the user's manual choices survive.
     */
    private val splits = mapOf(
        "meat" to setOf("seafood", "eggs", "pets", "ready_meals"),
        "pasta_rice" to setOf("bakery"),
        "dairy" to setOf("eggs"),
        "beverages" to setOf("alcohol"),
        "household" to setOf("personal_hygiene"),
        "personal_hygiene" to setOf("household"),
    )

    /**
     * New label for a product (or alias) named [name] currently in [label],
     * or null if it stays where it is.
     */
    fun newLabel(label: String, name: String): String? {
        val detected = CategoryMatcher.detectCategoryLabel(name)
        return when (label) {
            "other" -> detected?.takeIf { it != "other" }
            REMOVED -> detected ?: REMOVED_FALLBACK
            else -> detected?.takeIf { it in splits[label].orEmpty() }
        }
    }

    fun migrate(db: SupportSQLiteDatabase) {
        renamed.forEach { (old, new) ->
            db.execSQL("UPDATE Category SET label = ? WHERE label = ? AND isCustom = 0", arrayOf<Any>(new, old))
        }

        // New defaults get the next free ids (custom categories may already use the low ones).
        CategoryIcons.defaults.forEach { (label, style) ->
            db.execSQL(
                "INSERT INTO Category (label, iconName, color, isCustom) " +
                    "SELECT ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM Category WHERE label = ? AND isCustom = 0)",
                arrayOf<Any>(label, style.iconName, style.color, label),
            )
        }

        val idByLabel = mutableMapOf<String, Int>()
        db.query("SELECT id, label FROM Category WHERE isCustom = 0").use { c ->
            while (c.moveToNext()) idByLabel[c.getString(1)] = c.getInt(0)
        }
        val labelById = idByLabel.entries.associate { (label, id) -> id to label }

        // select returns (key, name, categoryId); update takes (categoryId, key).
        fun reassign(select: String, update: String) {
            val moves = mutableListOf<Pair<String, Int>>()
            db.query(select).use { c ->
                while (c.moveToNext()) {
                    val label = labelById[c.getInt(2)] ?: continue
                    val target = newLabel(label, c.getString(1))?.let(idByLabel::get) ?: continue
                    moves += c.getString(0) to target
                }
            }
            moves.forEach { (key, target) -> db.execSQL(update, arrayOf<Any>(target, key)) }
        }

        // Aliases own their category, and their products mirror it.
        reassign(
            "SELECT name, name, categoryId FROM ProductAlias",
            "UPDATE ProductAlias SET categoryId = ? WHERE name = ?",
        )
        reassign(
            "SELECT id, name, categoryId FROM Product WHERE alias IS NULL",
            "UPDATE Product SET categoryId = ? WHERE id = ?",
        )
        db.execSQL(
            "UPDATE Product SET categoryId = (SELECT categoryId FROM ProductAlias WHERE name = Product.alias) " +
                "WHERE alias IS NOT NULL AND EXISTS (SELECT 1 FROM ProductAlias WHERE name = Product.alias)"
        )

        idByLabel[REMOVED]?.let { removedId ->
            val fallback = idByLabel.getValue(REMOVED_FALLBACK)
            db.execSQL("UPDATE ProductAlias SET categoryId = ? WHERE categoryId = ?", arrayOf<Any>(fallback, removedId))
            db.execSQL("UPDATE Product SET categoryId = ? WHERE categoryId = ?", arrayOf<Any>(fallback, removedId))
            db.execSQL("DELETE FROM Category WHERE id = ?", arrayOf<Any>(removedId))
        }
    }
}
