package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.ItemEntry

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
    SELECT Product.name, Item.price, Item.quantity, Product.categoryId
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    WHERE Item.receiptId = :receiptId
""")
    suspend fun getItemEntriesByReceipt(receiptId: Int): List<ItemEntry>
    @Query("SELECT * FROM Item WHERE productId = :productId")
    suspend fun getByProduct(productId: Int): List<Item>

    @Query("DELETE FROM Item WHERE receiptId = :receiptId")
    suspend fun deleteByReceipt(receiptId: Int)
}