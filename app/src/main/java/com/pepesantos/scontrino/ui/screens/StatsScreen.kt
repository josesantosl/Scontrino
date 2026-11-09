package com.pepesantos.scontrino.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.StoreSpending
import com.pepesantos.scontrino.data.model.TopCategorySpending
import com.pepesantos.scontrino.data.model.TopProductSpending
import com.pepesantos.scontrino.ui.components.LocalCurrency
import com.pepesantos.scontrino.ui.components.Money
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import com.pepesantos.scontrino.ui.theme.getCategoryDisplayName
import com.pepesantos.scontrino.ui.viewmodel.DailySpendingPoint
import com.pepesantos.scontrino.ui.viewmodel.PriceComparisonViewModel
import com.pepesantos.scontrino.ui.viewmodel.StatsCard
import com.pepesantos.scontrino.ui.viewmodel.StatsViewModel
import com.pepesantos.scontrino.ui.viewmodel.TimePeriod
import com.pepesantos.scontrino.ui.viewmodel.TopMode
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    priceComparisonViewModel: PriceComparisonViewModel,
) {
    var showComparison by rememberSaveable { mutableStateOf(false) }

    if (showComparison) {
        PriceComparisonScreen(
            viewModel = priceComparisonViewModel,
            onNavigateBack = { showComparison = false }
        )
    } else {
        StatsContent(
            viewModel = viewModel,
            onOpenComparison = { productName ->
                priceComparisonViewModel.open(productName)
                showComparison = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsContent(
    viewModel: StatsViewModel,
    onOpenComparison: (productName: String?) -> Unit,
) {
    val state by viewModel.stats.collectAsState()
    val collapsed by viewModel.collapsedCards.collectAsState()
    fun isCollapsed(card: StatsCard) = card.name in collapsed

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_title),
                        fontWeight = FontWeight.Bold
                    )
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
            // Selector de período rápido
            PeriodSelector(
                selectedPeriod = state.selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Card Gasto Total
                TotalSpendingCard(
                    total = state.totalSpent,
                    percentageChange = state.percentageChange,
                    collapsed = isCollapsed(StatsCard.TOTAL),
                    onToggle = { viewModel.toggleCard(StatsCard.TOTAL) }
                )

                // Card Gasto por Supermercado
                StoreSpendingCard(
                    storeSpendings = state.storeSpendings,
                    overallTotal = state.totalSpent,
                    collapsed = isCollapsed(StatsCard.STORES),
                    onToggle = { viewModel.toggleCard(StatsCard.STORES) }
                )

                // Card Evolución del Gasto
                SpendingEvolutionCard(
                    dailySpendings = state.dailySpendings,
                    collapsed = isCollapsed(StatsCard.EVOLUTION),
                    onToggle = { viewModel.toggleCard(StatsCard.EVOLUTION) }
                )

                // Acceso a la comparativa de precios
                PriceComparisonEntryCard(onClick = { onOpenComparison(null) })

                // Card Productos con Mayor Gasto
                TopProductsCard(
                    topMode = state.topMode,
                    onTopModeSelected = { viewModel.selectTopMode(it) },
                    topProducts = state.topProducts,
                    topCategories = state.topCategories,
                    onProductClick = { onOpenComparison(it.productName) },
                    collapsed = isCollapsed(StatsCard.TOP),
                    onToggle = { viewModel.toggleCard(StatsCard.TOP) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PriceComparisonEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_compare_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.stats_compare_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun PeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedPeriod == TimePeriod.THIS_MONTH,
            onClick = { onPeriodSelected(TimePeriod.THIS_MONTH) },
            label = { Text(stringResource(R.string.stats_period_this_month)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedPeriod == TimePeriod.THREE_MONTHS,
            onClick = { onPeriodSelected(TimePeriod.THREE_MONTHS) },
            label = { Text(stringResource(R.string.stats_period_three_months)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedPeriod == TimePeriod.THIS_YEAR,
            onClick = { onPeriodSelected(TimePeriod.THIS_YEAR) },
            label = { Text(stringResource(R.string.stats_period_this_year)) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Statistics card whose content can be folded away by tapping its header.
 * Only the header toggles, so taps on the content (chart, product rows) keep working.
 */
@Composable
private fun CollapsibleCard(
    title: String,
    icon: ImageVector?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 180f,
        label = "collapseArrow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (collapsed) R.string.stats_card_expand else R.string.stats_card_collapse
                    ),
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            if (!collapsed) {
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun TotalSpendingCard(
    total: Double,
    percentageChange: Double?,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    CollapsibleCard(
        title = stringResource(R.string.stats_total_spending),
        icon = Icons.Default.AccountBalanceWallet,
        collapsed = collapsed,
        onToggle = onToggle,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        TotalSpendingDetails(total = total, percentageChange = percentageChange)
    }
}

@Composable
private fun TotalSpendingDetails(
    total: Double,
    percentageChange: Double?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = Money.format(total, LocalCurrency.current),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (percentageChange != null) {
                val isIncrease = percentageChange > 0
                val icon = if (isIncrease) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                val badgeColor = if (isIncrease) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9)
                val badgeTextColor = if (isIncrease) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "%.1f%% %s".format(abs(percentageChange), stringResource(R.string.stats_vs_prev_period)),
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeTextColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.stats_no_prev_data),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
    }
}

@Composable
fun StoreSpendingCard(
    storeSpendings: List<StoreSpending>,
    overallTotal: Double,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    CollapsibleCard(
        title = stringResource(R.string.stats_spending_by_store),
        icon = Icons.Default.ShoppingCart,
        collapsed = collapsed,
        onToggle = onToggle
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (storeSpendings.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                storeSpendings.forEach { store ->
                    val percentage = if (overallTotal > 0) (store.totalSpent / overallTotal) else 0.0
                    val storeColor = if (store.storeColor != null) Color(store.storeColor) else MaterialTheme.colorScheme.primary

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
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
                                    text = store.storeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "(%.0f%%)".format(percentage * 100),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = Money.format(store.totalSpent, LocalCurrency.current),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { percentage.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = storeColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingEvolutionCard(
    dailySpendings: List<DailySpendingPoint>,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    CollapsibleCard(
        title = stringResource(R.string.stats_spending_evolution),
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        collapsed = collapsed,
        onToggle = onToggle
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (dailySpendings.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val gradientColor = lineColor.copy(alpha = 0.2f)
                val guideColor = MaterialTheme.colorScheme.outlineVariant
                var selectedIndex by remember(dailySpendings) { mutableStateOf<Int?>(null) }
                val selected = selectedIndex?.let { dailySpendings.getOrNull(it) }

                Column {
                    // Detalle del punto tocado (altura fija para que la gráfica no salte)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (selected != null) {
                            Text(
                                text = "${selected.label} · ${Money.format(selected.amount, LocalCurrency.current)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = lineColor
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.stats_chart_tap_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .pointerInput(dailySpendings) {
                                detectTapGestures { tap ->
                                    val count = dailySpendings.size
                                    val index = if (count == 1) 0 else
                                        (tap.x / size.width * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                    selectedIndex = if (selectedIndex == index) null else index
                                }
                            }
                    ) {
                        val maxAmount = dailySpendings.maxOf { it.amount }.coerceAtLeast(1.0)
                        val points = dailySpendings.mapIndexed { index, point ->
                            val x = if (dailySpendings.size > 1) {
                                (index.toFloat() / (dailySpendings.size - 1)) * size.width
                            } else {
                                size.width / 2f
                            }
                            val y = size.height - ((point.amount.toFloat() / maxAmount.toFloat()) * (size.height - 20.dp.toPx())) - 10.dp.toPx()
                            Offset(x, y)
                        }

                        if (points.size == 1) {
                            drawCircle(
                                color = lineColor,
                                radius = 6.dp.toPx(),
                                center = points[0]
                            )
                        } else {
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 0 until points.size - 1) {
                                    val current = points[i]
                                    val next = points[i + 1]
                                    val controlX1 = current.x + (next.x - current.x) / 2f
                                    val controlY1 = current.y
                                    val controlX2 = current.x + (next.x - current.x) / 2f
                                    val controlY2 = next.y
                                    cubicTo(controlX1, controlY1, controlX2, controlY2, next.x, next.y)
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, size.height)
                                lineTo(points.first().x, size.height)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(gradientColor, Color.Transparent),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )

                            drawPath(
                                path = linePath,
                                color = lineColor,
                                style = Stroke(width = 3.dp.toPx())
                            )

                            points.forEach { point ->
                                drawCircle(
                                    color = lineColor,
                                    radius = 4.dp.toPx(),
                                    center = point
                                )
                            }
                        }

                        selectedIndex?.let { points.getOrNull(it) }?.let { point ->
                            drawLine(
                                color = guideColor,
                                start = Offset(point.x, 0f),
                                end = Offset(point.x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = point)
                            drawCircle(color = lineColor, radius = 6.dp.toPx(), center = point)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dailySpendings.forEach { point ->
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopProductsCard(
    topMode: TopMode,
    onTopModeSelected: (TopMode) -> Unit,
    topProducts: List<TopProductSpending>,
    topCategories: List<TopCategorySpending>,
    onProductClick: (TopProductSpending) -> Unit = {},
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    CollapsibleCard(
        title = stringResource(R.string.stats_top_products),
        icon = Icons.Default.Star,
        collapsed = collapsed,
        onToggle = onToggle
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = topMode == TopMode.BY_PRODUCT,
                    onClick = { onTopModeSelected(TopMode.BY_PRODUCT) },
                    label = { Text(stringResource(R.string.stats_top_by_product)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = topMode == TopMode.BY_CATEGORY,
                    onClick = { onTopModeSelected(TopMode.BY_CATEGORY) },
                    label = { Text(stringResource(R.string.stats_top_by_category)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (topMode == TopMode.BY_PRODUCT) {
                if (topProducts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    topProducts.forEachIndexed { index, product ->
                        val rankColor = when (index) {
                            0 -> Color(0xFFFFD700) // Oro
                            1 -> Color(0xFFC0C0C0) // Plata
                            2 -> Color(0xFFCD7F32) // Bronce
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val rankTextColor = if (index < 3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onProductClick(product) }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = rankColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (index + 1).toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = rankTextColor
                                        )
                                    }
                                }
                                Text(
                                    text = product.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = Money.format(product.totalSpent, LocalCurrency.current),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                if (topCategories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    topCategories.forEachIndexed { index, category ->
                        val rankColor = when (index) {
                            0 -> Color(0xFFFFD700) // Oro
                            1 -> Color(0xFFC0C0C0) // Plata
                            2 -> Color(0xFFCD7F32) // Bronce
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val rankTextColor = if (index < 3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        val categoryColor = CategoryIcons.colorFor(category.categoryLabel, category.categoryColor)
                        val categoryIcon = CategoryIcons.forCategory(category.categoryLabel, category.categoryIconName)
                        val categoryName = getCategoryDisplayName(category.categoryLabel)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = rankColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (index + 1).toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = rankTextColor
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = null,
                                    tint = categoryColor,
                                    modifier = Modifier.size(20.dp)
                                )

                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = Money.format(category.totalSpent, LocalCurrency.current),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
