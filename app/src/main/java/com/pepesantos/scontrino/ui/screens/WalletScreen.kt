package com.pepesantos.scontrino.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ui.components.BarcodeScanner
import com.pepesantos.scontrino.ui.components.ColorPickerDialog
import com.pepesantos.scontrino.ui.components.hueColors
import androidx.compose.ui.graphics.Brush
import com.pepesantos.scontrino.ui.components.LaunchableApp
import com.pepesantos.scontrino.ui.components.PaymentApps
import com.pepesantos.scontrino.ui.viewmodel.WalletViewModel

/**
 * Card colours, shown in two rows of six; any other one can be picked with "Custom color".
 * The first one is the default of a new card.
 */
val cardColors = listOf(
    0xFFD32F2F, // Bright red
    0xFF7B1E2B, // Burgundy
    0xFFEF6C00, // Orange
    0xFFFBC02D, // Yellow
    0xFF2E7D32, // Green
    0xFF7CB342, // Lime

    0xFF5B9BD5, // Light blue
    0xFF1565C0, // Medium blue
    0xFF1A237E, // Navy
    0xFF757575, // Grey
    0xFF212121, // Black
    0xFFFFFFFF, // White (Google Pay, Apple Pay...)
)

/** Text and icons on a card of [color]: dark on very light colours (white), white otherwise. */
fun onCardColor(color: Color): Color =
    if (color.luminance() > 0.6f) Color.Black.copy(alpha = 0.87f) else Color.White

/** Light colours (white) need an outline to be seen against a light background. */
fun isLightCardColor(color: Color): Boolean = color.luminance() > 0.6f

