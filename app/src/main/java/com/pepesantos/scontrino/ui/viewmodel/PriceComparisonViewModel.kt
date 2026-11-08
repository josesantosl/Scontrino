package com.pepesantos.scontrino.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.model.ComparableProduct
import com.pepesantos.scontrino.data.repository.ItemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the weight used to normalize the price comes from. */
enum class WeightSource {
    /** The latest receipt line of this store has its own weight. */
    MEASURED,
    /**
     * Taken from an earlier receipt of the same product in the same store (however old), or,
     * only if this store never had a weight for it, from the most recent weight in another store.
     */
    ESTIMATED,
    /** No weight was ever recorded for this product in any store: a default reference weight is assumed. */
    DEFAULT
}

/** How the normalized price is expressed. Weights are stored in g, beverages in L. */
enum class MeasureUnit { KG, L }

data class StorePriceRow(
    val storeId: Int,
    val storeName: String,
    val storeColor: Long?,
    val price: Double,
    val pricePerUnit: Double,
    val source: WeightSource
)

data class PriceComparisonState(
    val products: List<ComparableProduct> = emptyList(),
    val selectedProduct: ComparableProduct? = null,
    val unit: MeasureUnit = MeasureUnit.KG,
    /** Sorted from cheapest to most expensive. */
    val rows: List<StorePriceRow> = emptyList(),
    val isLoading: Boolean = false
) {
    val cheapest: StorePriceRow? get() = rows.firstOrNull()
    val mostExpensive: StorePriceRow? get() = rows.lastOrNull()

    /** Fraction (0..1) saved buying at the cheapest store instead of the most expensive one. */
    val savingsFraction: Double?
        get() {
            val min = cheapest ?: return null
            val max = mostExpensive ?: return null
            if (rows.size < 2 || max.pricePerUnit <= 0.0) return null
            return (max.pricePerUnit - min.pricePerUnit) / max.pricePerUnit
        }
}

class PriceComparisonViewModel(
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PriceComparisonState())
    val state: StateFlow<PriceComparisonState> = _state.asStateFlow()

    private var comparisonJob: Job? = null

    /** Reloads the searchable products and, if a product is given, opens its comparison. */
    fun open(productId: Int? = null) {
        viewModelScope.launch {
            val products = itemRepository.getComparableProducts()
            _state.update { it.copy(products = products) }
            val target = productId?.let { id -> products.find { it.productId == id } }
            if (target != null) {
                selectProduct(target)
            } else if (productId != null) {
                clearSelection()
            }
        }
    }

    fun clearSelection() {
        comparisonJob?.cancel()
        _state.update { it.copy(selectedProduct = null, rows = emptyList(), isLoading = false) }
    }

    fun selectProduct(product: ComparableProduct) {
        val unit = if (product.categoryLabel == BEVERAGES_LABEL) MeasureUnit.L else MeasureUnit.KG
        _state.update { it.copy(selectedProduct = product, unit = unit, rows = emptyList(), isLoading = true) }

        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            val latest = itemRepository.getLatestPricePerStore(product.productId)
            val storeWeights = itemRepository.getLastKnownWeightPerStore(product.productId)
            val weightByStore = storeWeights.associate { it.storeId to it.weight }
            // Only used for stores that never recorded a weight for this product.
            val mostRecentWeightAnyStore = storeWeights.maxByOrNull { it.date }?.weight

            val rows = latest.map { entry ->
                val ownWeight = entry.weight?.takeIf { it > 0.0 }
                val lastKnownWeight = weightByStore[entry.storeId] ?: mostRecentWeightAnyStore
                val (weight, source) = when {
                    ownWeight != null -> ownWeight to WeightSource.MEASURED
                    lastKnownWeight != null -> lastKnownWeight to WeightSource.ESTIMATED
                    else -> defaultWeightFor(unit) to WeightSource.DEFAULT
                }
                StorePriceRow(
                    storeId = entry.storeId,
                    storeName = entry.storeName,
                    storeColor = entry.storeColor,
                    price = entry.price,
                    pricePerUnit = pricePerUnit(entry.price, weight, unit),
                    source = source
                )
            }.sortedBy { it.pricePerUnit }

            _state.update { it.copy(rows = rows, isLoading = false) }
        }
    }

    private fun pricePerUnit(price: Double, weight: Double, unit: MeasureUnit): Double =
        when (unit) {
            MeasureUnit.KG -> price / weight * 1000.0 // weight in g -> price per kg
            MeasureUnit.L -> price / weight           // volume in L -> price per L
        }

    private fun defaultWeightFor(unit: MeasureUnit): Double =
        when (unit) {
            MeasureUnit.KG -> DEFAULT_WEIGHT_GRAMS
            MeasureUnit.L -> DEFAULT_VOLUME_LITERS
        }

    companion object {
        private const val BEVERAGES_LABEL = "beverages"
        private const val DEFAULT_WEIGHT_GRAMS = 100.0
        private const val DEFAULT_VOLUME_LITERS = 1.0
    }
}
