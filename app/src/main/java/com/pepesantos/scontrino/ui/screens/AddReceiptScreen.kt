package com.pepesantos.scontrino.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R

data class ItemEntry(
    val name: String = "",
    val price: String = "",
    val quantity: Int = 1,
    val categoryId: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptScreen(
    onNavigateBack: () -> Unit
) {
    var storeName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf(ItemEntry()) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val selectedDate = datePickerState.selectedDateMillis?.let {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_receipt_title)) },
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
                    onClick = { /* TODO guardar */ },
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
                        items[index] = items[index].copy(name = name)
                    },
                    onPriceChange = { price ->
                        items[index] = items[index].copy(price = price)
                        if (index == items.lastIndex && price.isNotBlank()) {
                            items.add(ItemEntry())
                        }
                    },
                    onQuantityChange = { quantity ->
                        items[index] = items[index].copy(quantity = quantity)
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
    onPriceChange: (String) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.price,
                    onValueChange = onPriceChange,
                    label = { Text(stringResource(R.string.add_receipt_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.quantity.toString(),
                    onValueChange = { onQuantityChange(it.toIntOrNull() ?: 1) },
                    label = { Text(stringResource(R.string.add_receipt_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}