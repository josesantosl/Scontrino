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
        ProductAlias::class,
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun productDao(): ProductDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun storeDao(): StoreDao
    abstract fun loyaltyCardDao(): LoyaltyCardDao
    abstract fun aliasDao(): AliasDao

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
                db.execSQL("ALTER TABLE Product ADD COLUMN alias TEXT DEFAULT NULL")
                // Every receipt so far was in euros.
                db.execSQL("ALTER TABLE Receipt ADD COLUMN currency TEXT NOT NULL DEFAULT 'EUR'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ProductAlias` (`name` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`name`), FOREIGN KEY(`categoryId`) REFERENCES `Category`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )"
                )
                // Each alias takes the most used real category of its products (1 = other).
                db.execSQL(
                    """
                    INSERT INTO ProductAlias (name, categoryId)
                    SELECT p.alias, COALESCE(
                        (SELECT p2.categoryId FROM Product p2
                         WHERE p2.alias = p.alias AND p2.categoryId != 1
                         GROUP BY p2.categoryId ORDER BY COUNT(*) DESC LIMIT 1), 1)
                    FROM Product p WHERE p.alias IS NOT NULL GROUP BY p.alias
                    """.trimIndent()
                )
                // Products mirror the category of their alias.
                db.execSQL(
                    "UPDATE Product SET categoryId = (SELECT categoryId FROM ProductAlias WHERE name = Product.alias) " +
                        "WHERE alias IS NOT NULL"
                )
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
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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
