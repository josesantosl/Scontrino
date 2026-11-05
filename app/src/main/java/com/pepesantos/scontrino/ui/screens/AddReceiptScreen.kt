package com.pepesantos.scontrino.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import com.pepesantos.scontrino.ui.theme.displayName
import com.pepesantos.scontrino.ui.theme.displayColor
import com.pepesantos.scontrino.ui.theme.icon
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher

/**
 * Screen for creating or editing a receipt.
 * Features automatic category detection, store/product autocompletion, a dynamic item list, and date selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptScreen(
    onNavigateBack: () -> Unit,
    onSave: (storeName: String, date: Long, note: String?, items: List<ItemEntry>) -> Unit,
    categories: List<Category> = emptyList(),
    existingReceipt: ReceiptWithStoreName? = null,
    existingItems: List<ItemEntry> = emptyList(),
    availableStores: List<Store> = emptyList(),
    availableProducts: List<Product> = emptyList(),
) {
    val receiptId = existingReceipt?.receipt?.id ?: -1
    val isEditMode = existingReceipt != null
    
    // State reset logic: when receiptId changes, reset storeName, note, and items
    var storeName by remember(receiptId) { mutableStateOf(existingReceipt?.storeName ?: "") }
    var note by remember(receiptId) { mutableStateOf(existingReceipt?.receipt?.note ?: "") }
    
    val items = remember(receiptId) { mutableStateListOf<ItemEntry>() }
    
    // Effect to populate items when loading from database (handles async loading)
    LaunchedEffect(receiptId, existingItems) {
        if (existingItems.isNotEmpty()) {
            items.clear()
            items.addAll(existingItems)
            items.add(ItemEntry(categoryId = 1)) // Add empty row for new additions
        } else if (receiptId == -1 && items.isEmpty()) {
            items.add(ItemEntry(categoryId = 1))
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val datePickerState = key(receiptId) {
        rememberDatePickerState(
            initialSelectedDateMillis = existingReceipt?.receipt?.date ?: System.currentTimeMillis()
        )
    }
    val selectedDate = datePickerState.selectedDateMillis?.let {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
    } ?: ""

    val initialStoreName = existingReceipt?.storeName ?: ""
    val initialNote = existingReceipt?.receipt?.note ?: ""
    val initialDate = existingReceipt?.receipt?.date

    val hasChanges = remember(
        storeName,
        note,
        datePickerState.selectedDateMillis,
        items.toList(),
        existingReceipt,
        existingItems
    ) {
        val currentValidItems = items.filter { it.name.isNotBlank() || it.price > 0.0 || it.weight != null }
        if (isEditMode) {
            storeName != initialStoreName ||
                    note != initialNote ||
                    (initialDate != null && datePickerState.selectedDateMillis != initialDate) ||
                    currentValidItems != existingItems
        } else {
            storeName.isNotBlank() || note.isNotBlank() || currentValidItems.isNotEmpty()
        }
    }

    val handleBack = remember(hasChanges, onNavigateBack) {
        {
            if (hasChanges) {
                showDiscardDialog = true
            } else {
                onNavigateBack()
            }
        }
    }

    BackHandler(enabled = true, onBack = handleBack)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.unsaved_changes_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) stringResource(R.string.edit_receipt_title)
                        else stringResource(R.string.add_receipt_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_receipt_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        onSave(
                            storeName,
                            datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                            note.ifBlank { null },
                            items.filter { it.name.isNotBlank() }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_receipt_save))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                var storeExpanded by remember { mutableStateOf(false) }
                val storeSuggestions = remember(availableStores) {
                    availableStores.map { it.name }.distinct()
                }
                val filteredStoreSuggestions = remember(storeName, storeSuggestions) {
                    if (storeName.isBlank()) {
                        emptyList()
                    } else {
                        storeSuggestions.filter {
                            it.contains(storeName, ignoreCase = true) && !it.equals(storeName, ignoreCase = true)
                        }.take(5)
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = storeExpanded && filteredStoreSuggestions.isNotEmpty(),
                    onExpandedChange = { storeExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = {
                            storeName = it
                            storeExpanded = true
                        },
                        label = { Text(stringResource(R.string.add_receipt_store)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true
                    )
                    if (filteredStoreSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = storeExpanded,
                            onDismissRequest = { storeExpanded = false }
                        ) {
                            filteredStoreSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        storeName = suggestion
                                        storeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = selectedDate,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.add_receipt_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = stringResource(R.string.add_receipt_select_date)
                            )
                        }
                    }
                )
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text(stringResource(R.string.add_receipt_ok))
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.add_receipt_items),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            itemsIndexed(items) { index, item ->
                ItemRow(
                    item = item,
                    categories = categories,
                    availableProducts = availableProducts,
                    onNameChange = { name ->
                        var categoryId = items[index].categoryId
                        // Automatically detect category based on product name
                        val detectedLabel = CategoryMatcher.detectCategoryLabel(name)
                        if (detectedLabel != null) {
                            val category = categories.find { it.label == detectedLabel }
                            if (category != null) {
                                categoryId = category.id
                            }
                        }
                        
                        val updated = items[index].copy(name = name, categoryId = categoryId)
                        items[index] = updated
                        // Add new empty row if user started typing in the last one
                        if (index == items.lastIndex && updated.name.isNotBlank()) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onProductSelect = { selectedProduct ->
                        val updated = items[index].copy(
                            name = selectedProduct.name,
                            categoryId = selectedProduct.categoryId
                        )
                        items[index] = updated
                        if (index == items.lastIndex && updated.name.isNotBlank()) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onPriceChange = { price ->
                        val updated = items[index].copy(price = price)
                        items[index] = updated
                        if (index == items.lastIndex && updated.price != 0.0) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onWeightChange = { weight ->
                        val updated = items[index].copy(weight = weight)
                        items[index] = updated
                        if (index == items.lastIndex && updated.weight != null) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onQuantityChange = { quantity ->
                        val updated = items[index].copy(quantity = quantity)
                        items[index] = updated
                        if (index == items.lastIndex && updated.quantity > 1) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onCategoryChange = { catId ->
                        items[index] = items[index].copy(categoryId = catId)
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.add_receipt_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }
    }
}

/**
 * Individual row for a product entry.
 * Includes category selector, product name with autocomplete, price input, and quantity counter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemRow(
    item: ItemEntry,
    categories: List<Category>,
    availableProducts: List<Product> = emptyList(),
    onNameChange: (String) -> Unit,
    onProductSelect: ((Product) -> Unit)? = null,
    onPriceChange: (Double) -> Unit,
    onWeightChange: (Double?) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onCategoryChange: (Int) -> Unit
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    val currentCategory = categories.find { it.id == item.categoryId } ?: categories.firstOrNull()

    // Local state for price text to handle intermediate states like dots or commas
    var priceInput by remember(item.price) { 
        mutableStateOf(if (item.price == 0.0) "" else item.price.toString()) 
    }
    var weightInput by remember(item.weight) {
        mutableStateOf(
            item.weight?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }

    val isBeverage = currentCategory?.label == "beverages"
    val weightOrVolumeLabel = if (isBeverage) {
        stringResource(R.string.add_receipt_volume)
    } else {
        stringResource(R.string.add_receipt_weight)
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(stringResource(R.string.category_select_title)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(categories) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCategoryChange(category.id)
                                    showCategoryDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = category.displayName(),
                                tint = category.displayColor()
                            )
                            Text(category.displayName())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Category button: triggers manual override dialog
                IconButton(onClick = { showCategoryDialog = true }) {
                    Icon(
                        imageVector = currentCategory?.icon() ?: CategoryIcons.getIcon("Help"),
                        contentDescription = currentCategory?.displayName() ?: stringResource(R.string.category_label),
                        tint = currentCategory?.displayColor() ?: MaterialTheme.colorScheme.primary
                    )
                }
                
                var productExpanded by remember { mutableStateOf(false) }
                val filteredProducts = remember(item.name, availableProducts) {
                    if (item.name.isBlank()) {
                        emptyList()
                    } else {
                        availableProducts
                            .distinctBy { it.name.lowercase() }
                            .filter {
                                it.name.contains(item.name, ignoreCase = true) &&
                                        !it.name.equals(item.name, ignoreCase = true)
                            }
                            .take(5)
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = productExpanded && filteredProducts.isNotEmpty(),
                    onExpandedChange = { productExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = {
                            onNameChange(it)
                            productExpanded = true
                        },
                        label = { Text(stringResource(R.string.add_receipt_item_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true
                    )
                    if (filteredProducts.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = productExpanded,
                            onDismissRequest = { productExpanded = false }
                        ) {
                            filteredProducts.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product.name) },
                                    onClick = {
                                        onProductSelect?.invoke(product) ?: onNameChange(product.name)
                                        productExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { newValue ->
                        // Allow digits and a single optional decimal separator
                        if (newValue.isEmpty() || newValue.matches(Regex("""^\d*[.,]?\d*$"""))) {
                            priceInput = newValue
                            val parsedPrice = newValue.replace(',', '.').toDoubleOrNull() ?: 0.0
                            onPriceChange(parsedPrice)
                        }
                    },
                    label = { Text(stringResource(R.string.add_receipt_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("""^\d*[.,]?\d*$"""))) {
                            weightInput = newValue
                            val parsedWeight = newValue.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                            onWeightChange(parsedWeight)
                        }
                    },
                    label = { Text(weightOrVolumeLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                
                // Quantity Counter (+ / - buttons)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    IconButton(
                        onClick = { if (item.quantity > 1) onQuantityChange(item.quantity - 1) },
                        enabled = item.quantity > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    IconButton(
                        onClick = { onQuantityChange(item.quantity + 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        }
    }
}
