package com.pepesantos.scontrino.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.ReceiptWithStoreName
import com.pepesantos.scontrino.ocr.draft.ReceiptDraft
import com.pepesantos.scontrino.ui.components.Money
import com.pepesantos.scontrino.ui.viewmodel.ReceiptViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ReceiptsScreen(viewModel: ReceiptViewModel) {
    var showAddReceipt by remember { mutableStateOf(false) }
    var editingReceipt by remember { mutableStateOf<ReceiptWithStoreName?>(null) }
    var receiptToDelete by remember { mutableStateOf<ReceiptWithStoreName?>(null) }
    var showOcrDebug by remember { mutableStateOf(false) }
    // A receipt read from a photo, being reviewed in the new receipt screen.
    var scanDraft by remember { mutableStateOf<ReceiptDraft?>(null) }
    // The OCR lab screen is only reachable in debug builds while it is being developed.
    val context = LocalContext.current
    val isDebuggable = remember {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    val receipts by viewModel.receipts.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val products by viewModel.products.collectAsState()
    val purchaseHistory by viewModel.purchaseHistory.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadStoresAndProducts()
    }

    // Fresh stores and products for autocomplete and for recognising scanned receipts.
    LaunchedEffect(showAddReceipt, editingReceipt, showOcrDebug) {
        if (showAddReceipt || editingReceipt != null || showOcrDebug) {
            viewModel.loadStoresAndProducts()
        }
    }

    when {
        showOcrDebug -> {
            OcrDebugScreen(
                onNavigateBack = { showOcrDebug = false },
                onUseReceipt = { parsed ->
                    // Short database read (the store's past weights), then the review screen.
                    scope.launch {
                        scanDraft = viewModel.createDraft(parsed)
                        showOcrDebug = false
                    }
                },
            )
        }
        scanDraft != null -> {
            val draft = scanDraft!!
            AddReceiptScreen(
                onNavigateBack = { scanDraft = null },
                onSave = { storeName, date, note, items ->
                    viewModel.saveReceipt(storeName, date, note, items, storeVatNumber = draft.storeVatNumber)
                    scanDraft = null
                },
                categories = categories,
                availableStores = stores,
                availableProducts = products,
                draft = draft,
                purchaseHistory = purchaseHistory,
                onStoreNameChange = { viewModel.loadPurchaseHistory(it, excludeReceiptId = -1) },
            )
        }
        showAddReceipt -> {
            AddReceiptScreen(
                onNavigateBack = { showAddReceipt = false },
                onSave = { storeName, date, note, items ->
                    viewModel.saveReceipt(storeName, date, note, items)
                    showAddReceipt = false
                },
                categories = categories,
                availableStores = stores,
                availableProducts = products,
                purchaseHistory = purchaseHistory,
                onStoreNameChange = { viewModel.loadPurchaseHistory(it, excludeReceiptId = -1) },
            )
        }
        editingReceipt != null -> {
            AddReceiptScreen(
                onNavigateBack = { editingReceipt = null },
                onSave = { storeName, date, note, items ->
                    viewModel.updateReceipt(editingReceipt!!.receipt, storeName, date, note, items)
                    editingReceipt = null
                },
                categories = categories,
                existingReceipt = editingReceipt,
                existingItems = selectedItems,
                availableStores = stores,
                availableProducts = products,
                purchaseHistory = purchaseHistory,
                onStoreNameChange = {
                    viewModel.loadPurchaseHistory(it, excludeReceiptId = editingReceipt?.receipt?.id ?: -1)
                },
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
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isDebuggable) {
                            SmallFloatingActionButton(onClick = { showOcrDebug = true }) {
                                Icon(
                                    imageVector = Icons.Filled.DocumentScanner,
                                    contentDescription = stringResource(R.string.ocr_debug_open)
                                )
                            }
                        }
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
                            currency = receiptWithStore.receipt.currency,
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
    currency: String = "EUR",
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
                        text = Money.format(total, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
