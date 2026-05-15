package com.pepesantos.scontrino.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import com.pepesantos.scontrino.ui.viewmodel.WalletViewModel
import androidx.compose.runtime.collectAsState

val cardColors = listOf(
    0xFFB05050, // Red
    0xFF2E7D32, // Green
    0xFF1565C0, // Blue
    0xFF5B9BD5, // Light Blue
    0xFF7B1FA2, // Purple
    0xFFFBC02D, // Yellow
    0xFFE64A19, // Deep Orange
    0xFF455A64, // Blue Grey
)

fun generateBarcode(content: String, width: Int = 600, height: Int = 300): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.CODE_128,
            width,
            height,
            hints
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    var selectedCard by remember { mutableStateOf<LoyaltyCardWithStore?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<LoyaltyCardWithStore?>(null) }
    
    val cards by viewModel.cards.collectAsState()

    selectedCard?.let { cardWithStore ->
        BarcodeDialog(
            cardWithStore = cardWithStore,
            onDismiss = { selectedCard = null }
        )
    }
    
    if (showAddDialog) {
        AddCardDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, number, color ->
                viewModel.saveCard(name, number, color)
                showAddDialog = false
            }
        )
    }

    // Dialog de confirmación de borrado
    cardToDelete?.let { cardWithStore ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text(stringResource(R.string.delete_card_title)) },
            text = { Text(stringResource(R.string.delete_card_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCard(cardWithStore.card)
                    cardToDelete = null
                }) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.wallet_your_cards),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(cards) { cardWithStore ->
                LoyaltyCardItem(
                    cardWithStore = cardWithStore,
                    onClick = { selectedCard = cardWithStore },
                    onLongClick = { cardToDelete = cardWithStore }
                )
            }
            item {
                AddCardButton(onClick = { showAddDialog = true })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoyaltyCardItem(
    cardWithStore: LoyaltyCardWithStore,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardColor = Color(cardWithStore.storeColor ?: 0xFF94A3B8)
    val darkColor = Color(
        red = (cardColor.red * 0.7f),
        green = (cardColor.green * 0.7f),
        blue = (cardColor.blue * 0.7f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // diagonal darker stripe
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkColor.copy(alpha = 0.3f))
        )
        Text(
            text = cardWithStore.storeName,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AddCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(cardColors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_add_card_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.wallet_card_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.wallet_card_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = stringResource(R.string.wallet_select_color),
                    style = MaterialTheme.typography.labelLarge
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cardColors.take(4).forEach { colorLong ->
                        ColorOption(
                            color = Color(colorLong),
                            isSelected = selectedColor == colorLong,
                            onClick = { selectedColor = colorLong }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cardColors.drop(4).forEach { colorLong ->
                        ColorOption(
                            color = Color(colorLong),
                            isSelected = selectedColor == colorLong,
                            onClick = { selectedColor = colorLong }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, number, selectedColor) },
                enabled = name.isNotBlank() && number.isNotBlank()
            ) {
                Text(stringResource(R.string.wallet_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wallet_cancel))
            }
        }
    )
}

@Composable
fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BarcodeDialog(
    cardWithStore: LoyaltyCardWithStore,
    onDismiss: () -> Unit
) {
    val barcodeBitmap = remember(cardWithStore.card.cardNumber) {
        generateBarcode(cardWithStore.card.cardNumber)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                // Header con color de la tarjeta
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(cardWithStore.storeColor ?: 0xFF94A3B8)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Chiudi",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = cardWithStore.storeName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Código de barras
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    barcodeBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Barcode",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                    Text(
                        text = cardWithStore.card.cardNumber,
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AddCardButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.wallet_add_card),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
