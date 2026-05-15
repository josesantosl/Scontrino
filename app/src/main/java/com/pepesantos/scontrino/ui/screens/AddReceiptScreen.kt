package com.pepesantos.scontrino.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptScreen(
    onNavigateBack: () -> Unit,
    onSave: (storeName: String, date: Long, note: String?, items: List<ItemEntry>) -> Unit,
    existingReceipt: ReceiptWithStoreName? = null,
    existingItems: List<ItemEntry> = emptyList(),
) {
    // Usamos el ID del recibo como clave principal para resetear todo el estado
    val receiptId = existingReceipt?.receipt?.id ?: -1
    val isEditMode = existingReceipt != null
    
    var storeName by remember(receiptId) { mutableStateOf(existingReceipt?.storeName ?: "") }
    var note by remember(receiptId) { mutableStateOf(existingReceipt?.receipt?.note ?: "") }
    
    // Para los items, queremos que se inicialicen con existingItems cuando estos lleguen del ViewModel
    val items = remember(receiptId) { mutableStateListOf<ItemEntry>() }
    
    // Efecto para sincronizar existingItems cuando se cargan
    LaunchedEffect(receiptId, existingItems) {
        if (existingItems.isNotEmpty()) {
            items.clear()
            items.addAll(existingItems)
            items.add(ItemEntry()) // Fila vacía para añadir más
        } else if (receiptId == -1 && items.isEmpty()) {
            items.add(ItemEntry())
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = key(existingReceipt?.receipt?.id) {
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
                            imageVector = Icons.Filled.ArrowBack,
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
                    onNameChange = { name ->
                        val updated = items[index].copy(name = name)
                        items[index] = updated
                        if (index == items.lastIndex && updated.name.isNotBlank()) {
                            items.add(ItemEntry())
                        }
                    },
                    onPriceChange = { price ->
                        val updated = items[index].copy(price = price)
                        items[index] = updated
                        if (index == items.lastIndex && updated.price != 0.0) {
                            items.add(ItemEntry())
                        }
                    },
                    onQuantityChange = { quantity ->
                        val updated = items[index].copy(quantity = quantity)
                        items[index] = updated
                        if (index == items.lastIndex && updated.quantity > 1) {
                            items.add(ItemEntry())
                        }
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

@Composable
fun ItemRow(
    item: ItemEntry,
    onNameChange: (String) -> Unit,
    onPriceChange: (Double) -> Unit,
    onQuantityChange: (Int) -> Unit
) {
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
            OutlinedTextField(
                value = item.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.add_receipt_item_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (item.price == 0.0) "" else item.price.toString(),
                    onValueChange = { onPriceChange(it.toDoubleOrNull() ?: 0.0) },
                    label = { Text(stringResource(R.string.add_receipt_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                
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