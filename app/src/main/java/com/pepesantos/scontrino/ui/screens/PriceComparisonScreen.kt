package com.pepesantos.scontrino.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.ComparableProduct
import com.pepesantos.scontrino.ui.components.LocalCurrency
import com.pepesantos.scontrino.ui.components.Money
import com.pepesantos.scontrino.ui.viewmodel.MeasureUnit
import com.pepesantos.scontrino.ui.viewmodel.PriceComparisonState
import com.pepesantos.scontrino.ui.viewmodel.PriceComparisonViewModel
import com.pepesantos.scontrino.ui.viewmodel.StorePriceRow
import com.pepesantos.scontrino.ui.viewmodel.WeightSource
import kotlin.math.roundToInt

private val CheapestColor = Color(0xFF2E7D32)

/**
 * Compares the latest price of a product across supermarkets, normalized per kg (or per L for beverages).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceComparisonScreen(
    viewModel: PriceComparisonViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.compare_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_receipt_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProductSearchField(
                products = state.products,
                selectedProduct = state.selectedProduct,
                onProductSelected = viewModel::selectProduct,
                onClear = viewModel::clearSelection
            )

            when {
                state.selectedProduct == null -> HintText(stringResource(R.string.compare_empty_hint))
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                state.rows.isEmpty() -> HintText(stringResource(R.string.compare_no_data))
                else -> {
                    ComparisonCard(state)
                    if (state.rows.size == 1) {
                        HintText(stringResource(R.string.compare_single_store))
                    } else {
                        SavingsCard(state)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductSearchField(
    products: List<ComparableProduct>,
    selectedProduct: ComparableProduct?,
    onProductSelected: (ComparableProduct) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var query by remember(selectedProduct?.name) { mutableStateOf(selectedProduct?.name ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val suggestions = remember(query, products, selectedProduct) {
        if (query.isBlank() || query == selectedProduct?.name) {
            emptyList()
        } else {
            products.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(8)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            placeholder = { Text(stringResource(R.string.compare_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onClear()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.compare_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
        )
        if (suggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                suggestions.forEach { product ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = product.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.compare_store_count,
                                        product.storeCount,
                                        product.storeCount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        },
                        onClick = {
                            query = product.name
                            expanded = false
                            focusManager.clearFocus()
                            onProductSelected(product)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(state: PriceComparisonState) {
    val maxPrice = state.mostExpensive?.pricePerUnit?.takeIf { it > 0.0 } ?: 1.0
    val unitLabel = unitLabel(state.unit)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = state.selectedProduct?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Money.symbol(LocalCurrency.current)}/$unitLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.rows.forEachIndexed { index, row ->
                StorePriceItem(
                    row = row,
                    isCheapest = index == 0 && state.rows.size > 1,
                    progress = (row.pricePerUnit / maxPrice).toFloat(),
                    unit = state.unit
                )
            }
        }
    }
}

@Composable
private fun StorePriceItem(
    row: StorePriceRow,
    isCheapest: Boolean,
    progress: Float,
    unit: MeasureUnit,
) {
    val storeColor = row.storeColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(storeColor)
            )
            Text(
                text = row.storeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SourceBadge(source = row.source, unit = unit)
            Text(
                text = "${Money.format(row.pricePerUnit, LocalCurrency.current)}/${unitLabel(unit)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCheapest) CheapestColor else MaterialTheme.colorScheme.onSurface
            )
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isCheapest) CheapestColor else storeColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )

        Text(
            text = stringResource(R.string.compare_paid, Money.format(row.price, LocalCurrency.current)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SourceBadge(source: WeightSource, unit: MeasureUnit) {
    val (label, container, content) = when (source) {
        WeightSource.MEASURED -> Triple(
            stringResource(if (unit == MeasureUnit.L) R.string.compare_source_volume else R.string.compare_source_weight),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        WeightSource.ESTIMATED -> Triple(
            stringResource(R.string.compare_source_estimated),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        WeightSource.DEFAULT -> Triple(
            stringResource(R.string.compare_source_unit),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SavingsCard(state: PriceComparisonState) {
    val cheapest = state.cheapest ?: return
    val expensive = state.mostExpensive ?: return
    val fraction = state.savingsFraction ?: return
    val currency = LocalCurrency.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (fraction <= 0.0) {
                    stringResource(R.string.compare_same_price)
                } else {
                    stringResource(
                        R.string.compare_savings,
                        cheapest.storeName,
                        (fraction * 100).roundToInt(),
                        expensive.storeName,
                        "${Money.format(expensive.pricePerUnit - cheapest.pricePerUnit, currency)}/${unitLabel(state.unit)}"
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}

private fun unitLabel(unit: MeasureUnit): String = when (unit) {
    MeasureUnit.KG -> "kg"
    MeasureUnit.L -> "L"
}
