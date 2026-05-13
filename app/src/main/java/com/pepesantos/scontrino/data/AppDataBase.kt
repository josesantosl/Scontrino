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
                    database.categoryDao().insert(
                        Category(id = 1, label = "other", iconName = "MoreHoriz", color = 0xFF94A3B8, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 2, label = "groceries", iconName = "ShoppingCart", color = 0xFF4CAF50, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 3, label = "transport", iconName = "Train", color = 0xFF2196F3, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 4, label = "health", iconName = "LocalPharmacy", color = 0xFFE91E63, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 5, label = "home", iconName = "Home", color = 0xFF9C27B0, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 6, label = "technology", iconName = "Devices", color = 0xFF00BCD4, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 7, label = "clothing", iconName = "Checkroom", color = 0xFFFF9800, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 8, label = "leisure", iconName = "SportsEsports", color = 0xFFFFEB3B, isCustom = false)
                    )
                    database.categoryDao().insert(
                        Category(id = 9, label = "services", iconName = "Bolt", color = 0xFFFF5722, isCustom = false)
                    )
                }
            }
        }
    }
}