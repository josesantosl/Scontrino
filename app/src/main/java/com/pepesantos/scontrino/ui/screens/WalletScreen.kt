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
import androidx.compose.ui.graphics.toArgb
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

val sampleCards = listOf(
    LoyaltyCard(1, "Bennet", "0420003982200", 0xFFB05050),
    LoyaltyCard(2, "Crai", "1234567890123", 0xFF2E7D32),
    LoyaltyCard(3, "Lidl", "9876543210987", 0xFF1565C0),
    LoyaltyCard(4, "Carrefour", "5647382910111", 0xFF5B9BD5),
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
fun WalletScreen() {
    var selectedCard by remember { mutableStateOf<LoyaltyCard?>(null) }
    val cards = remember { sampleCards.toMutableStateList() }

    selectedCard?.let { card ->
        BarcodeDialog(
            card = card,
            onDismiss = { selectedCard = null }
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
            TextButton(onClick = { /* TODO edit mode */ }) {
                Text(stringResource(R.string.wallet_edit))
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(cards) { card ->
                LoyaltyCardItem(
                    card = card,
                    onClick = { selectedCard = card }
                )
            }
            item {
                AddCardButton(onClick = { /* TODO */ })
            }
        }
    }
}

@Composable
fun LoyaltyCardItem(
    card: LoyaltyCard,
    onClick: () -> Unit
) {
    val cardColor = Color(card.color)
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // diagonal darker stripe
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkColor.copy(alpha = 0.3f))
        )
        Text(
            text = card.name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BarcodeDialog(
    card: LoyaltyCard,
    onDismiss: () -> Unit
) {
    val barcodeBitmap = remember(card.cardNumber) {
        generateBarcode(card.cardNumber)
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
                        .background(Color(card.color)),
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
                        text = card.name,
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
                        text = card.cardNumber,
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