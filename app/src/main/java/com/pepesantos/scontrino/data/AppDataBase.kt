package com.pepesantos.scontrino.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pepesantos.scontrino.data.dao.*
import com.pepesantos.scontrino.data.model.*

/**
 * Main database class for the application.
 * Manages the persistence of categories, items, products, receipts, stores, and loyalty cards.
 */
@Database(
    entities = [
        Category::class,
        Item::class,
        Product::class,
        Receipt::class,
        Store::class,
        LoyaltyCard::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun productDao(): ProductDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun storeDao(): StoreDao
    abstract fun loyaltyCardDao(): LoyaltyCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of [AppDatabase].
         * Uses fallbackToDestructiveMigration for simplified schema updates during development.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scontrino_database"
                )
                    .addCallback(PrepopulateCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Internal callback to handle initial data prepopulation and schema recovery.
     */
    private class PrepopulateCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            prepopulate(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Safety check: ensure Category table exists and is not empty on every database open
            val cursorTable = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='Category'")
            val tableExists = cursorTable.moveToFirst()
            cursorTable.close()

            if (tableExists) {
                val cursorCount = db.query("SELECT COUNT(*) FROM Category")
                if (cursorCount.moveToFirst() && cursorCount.getInt(0) == 0) {
                    prepopulate(db)
                }
                cursorCount.close()
            }
        }

        /**
         * Inserts default product categories into the database using raw SQL for synchronous execution.
         */
        private fun prepopulate(db: SupportSQLiteDatabase) {
            val categories = listOf(
                "other" to Pair("MoreHoriz", 0xFF94A3B8),
                "vegetables_fruit" to Pair("Grass", 0xFF4CAF50),
                "plant_protein" to Pair("Spa", 0xFF8BC34A),
                "animal_protein" to Pair("SetMeal", 0xFFFF5722),
                "dairy_alternatives" to Pair("LocalDrink", 0xFF03A9F4),
                "grains_pasta_bread" to Pair("RiceBowl", 0xFFFFB300),
                "snacks_sweets" to Pair("Cookie", 0xFFE91E63),
                "beverages" to Pair("LocalCafe", 0xFF795548),
                "canned_sauces" to Pair("Inventory2", 0xFFFF9800),
                "oils_condiments" to Pair("Opacity", 0xFFCDDC39),
                "cleaning" to Pair("CleaningServices", 0xFF00BCD4),
                "personal_hygiene" to Pair("Soap", 0xFF9C27B0),
                "pets" to Pair("Pets", 0xFF607D8B),
                "pharmacy" to Pair("LocalPharmacy", 0xFFF44336),
                "transport" to Pair("Train", 0xFF2196F3)
            )

            db.beginTransaction()
            try {
                categories.forEachIndexed { index, (label, details) ->
                    val id = index + 1
                    val (icon, color) = details
                    db.execSQL(
                        "INSERT OR REPLACE INTO Category (id, label, iconName, color, isCustom) VALUES ($id, '$label', '$icon', $color, 0)"
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
