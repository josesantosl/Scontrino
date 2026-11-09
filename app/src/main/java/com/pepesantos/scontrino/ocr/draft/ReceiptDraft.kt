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
    /** Whether the items add up to [receiptTotal] as read. */
    val addsUp: Boolean,
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
        )
    }

    /**
     * One entry per product, with the price after discounts. Identical lines (the
     * same product bought twice) become one entry with quantity 2.
     */
    private fun items(parsed: ParsedReceipt, products: List<Product>, categories: List<Category>): List<ItemEntry> {
        val productsByName = products.associateBy { ProductNames.normalize(it.name) }
        val categoryIdByLabel = categories.associate { it.label to it.id }

        val grouped = linkedMapOf<Pair<String, Long>, Int>()
        for (item in parsed.items) {
            val key = item.name to item.netCents
            grouped[key] = (grouped[key] ?: 0) + 1
        }
        return grouped.map { (key, quantity) ->
            val (name, netCents) = key
            // Reuse the user's own product (name and category) when it matches.
            val existing = productsByName[ProductNames.normalize(name)]
            ItemEntry(
                name = existing?.name ?: name,
                price = netCents / 100.0,
                quantity = quantity,
                categoryId = existing?.categoryId
                    ?: CategoryMatcher.detectCategoryLabel(name)?.let { categoryIdByLabel[it] }
                    ?: OTHER_CATEGORY_ID,
            )
        }
    }

    /**
     * Fills the weight of the items that were bought before in the draft's store with the
     * same name and price (receipts don't print weights). [history] is that store's.
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
