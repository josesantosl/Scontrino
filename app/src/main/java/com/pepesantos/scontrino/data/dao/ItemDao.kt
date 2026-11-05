package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.TopCategorySpending
import com.pepesantos.scontrino.data.model.TopProductSpending

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM Item WHERE receiptId = :receiptId")
    suspend fun getByReceipt(receiptId: Int): List<Item>

    @Query("""
    SELECT Product.name, Item.price, Item.quantity, Product.categoryId, Item.weight
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    WHERE Item.receiptId = :receiptId
""")
    suspend fun getItemEntriesByReceipt(receiptId: Int): List<ItemEntry>
    @Query("SELECT * FROM Item WHERE productId = :productId")
    suspend fun getByProduct(productId: Int): List<Item>

    @Query("DELETE FROM Item WHERE receiptId = :receiptId")
    suspend fun deleteByReceipt(receiptId: Int)

    @Query("""
    SELECT Product.name AS productName, SUM(Item.price * Item.quantity) AS totalSpent
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    WHERE Receipt.date BETWEEN :from AND :to
    GROUP BY Product.id
    ORDER BY totalSpent DESC
    LIMIT 5
""")
    suspend fun getTopProductsSpendingInRange(from: Long, to: Long): List<TopProductSpending>

    @Query("""
    SELECT Category.id AS categoryId, Category.label AS categoryLabel, Category.iconName AS categoryIconName, Category.color AS categoryColor, SUM(Item.price * Item.quantity) AS totalSpent
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    INNER JOIN Category ON Product.categoryId = Category.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    WHERE Receipt.date BETWEEN :from AND :to
    GROUP BY Category.id
    ORDER BY totalSpent DESC
    LIMIT 5
""")
    suspend fun getTopCategoriesSpendingInRange(from: Long, to: Long): List<TopCategorySpending>
}