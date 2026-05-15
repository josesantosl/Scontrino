package com.pepesantos.scontrino.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.ui.viewmodel.ReceiptViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptsScreen(viewModel: ReceiptViewModel) {
    var showAddReceipt by remember { mutableStateOf(false) }
    var editingReceipt by remember { mutableStateOf<ReceiptWithStoreName?>(null) }
    var receiptToDelete by remember { mutableStateOf<ReceiptWithStoreName?>(null) }

    val receipts by viewModel.receipts.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()

    when {
        showAddReceipt -> {
            AddReceiptScreen(
                onNavigateBack = { showAddReceipt = false },
                onSave = { storeName, date, note, items ->
                    viewModel.saveReceipt(storeName, date, note, items)
                    showAddReceipt = false
                }
            )
        }
        editingReceipt != null -> {
            AddReceiptScreen(
                onNavigateBack = { editingReceipt = null },
                onSave = { storeName, date, note, items ->
                    viewModel.updateReceipt(editingReceipt!!.receipt, storeName, date, note, items)
                    editingReceipt = null
                },
                existingReceipt = editingReceipt,
                existingItems = selectedItems
            )
        }
        else -> {
            // Dialog de confirmación de borrado
            receiptToDelete?.let { receipt ->
                AlertDialog(
                    onDismissRequest = { receiptToDelete = null },
                    title = { Text(stringResource(R.string.delete_receipt_title)) },
                    text = { Text(stringResource(R.string.delete_receipt_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteReceipt(receipt.receipt)
                            receiptToDelete = null
                        }) {
                            Text(stringResource(R.string.delete_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { receiptToDelete = null }) {
                            Text(stringResource(R.string.delete_cancel))
                        }
                    }
                )
            }

            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddReceipt = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_receipt_fab)
                        )
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(receipts) { receiptWithStore ->
                        ReceiptCard(
                            storeName = receiptWithStore.storeName,
                            date = receiptWithStore.receipt.date,
                            total = receiptWithStore.receipt.total,
                            storeColor = receiptWithStore.storeColor,
                            onClick = {
                                viewModel.loadReceiptById(receiptWithStore.receipt.id)
                                editingReceipt = receiptWithStore
                            },
                            onLongClick = {
                                receiptToDelete = receiptWithStore
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptCard(
    storeName: String,
    date: Long,
    total: Double,
    storeColor: Long? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val formattedDate = remember(date) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(date))
    }
    
    val cardColor = if (storeColor != null) Color(storeColor) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (storeColor != null) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (storeColor != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Box {
            if (storeColor != null) {
                val stripeColor = Color(
                    red = (cardColor.red * 0.7f),
                    green = (cardColor.green * 0.7f),
                    blue = (cardColor.blue * 0.7f)
                ).copy(alpha = 0.3f)
                
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(stripeColor)
                )
            }
            
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = storeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor
                    )
                    Text(
                        text = "€%.2f".format(total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
