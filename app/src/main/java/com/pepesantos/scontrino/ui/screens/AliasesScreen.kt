package com.pepesantos.scontrino.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.ProductUsage
import com.pepesantos.scontrino.ui.components.AliasDialog
import com.pepesantos.scontrino.ui.components.CategoryPickerDialog
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import com.pepesantos.scontrino.ui.theme.displayColor
import com.pepesantos.scontrino.ui.theme.displayName
import com.pepesantos.scontrino.ui.theme.icon
import com.pepesantos.scontrino.ui.viewmodel.SettingsViewModel

/**
 * Every product bought so far, to give it an alias: products without alias first (most
 * bought first), then each alias with its category and the products grouped under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasesScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val rows by viewModel.aliasRows.collectAsState()
    val aliases by viewModel.aliases.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProductUsage?>(null) }
    // Alias whose category is being changed.
    var categoryOf by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onNavigateBack)

    val filtered = remember(rows, query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter {
            it.product.name.contains(q, ignoreCase = true) ||
                it.product.alias?.contains(q, ignoreCase = true) == true
        }
    }
    val withoutAlias = remember(filtered) { filtered.filter { it.product.alias == null } }
    // Already sorted by alias, so grouping keeps that order.
    val groups = remember(filtered) {
        filtered.filter { it.product.alias != null }.groupBy { it.product.alias!! }
    }
    val categoryByAlias = remember(aliases, categories) {
        val byId = categories.associateBy { it.id }
        aliases.mapNotNull { alias -> byId[alias.categoryId]?.let { alias.name to it } }.toMap()
    }
    val allProducts = remember(rows) { rows.map { it.product } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aliases_title), fontWeight = FontWeight.Bold) },
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
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.aliases_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.compare_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(
                        if (rows.isEmpty()) R.string.aliases_empty else R.string.aliases_no_match
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (withoutAlias.isNotEmpty()) {
                    item(key = "header-without") {
                        SectionHeader(stringResource(R.string.aliases_section_without, withoutAlias.size))
                    }
                    items(withoutAlias, key = { it.product.id }) { row ->
                        ProductRow(row = row, indented = false, onClick = { editing = row })
                    }
                }
                if (groups.isNotEmpty()) {
                    item(key = "header-with") {
                        SectionHeader(stringResource(R.string.aliases_section_with, groups.size))
                    }
                    groups.forEach { (alias, products) ->
                        item(key = "alias-$alias") {
                            AliasHeader(
                                alias = alias,
                                category = categoryByAlias[alias],
                                onClick = { categoryOf = alias }
                            )
                        }
                        items(products, key = { it.product.id }) { row ->
                            ProductRow(row = row, indented = true, onClick = { editing = row })
                        }
                    }
                }
            }
        }
    }

    editing?.let { row ->
        AliasDialog(
            productName = row.product.name,
            currentAlias = row.product.alias,
            products = allProducts,
            onConfirm = { alias ->
                viewModel.setAlias(row.product, alias)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    categoryOf?.let { alias ->
        CategoryPickerDialog(
            categories = categories,
            title = alias,
            onSelect = {
                viewModel.setAliasCategory(alias, it)
                categoryOf = null
            },
            onDismiss = { categoryOf = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** Alias with its category icon and name; tap to change the category of the group. */
@Composable
private fun AliasHeader(alias: String, category: Category?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.category_select_title), onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = category?.icon() ?: CategoryIcons.getIcon("Help"),
            contentDescription = category?.displayName() ?: stringResource(R.string.category_label),
            tint = category?.displayColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alias,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            category?.let {
                Text(
                    text = it.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A product as named on the receipts, with how many times it was bought. */
@Composable
private fun ProductRow(row: ProductUsage, indented: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indented) 52.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = row.product.name,
            style = if (indented) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = if (indented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "×${row.purchaseCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
