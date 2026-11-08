package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Receipt
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.data.model.StoreSpending

@Dao
interface ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("SELECT * FROM Receipt ORDER BY date DESC")
    suspend fun getAll(): List<Receipt>

    @Query("SELECT COUNT(*) FROM Receipt")
    suspend fun count(): Int

    /** Changes the currency of every receipt (amounts are kept as they are). */
    @Query("UPDATE Receipt SET currency = :currency")
    suspend fun setCurrencyForAll(currency: String)

    @Query("""
    SELECT Receipt.*, Store.name AS storeName, Store.color AS storeColor
    FROM Receipt 
    INNER JOIN Store ON Receipt.storeId = Store.id 
    ORDER BY Receipt.date DESC
""")
    suspend fun getAllWithStoreName(): List<ReceiptWithStoreName>

    @Query("""
    SELECT Receipt.*, Store.name AS storeName, Store.color AS storeColor
    FROM Receipt 
    INNER JOIN Store ON Receipt.storeId = Store.id 
    WHERE Receipt.id = :id
""")
    suspend fun getByIdWithStoreName(id: Int): ReceiptWithStoreName?

    @Query("SELECT * FROM Receipt WHERE id = :id")
    suspend fun getById(id: Int): Receipt?

    @Query("SELECT * FROM Receipt WHERE storeId = :storeId ORDER BY date DESC")
    suspend fun getByStore(storeId: Int): List<Receipt>

    @Query("SELECT * FROM Receipt WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun getByDateRange(from: Long, to: Long): List<Receipt>

    @Query("SELECT * FROM Receipt WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getReceiptsInRangeAsc(from: Long, to: Long): List<Receipt>

    @Query("SELECT SUM(total) FROM Receipt WHERE date BETWEEN :from AND :to")
    suspend fun getTotalSpentInRange(from: Long, to: Long): Double?

    @Query("""
    SELECT Store.name AS storeName, Store.color AS storeColor, SUM(Receipt.total) AS totalSpent
    FROM Receipt 
    INNER JOIN Store ON Receipt.storeId = Store.id 
    WHERE Receipt.date BETWEEN :from AND :to
    GROUP BY Store.id
    ORDER BY totalSpent DESC
""")
    suspend fun getStoreSpendingInRange(from: Long, to: Long): List<StoreSpending>
}
