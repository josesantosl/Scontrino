package com.pepesantos.scontrino.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pepesantos.scontrino.data.dao.*
import com.pepesantos.scontrino.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pepesantos.scontrino.R

@Database(
    entities = [
        Category::class,
        Item::class,
        Product::class,
        Receipt::class,
        Store::class,
        LoyaltyCard::class,
    ],
    version = 1,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scontrino_database"
                )
                    .addCallback(PrepopulateCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
    private class PrepopulateCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val categories = listOf(
                        Category(id = 1, label = "other", iconName = "MoreHoriz", color = 0xFF94A3B8, isCustom = false),
                        Category(id = 2, label = "vegetables_fruit", iconName = "Grass", color = 0xFF4CAF50, isCustom = false),
                        Category(id = 3, label = "plant_protein", iconName = "Spa", color = 0xFF8BC34A, isCustom = false),
                        Category(id = 4, label = "animal_protein", iconName = "SetMeal", color = 0xFFFF5722, isCustom = false),
                        Category(id = 5, label = "dairy_alternatives", iconName = "LocalDrink", color = 0xFF03A9F4, isCustom = false),
                        Category(id = 6, label = "grains_pasta_bread", iconName = "RiceBowl", color = 0xFFFFB300, isCustom = false),
                        Category(id = 7, label = "snacks_sweets", iconName = "Cookie", color = 0xFFE91E63, isCustom = false),
                        Category(id = 8, label = "beverages", iconName = "LocalCafe", color = 0xFF795548, isCustom = false),
                        Category(id = 9, label = "canned_sauces", iconName = "Inventory2", color = 0xFFFF9800, isCustom = false),
                        Category(id = 10, label = "oils_condiments", iconName = "Opacity", color = 0xFFCDDC39, isCustom = false),
                        Category(id = 11, label = "cleaning", iconName = "CleaningServices", color = 0xFF00BCD4, isCustom = false),
                        Category(id = 12, label = "personal_hygiene", iconName = "Soap", color = 0xFF9C27B0, isCustom = false),
                        Category(id = 13, label = "pets", iconName = "Pets", color = 0xFF607D8B, isCustom = false),
                        Category(id = 14, label = "pharmacy", iconName = "LocalPharmacy", color = 0xFFF44336, isCustom = false),
                        Category(id = 15, label = "transport", iconName = "Train", color = 0xFF2196F3, isCustom = false),
                    )
                    categories.forEach { database.categoryDao().insert(it) }
                }
            }
        }
    }
}