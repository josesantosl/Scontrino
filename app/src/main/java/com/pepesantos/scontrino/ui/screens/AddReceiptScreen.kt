package com.pepesantos.scontrino.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.alias.AliasRules
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ocr.draft.ReceiptDraft
import com.pepesantos.scontrino.ui.components.AliasDialog
import com.pepesantos.scontrino.ui.components.CategoryPickerDialog
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import com.pepesantos.scontrino.ui.theme.displayName
import com.pepesantos.scontrino.ui.theme.displayColor
import com.pepesantos.scontrino.ui.theme.icon
import com.pepesantos.scontrino.ui.viewmodel.AutoFilled
import com.pepesantos.scontrino.ui.viewmodel.CategoryMatcher
import com.pepesantos.scontrino.ui.viewmodel.PurchaseHistory
import com.pepesantos.scontrino.ui.viewmodel.prefilledWith
import kotlinx.coroutines.delay

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
    /** Data read from a receipt photo, to prefill a new receipt for review. */
    draft: ReceiptDraft? = null,
    /** Past purchases in the store being typed, requested through [onStoreNameChange]. */
    purchaseHistory: PurchaseHistory = PurchaseHistory.EMPTY,
    onStoreNameChange: (String) -> Unit = {},
) {
    val receiptId = existingReceipt?.receipt?.id ?: -1
    val isEditMode = existingReceipt != null
    // Rows read from the photo keep the receipt's own prices: only rows added by hand are prefilled.
    val scannedRowCount = if (isEditMode) 0 else draft?.items?.size ?: 0
    
    // State reset logic: when receiptId changes, reset storeName, note, and items
    var storeName by remember(receiptId) { mutableStateOf(existingReceipt?.storeName ?: draft?.storeName ?: "") }
    var note by remember(receiptId) { mutableStateOf(existingReceipt?.receipt?.note ?: "") }
    
    val items = remember(receiptId) { mutableStateListOf<ItemEntry>() }

    // Alias already given to each product, so that a known name shows its alias at once.
    val aliasByName = remember(availableProducts) {
        availableProducts.filter { it.alias != null }.associate { it.name.trim().lowercase() to it.alias }
    }
    fun knownAlias(name: String): String? = aliasByName[name.trim().lowercase()]

    fun detectedCategoryId(text: String): Int? =
        CategoryMatcher.detectCategoryLabel(text)?.let { label -> categories.find { it.label == label }?.id }

    /**
     * Category of a row: the one already used by its alias group wins (it was chosen or
     * confirmed by the user), then the one detected from the name, then from the alias,
     * and otherwise [current]. [products] may include rows not saved yet.
     */
    fun categoryFor(name: String, alias: String?, current: Int, products: List<Product> = availableProducts): Int =
        alias?.takeIf { it.isNotBlank() }?.let { AliasRules.groupCategoryId(it, products) }
            ?: detectedCategoryId(name)
            ?: alias?.takeIf { it.isNotBlank() }?.let { detectedCategoryId(it) }
            ?: current
    // Row whose alias is being edited (long press on its card).
    var aliasRowIndex by remember { mutableStateOf<Int?>(null) }
    
    // Effect to populate items when loading from database (handles async loading)
    LaunchedEffect(receiptId, existingItems) {
        if (existingItems.isNotEmpty()) {
            items.clear()
            items.addAll(existingItems)
            items.add(ItemEntry(categoryId = 1)) // Add empty row for new additions
        } else if (receiptId == -1 && items.isEmpty()) {
            draft?.let { d ->
                items.addAll(d.items.map { entry ->
                    val alias = entry.alias ?: knownAlias(entry.name)
                    // The draft already took the category of a known product or of its name:
                    // the alias group's category still wins, the alias text only fills "other".
                    val categoryId = alias?.let { AliasRules.groupCategoryId(it, availableProducts) }
                        ?: entry.categoryId.takeIf { it != 1 }
                        ?: alias?.let { detectedCategoryId(it) }
                        ?: entry.categoryId
                    entry.copy(alias = alias, categoryId = categoryId)
                })
            }
            items.add(ItemEntry(categoryId = 1))
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val datePickerState = key(receiptId) {
        rememberDatePickerState(
            initialSelectedDateMillis = existingReceipt?.receipt?.date
                ?: draft?.dateMillis
                ?: System.currentTimeMillis()
        )
    }
    val selectedDate = datePickerState.selectedDateMillis?.let {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
    } ?: ""
    val selectedDateMillis = datePickerState.selectedDateMillis

    // Ask for the store's purchase history once the user stops typing its name.
    val currentOnStoreNameChange by rememberUpdatedState(onStoreNameChange)
    LaunchedEffect(storeName) {
        delay(300)
        currentOnStoreNameChange(storeName)
    }
    // Drop it when leaving, so the next receipt doesn't briefly show this store's prices.
    DisposableEffect(Unit) {
        onDispose { currentOnStoreNameChange("") }
    }

    // Per row: which values came from the last purchase (and may be replaced or cleared).
    val autoFilled = remember(receiptId) { mutableStateMapOf<Int, AutoFilled>() }
    fun prefill(index: Int, entry: ItemEntry): ItemEntry {
        if (index < scannedRowCount) return entry
        val last = purchaseHistory.lastPurchase(entry.name, selectedDateMillis)
        val (filled, auto) = entry.prefilledWith(last, autoFilled[index] ?: AutoFilled())
        autoFilled[index] = auto
        return filled
    }

    // The store or the date may be set after the items: prefill again rows that are still
    // empty or were prefilled, never values the user typed.
    LaunchedEffect(purchaseHistory, selectedDateMillis) {
        for (index in items.indices) {
            val entry = items[index]
            val auto = autoFilled[index] ?: AutoFilled()
            val untouched = entry.price == 0.0 && entry.weight == null
            if (entry.name.isNotBlank() && (auto.price || auto.weight || untouched)) {
                items[index] = prefill(index, entry)
            }
        }
    }

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

    // Enter on the keyboard goes to the next field (store → item name → price → weight → next item).
    val focusManager = LocalFocusManager.current

    val aliasEntry = aliasRowIndex?.let { items.getOrNull(it) }?.takeIf { it.name.isNotBlank() }
    if (aliasEntry != null) {
        val entry = aliasEntry
        // Aliases given in this receipt (not saved yet) are suggested too.
        val unsaved = items
            .filter { !it.alias.isNullOrBlank() && !it.name.equals(entry.name, ignoreCase = true) }
            .mapIndexed { i, it -> Product(id = -1 - i, name = it.name, categoryId = it.categoryId, alias = it.alias) }
        val products = unsaved + availableProducts
        AliasDialog(
            productName = entry.name,
            currentAlias = entry.alias?.takeIf { it.isNotBlank() },
            products = products,
            onConfirm = { typed ->
                val selfId = availableProducts.firstOrNull { it.name.equals(entry.name, ignoreCase = true) }?.id
                val resolution = AliasRules.resolve(typed, products, selfId)
                val promotedName = resolution.promotedProductId?.let { id -> products.find { it.id == id }?.name }
                // Every row of the same product shares the alias (saved on the product).
                for (i in items.indices) {
                    val row = items[i]
                    if (row.name.equals(entry.name, ignoreCase = true)) {
                        // Blank (not null) so that saving removes the product's alias.
                        val alias = resolution.alias ?: ""
                        val groupProducts = products.filterNot { it.name.equals(entry.name, ignoreCase = true) }
                        items[i] = row.copy(
                            alias = alias,
                            categoryId = alias.takeIf { it.isNotBlank() }
                                ?.let { AliasRules.groupCategoryId(it, groupProducts) }
                                ?: row.categoryId.takeIf { it != 1 }
                                ?: alias.takeIf { it.isNotBlank() }?.let { detectedCategoryId(it) }
                                ?: row.categoryId
                        )
                    } else if (promotedName != null && row.alias == null && row.name.equals(promotedName, ignoreCase = true)) {
                        items[i] = row.copy(alias = promotedName)
                    }
                }
                aliasRowIndex = null
            },
            onDismiss = { aliasRowIndex = null },
        )
    }

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
        // Fit the whole screen above the keyboard, so the save button rises with it and
        // never gets hidden. A ruler, unlike imePadding(), measures from where this screen
        // really ends (above the app's navigation bar), so there's no extra gap.
        modifier = Modifier.fitInside(WindowInsetsRulers.Ime.current),
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
            if (draft != null) {
                item {
                    ScanReviewBanner(
                        draft = draft,
                        currentItemsTotal = items.sumOf { it.price * it.quantity },
                    )
                }
            }
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            storeExpanded = false
                            focusManager.moveFocus(FocusDirection.Next)
                        })
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
                    // Picked with the dialog: skipped when moving to the next field.
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false },
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
                        // Category from the alias group, else detected from the name or the alias
                        val alias = knownAlias(name)
                        val categoryId = categoryFor(name, alias, items[index].categoryId)
                        val updated = prefill(
                            index,
                            items[index].copy(name = name, categoryId = categoryId, alias = alias)
                        )
                        items[index] = updated
                        // Add new empty row if user started typing in the last one
                        if (index == items.lastIndex && updated.name.isNotBlank()) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onProductSelect = { selectedProduct ->
                        val updated = prefill(
                            index,
                            items[index].copy(
                                name = selectedProduct.name,
                                categoryId = selectedProduct.alias
                                    ?.let { AliasRules.groupCategoryId(it, availableProducts) }
                                    ?: selectedProduct.categoryId.takeIf { it != 1 }
                                    ?: categoryFor(selectedProduct.name, selectedProduct.alias, selectedProduct.categoryId),
                                alias = selectedProduct.alias
                            )
                        )
                        items[index] = updated
                        if (index == items.lastIndex && updated.name.isNotBlank()) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onPriceChange = { price ->
                        autoFilled[index] = (autoFilled[index] ?: AutoFilled()).copy(price = false)
                        val updated = items[index].copy(price = price)
                        items[index] = updated
                        if (index == items.lastIndex && updated.price != 0.0) {
                            items.add(ItemEntry(categoryId = 1))
                        }
                    },
                    onWeightChange = { weight ->
                        autoFilled[index] = (autoFilled[index] ?: AutoFilled()).copy(weight = false)
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
                        // The category of a product with alias is the alias's: the whole
                        // group changes, here and (when saved) everywhere.
                        val alias = items[index].alias?.takeIf { it.isNotBlank() }
                        for (i in items.indices) {
                            val row = items[i]
                            if (i == index || (alias != null && row.alias.equals(alias, ignoreCase = true))) {
                                items[i] = row.copy(categoryId = catId)
                            }
                        }
                    },
                    onEditAlias = { if (items[index].name.isNotBlank()) aliasRowIndex = index }
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
 * Shown when the receipt comes from a photo: asks the user to review it and compares,
 * live, the total printed on the receipt with the sum of the items being edited.
 */
@Composable
private fun ScanReviewBanner(draft: ReceiptDraft, currentItemsTotal: Double) {
    val locale = java.util.Locale.getDefault()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.scan_review_notice),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            draft.receiptTotal?.let { receiptTotal ->
                Text(
                    stringResource(
                        R.string.scan_totals,
                        String.format(locale, "%.2f", receiptTotal),
                        String.format(locale, "%.2f", currentItemsTotal),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                // Compare in cents to avoid floating point noise.
                if (Math.round(receiptTotal * 100) != Math.round(currentItemsTotal * 100)) {
                    Text(
                        stringResource(R.string.scan_totals_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
    onCategoryChange: (Int) -> Unit,
    /** Long press on the card, or tap on the alias: add or change the product's alias. */
    onEditAlias: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val currentOnEditAlias by rememberUpdatedState(onEditAlias)
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

    val isBeverage = currentCategory?.label == "beverages" || currentCategory?.label == "alcohol"
    val weightOrVolumeLabel = if (isBeverage) {
        stringResource(R.string.add_receipt_volume)
    } else {
        stringResource(R.string.add_receipt_weight)
    }

    if (showCategoryDialog) {
        CategoryPickerDialog(
            categories = categories,
            onSelect = {
                onCategoryChange(it)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnEditAlias()
                })
            },
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

                // With an alias the field shows it (the name used in the statistics); the
                // name read on the receipt goes small below. Typing changes the product.
                val alias = item.alias?.takeIf { it.isNotBlank() }
                ExposedDropdownMenuBox(
                    expanded = productExpanded && filteredProducts.isNotEmpty(),
                    onExpandedChange = { productExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = alias ?: item.name,
                        onValueChange = {
                            onNameChange(it)
                            productExpanded = true
                        },
                        label = {
                            Text(stringResource(if (alias != null) R.string.alias_field else R.string.add_receipt_item_name))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            productExpanded = false
                            focusManager.moveFocus(FocusDirection.Next)
                        })
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

            // Original name (as on the receipt) when the field shows the alias.
            if (!item.alias.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .padding(start = 56.dp)
                        .clickable(onClickLabel = stringResource(R.string.alias_edit), onClick = onEditAlias),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
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
