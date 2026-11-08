package com.pepesantos.scontrino.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pepesantos.scontrino.data.dao.*
import com.pepesantos.scontrino.data.model.*
import com.pepesantos.scontrino.ui.theme.CategoryIcons

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
    version = 8,
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Item ADD COLUMN weight REAL DEFAULT NULL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Store ADD COLUMN vatNumber TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Every receipt so far was in euros.
                db.execSQL("ALTER TABLE Receipt ADD COLUMN currency TEXT NOT NULL DEFAULT 'EUR'")
            }
        }

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
                    // Every version bump needs a migration here: the destructive
                    // fallback below would otherwise wipe the user's receipts.
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
            // Single source of truth for default categories; order defines the ids
            val categories = CategoryIcons.defaults.toList()

            db.beginTransaction()
            try {
                categories.forEachIndexed { index, (label, style) ->
                    val id = index + 1
                    val (icon, color) = style
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
