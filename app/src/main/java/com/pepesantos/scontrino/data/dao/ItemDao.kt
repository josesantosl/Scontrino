package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.ComparableProduct
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.LatestStorePrice
import com.pepesantos.scontrino.data.model.PastPurchase
import com.pepesantos.scontrino.data.model.StoreWeight
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
    SELECT Product.name, Item.price, Item.quantity, Product.categoryId, Item.weight, Product.alias
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    WHERE Item.receiptId = :receiptId
""")
    suspend fun getItemEntriesByReceipt(receiptId: Int): List<ItemEntry>
    @Query("SELECT * FROM Item WHERE productId = :productId")
    suspend fun getByProduct(productId: Int): List<Item>

    @Query("DELETE FROM Item WHERE receiptId = :receiptId")
    suspend fun deleteByReceipt(receiptId: Int)

    /** Products with an alias are added up under it: see [com.pepesantos.scontrino.data.model.Product.alias]. */
    @Query("""
    SELECT COALESCE(Product.alias, Product.name) AS productName, SUM(Item.price * Item.quantity) AS totalSpent
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    WHERE Receipt.date BETWEEN :from AND :to
    GROUP BY COALESCE(Product.alias, Product.name)
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

    /**
     * Product groups (alias, or name if none) bought at least once, most comparable (more
     * stores) first. The category is the one of any product of the group.
     */
    @Query("""
    SELECT COALESCE(Product.alias, Product.name) AS name, Category.label AS categoryLabel,
           COUNT(DISTINCT Receipt.storeId) AS storeCount
    FROM Product
    INNER JOIN Category ON Product.categoryId = Category.id
    INNER JOIN Item ON Item.productId = Product.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    GROUP BY COALESCE(Product.alias, Product.name)
    ORDER BY storeCount DESC, name COLLATE NOCASE ASC
""")
    suspend fun getComparableProducts(): List<ComparableProduct>

    /**
     * Latest price per store for a product group (alias, or name if none): each store may
     * sell a different product of the group. Relies on SQLite's bare-column semantics:
     * with MAX() in the select, the other columns come from the row holding the max date.
     */
    @Query("""
    SELECT Store.id AS storeId, Store.name AS storeName, Store.color AS storeColor,
           Item.price AS price, Item.weight AS weight, MAX(Receipt.date) AS date
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    INNER JOIN Store ON Receipt.storeId = Store.id
    WHERE COALESCE(Product.alias, Product.name) = :groupName
    GROUP BY Store.id
""")
    suspend fun getLatestPricePerStore(groupName: String): List<LatestStorePrice>

    /**
     * Most recent known weight of a product group in each store, used to fill in missing weights.
     * Weights are never shared across stores: each store sells its own package size.
     */
    @Query("""
    SELECT Receipt.storeId AS storeId, Item.weight AS weight, MAX(Receipt.date) AS date
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    WHERE COALESCE(Product.alias, Product.name) = :groupName
      AND Item.weight IS NOT NULL AND Item.weight > 0
    GROUP BY Receipt.storeId
""")
    suspend fun getLastKnownWeightPerStore(groupName: String): List<StoreWeight>

    /**
     * Everything bought in the stores with this name (case-insensitive, duplicated stores
     * included), newest first, leaving out one receipt (the one being edited, or -1).
     */
    @Query("""
    SELECT Product.name AS productName, Item.price AS price, Item.weight AS weight,
           Receipt.date AS date
    FROM Item
    INNER JOIN Product ON Item.productId = Product.id
    INNER JOIN Receipt ON Item.receiptId = Receipt.id
    INNER JOIN Store ON Receipt.storeId = Store.id
    WHERE Store.name = :storeName COLLATE NOCASE AND Receipt.id != :excludeReceiptId
    ORDER BY Receipt.date DESC, Receipt.id DESC, Item.id DESC
""")
    suspend fun getPurchasesInStore(storeName: String, excludeReceiptId: Int): List<PastPurchase>
}