package com.pepesantos.scontrino.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pepesantos.scontrino.data.model.ProductAlias

@Dao
interface AliasDao {

    @Query("SELECT * FROM ProductAlias ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ProductAlias>

    @Query("SELECT * FROM ProductAlias WHERE name = :name")
    suspend fun get(name: String): ProductAlias?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: ProductAlias)

    @Query("UPDATE ProductAlias SET categoryId = :categoryId WHERE name = :name")
    suspend fun setCategory(name: String, categoryId: Int)

    /** Aliases no product uses any more. */
    @Query("DELETE FROM ProductAlias WHERE name NOT IN (SELECT alias FROM Product WHERE alias IS NOT NULL)")
    suspend fun deleteUnused()
}
