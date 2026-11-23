package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher
import com.pepesantos.scontrino.ui.viewmodel.PurchaseHistory
import java.util.Calendar
import java.util.TimeZone

/**
 * A receipt read from a photo, ready to prefill the new receipt screen. The user
 * reviews and corrects it there before anything is saved.
 */
data class ReceiptDraft(
    /** Name of the recognised store, or blank if none matched. */
    val storeName: String,
    /** VAT number read on the receipt, remembered on the store when saved. */
    val storeVatNumber: String?,
    /** Purchase date as UTC midnight, the convention of the date picker. */
    val dateMillis: Long?,
    val items: List<ItemEntry>,
    /** Total printed on the receipt, to compare with the items while reviewing. */
    val receiptTotal: Double?,
    /** Whether the items (plus [tax]) add up to [receiptTotal] as read. */
    val addsUp: Boolean,
    /** Tax added on top of the items (US sales tax), 0 if prices include it. */
    val tax: Double = 0.0,
)

object ReceiptDraftBuilder {

    private const val OTHER_CATEGORY_ID = 1

    fun build(
        parsed: ParsedReceipt,
        stores: List<Store>,
        products: List<Product>,
        categories: List<Category>,
    ): ReceiptDraft {
        val store = StoreMatcher.match(stores, parsed.nonItemLines, parsed.vatNumber)?.store
        return ReceiptDraft(
            storeName = store?.name.orEmpty(),
            storeVatNumber = parsed.vatNumber,
            dateMillis = parsed.date?.let { utcMidnight(it.year, it.month, it.day) },
            items = items(parsed, products, categories),
            receiptTotal = parsed.totalCents?.let { it / 100.0 },
            addsUp = parsed.isConsistent,
            tax = (parsed.taxCents ?: 0L) / 100.0,
        )
    }

    /**
     * One entry per product, with the price after discounts. Identical lines (the
     * same product bought twice) become one entry with quantity 2. A name that is the
     * user's product, or one or two misread letters away from it, takes that product's
     * name and category.
     */
    private fun items(parsed: ParsedReceipt, products: List<Product>, categories: List<Category>): List<ItemEntry> {
        val matcher = ProductNameMatcher.Index(products)
        val categoryIdByLabel = categories.associate { it.label to it.id }

        // Keyed by the product's name when matched, so two readings of the same
        // product (one misread) still group together.
        val existingByName = mutableMapOf<String, Product>()
        // Items sold by weight only group when they weigh the same.
        val grouped = linkedMapOf<Triple<String, Long, Double?>, Int>()
        for (item in parsed.items) {
            val existing = matcher.match(item.name)
            val name = existing?.name ?: item.name
            if (existing != null) existingByName[name] = existing
            // ItemEntry holds the unit price; a line "2 x 0,79 = 1,58" becomes 2 × 0,79,
            // unless a discount makes the units differ in price.
            val perUnit = item.quantity > 1 && item.netCents % item.quantity == 0L
            val unitCents = if (perUnit) item.netCents / item.quantity else item.netCents
            val units = if (perUnit) item.quantity else 1
            val key = Triple(name, unitCents, item.weightGrams)
            grouped[key] = (grouped[key] ?: 0) + units
        }
        return grouped.map { (key, quantity) ->
            val (name, netCents, weightGrams) = key
            val existing = existingByName[name]
            ItemEntry(
                name = name,
                price = netCents / 100.0,
                quantity = quantity,
                weight = weightGrams,
                categoryId = existing?.categoryId
                    ?: CategoryMatcher.detectCategoryLabel(name)?.let { categoryIdByLabel[it] }
                    ?: OTHER_CATEGORY_ID,
            )
        }
    }

    /**
     * Fills the weight of the items that were bought before in the draft's store with the
     * same name and price (most receipts don't print weights). [history] is that store's.
     */
    fun withKnownWeights(draft: ReceiptDraft, history: PurchaseHistory): ReceiptDraft =
        draft.copy(
            items = draft.items.map { item ->
                if (item.weight != null) item
                else item.copy(weight = history.weightAtPrice(item.name, item.price))
            }
        )


    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis
}