fun generateBarcode(content: String, width: Int = 600, height: Int = 300): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        
        // Determinar el formato más probable basado en la longitud
        val format = when {
            content.length == 13 && content.all { it.isDigit() } -> BarcodeFormat.EAN_13
            content.length == 12 && content.all { it.isDigit() } -> BarcodeFormat.UPC_A
            content.length == 8 && content.all { it.isDigit() } -> BarcodeFormat.EAN_8
            else -> BarcodeFormat.CODE_128
        }

        val bitMatrix = MultiFormatWriter().encode(
            content,
            format,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    var selectedCard by remember { mutableStateOf<LoyaltyCardWithStore?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<LoyaltyCardWithStore?>(null) }
    
    val cards by viewModel.cards.collectAsState()
    val stores by viewModel.stores.collectAsState()

    val context = LocalContext.current
    val paymentPackage by viewModel.paymentApp.collectAsState()
    // Read again each time the wallet is shown: hidden if the app was uninstalled meanwhile.
    val paymentApp = remember(paymentPackage) {
        if (PaymentApps.isEnabled(context)) PaymentApps.load(context, paymentPackage) else null
    }
    val paymentAppName by viewModel.paymentAppName.collectAsState()
    val paymentAppColor by viewModel.paymentAppColor.collectAsState()
    var editPaymentApp by remember { mutableStateOf(false) }

    paymentApp?.takeIf { editPaymentApp }?.let { app ->
        EditPaymentAppCardDialog(
            appLabel = app.label,
            currentName = paymentAppName,
            currentColor = paymentAppColor ?: PAYMENT_APP_DEFAULT_COLOR,
            onSave = { name, color ->
                viewModel.setPaymentAppCard(name, color)
                editPaymentApp = false
            },
            onDismiss = { editPaymentApp = false }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadStores()
    }

    LaunchedEffect(showAddDialog) {
        if (showAddDialog) {
            viewModel.loadStores()
        }
    }

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
            },
            availableStores = stores
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_wallet),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            paymentApp?.let { app ->
                item {
                    PaymentAppCard(
                        app = app,
                        name = paymentAppName.ifBlank { app.label },
                        color = paymentAppColor ?: PAYMENT_APP_DEFAULT_COLOR,
                        onClick = {
                            if (!PaymentApps.open(context, app.packageName)) viewModel.clearPaymentApp()
                        },
                        onLongClick = { editPaymentApp = true }
                    )
                }
            }
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

        Text(
            text = "Made in Turin ♥️",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
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
            color = onCardColor(cardColor),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit,
    availableStores: List<Store> = emptyList()
) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(cardColors[0]) }
    var showScanner by remember { mutableStateOf(false) }

    var nameExpanded by remember { mutableStateOf(false) }
    val storeSuggestions = remember(availableStores) {
        availableStores.map { it.name }.distinct()
    }
    val filteredStoreSuggestions = remember(name, storeSuggestions) {
        if (name.isBlank()) {
            emptyList()
        } else {
            storeSuggestions.filter {
                it.contains(name, ignoreCase = true) && !it.equals(name, ignoreCase = true)
            }.take(5)
        }
    }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        }
    }

    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                BarcodeScanner(
                    onBarcodeScanned = {
                        number = it
                        showScanner = false
                    },
                    onDismiss = { showScanner = false }
                )
            }
        }
    }

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
                ExposedDropdownMenuBox(
                    expanded = nameExpanded && filteredStoreSuggestions.isNotEmpty(),
                    onExpandedChange = { nameExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameExpanded = true
                        },
                        label = { Text(stringResource(R.string.wallet_card_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true
                    )
                    if (filteredStoreSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = nameExpanded,
                            onDismissRequest = { nameExpanded = false }
                        ) {
                            filteredStoreSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        name = suggestion
                                        nameExpanded = false
                                        val matchedStore = availableStores.firstOrNull { it.name.equals(suggestion, ignoreCase = true) }
                                        if (matchedStore?.color != null) {
                                            selectedColor = matchedStore.color
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.wallet_card_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = {
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                showScanner = true
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.wallet_scan)
                            )
                        }
                    }
                )
                
                CardColorPicker(selected = selectedColor, onSelect = { selectedColor = it })
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

/**
 * "Select color" label, the [cardColors] in two rows of six spread over the whole width
 * (each circle up to 40 dp, smaller if the dialog is narrow) and a "Custom color" row that
 * opens [ColorPickerDialog]. A colour outside the palette shows up selected in that row.
 */
@Composable
private fun CardColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }
    if (showCustom) {
        ColorPickerDialog(
            initial = selected,
            onConfirm = {
                onSelect(it)
                showCustom = false
            },
            onDismiss = { showCustom = false }
        )
    }
    Text(
        text = stringResource(R.string.wallet_select_color),
        style = MaterialTheme.typography.labelLarge
    )
    cardColors.chunked(COLORS_PER_ROW).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { colorLong ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ColorOption(
                        color = Color(colorLong),
                        isSelected = selected == colorLong,
                        onClick = { onSelect(colorLong) },
                        modifier = Modifier
                            .widthIn(max = 40.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }

    val isCustom = selected !in cardColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { showCustom = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCustom) {
            ColorOption(
                color = Color(selected),
                isSelected = true,
                onClick = { showCustom = true },
                modifier = Modifier.size(32.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(hueColors))
            )
        }
        Text(
            text = if (isCustom) {
                stringResource(R.string.wallet_custom_color) + "  ·  #%06X".format(selected and 0xFFFFFF)
            } else {
                stringResource(R.string.wallet_custom_color)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private const val COLORS_PER_ROW = 6

@Composable
fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else if (isLightCardColor(color)) 1.dp else 0.dp,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isLightCardColor(color) -> MaterialTheme.colorScheme.outline
                    else -> Color.Transparent
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = onCardColor(color),
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
                val headerColor = Color(cardWithStore.storeColor ?: 0xFF94A3B8)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(headerColor),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Chiudi",
                            tint = onCardColor(headerColor)
                        )
                    }
                    Text(
                        text = cardWithStore.storeName,
                        color = onCardColor(headerColor),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // A white header would melt into the white barcode area.
                if (isLightCardColor(headerColor)) {
                    HorizontalDivider(color = Color.Black.copy(alpha = 0.12f))
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

/** Default colour of the payment app card (Grey of [cardColors]). */
private const val PAYMENT_APP_DEFAULT_COLOR = 0xFF757575

/**
 * Opens the payment app chosen in settings. Same look as the loyalty cards (title centred on
 * the colour), plus the app's own icon so it's recognisable; long press to rename or recolour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaymentAppCard(
    app: LaunchableApp,
    name: String,
    color: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cardColor = Color(color)
    val darkColor = Color(red = cardColor.red * 0.7f, green = cardColor.green * 0.7f, blue = cardColor.blue * 0.7f)
    val openLabel = stringResource(R.string.wallet_open_payment_app)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .combinedClickable(onClick = onClick, onClickLabel = openLabel, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkColor.copy(alpha = 0.3f))
        )
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(36.dp)
        )
        Text(
            text = name,
            color = onCardColor(cardColor),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp)
        )
    }
}

/** Rename (empty = the app's own name) and recolour the payment app card. */
@Composable
private fun EditPaymentAppCardDialog(
    appLabel: String,
    currentName: String,
    currentColor: Long,
    onSave: (name: String, color: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var color by remember { mutableStateOf(currentColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_payment_app)) },
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
                    placeholder = { Text(appLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                CardColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, color) }) {
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
