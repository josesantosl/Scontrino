package com.pepesantos.scontrino.ui.screens

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
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher

/**
 * Screen for creating or editing a receipt.
 * Features automatic category detection, a dynamic item list, and date selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptScreen(
    onNavigateBack: () -> Unit,
    onSave: (storeName: String, date: Long, note: String?, items: List<ItemEntry>) -> Unit,
    categories: List<Category> = emptyList(),
    existingReceipt: ReceiptWithStoreName? = null,
    existingItems: List<ItemEntry> = emptyList(),
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
    val datePickerState = key(receiptId) {
        rememberDatePickerState(
            initialSelectedDateMillis = existingReceipt?.receipt?.date ?: System.currentTimeMillis()
        )
    }
    val selectedDate = datePickerState.selectedDateMillis?.let {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
    } ?: ""

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
                    IconButton(onClick = onNavigateBack) {
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
                            items.filter { it.name.isNotBlank() && it.price != 0.0 }
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
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(stringResource(R.string.add_receipt_store)) },
                    modifier = Modifier.fillMaxWidth()
                )
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
                    onPriceChange = { price ->
                        val updated = items[index].copy(price = price)
                        items[index] = updated
                        if (index == items.lastIndex && updated.price != 0.0) {
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
 * Includes category selector, product name, price input, and quantity counter.
 */
@Composable
fun ItemRow(
    item: ItemEntry,
    categories: List<Category>,
    onNameChange: (String) -> Unit,
    onPriceChange: (Double) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onCategoryChange: (Int) -> Unit
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    val currentCategory = categories.find { it.id == item.categoryId } ?: categories.firstOrNull()

    // Local state for price text to handle intermediate states like dots or commas
    var priceInput by remember(item.price) { 
        mutableStateOf(if (item.price == 0.0) "" else item.price.toString()) 
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Select Category") },
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
                                imageVector = CategoryIcons.getIcon(category.iconName),
                                contentDescription = category.label,
                                tint = Color(category.color)
                            )
                            Text(category.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel")
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
                        imageVector = CategoryIcons.getIcon(currentCategory?.iconName ?: "Help"),
                        contentDescription = "Category",
                        tint = currentCategory?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
                    )
                }
                
                OutlinedTextField(
                    value = item.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.add_receipt_item_name)) },
                    modifier = Modifier.weight(1f)
                )
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
                
                // Quantity Counter (+ / - buttons)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    IconButton(
                        onClick = { if (item.quantity > 1) onQuantityChange(item.quantity - 1) },
                        enabled = item.quantity > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    IconButton(
                        onClick = { onQuantityChange(item.quantity + 1) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        }
    }
}
