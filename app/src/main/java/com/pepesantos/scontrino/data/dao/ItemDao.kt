package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Item

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

    @Query("SELECT * FROM Item WHERE productId = :productId")
    suspend fun getByProduct(productId: Int): List<Item>

    @Query("DELETE FROM Item WHERE receiptId = :receiptId")
    suspend fun deleteByReceipt(receiptId: Int)
}