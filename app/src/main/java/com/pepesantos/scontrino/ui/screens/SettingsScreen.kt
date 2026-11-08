package com.pepesantos.scontrino.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.settings.AppLocale
import com.pepesantos.scontrino.ui.components.Money
import com.pepesantos.scontrino.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * App settings: language and currency.
 * [onLanguageChanged] must recreate the activity so that the new language is applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLanguageChanged: () -> Unit,
) {
    val language by viewModel.language.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val scope = rememberCoroutineScope()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    // Currency picked while receipts exist: asks whether they change too.
    var pendingCurrency by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = {
                        Text(
                            if (language.isEmpty()) stringResource(R.string.settings_language_system)
                            else AppLocale.displayName(language)
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Language, contentDescription = null) },
                    modifier = Modifier.clickable { showLanguageDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_currency)) },
                    supportingContent = { Text(Money.displayName(currency)) },
                    leadingContent = { Icon(Icons.Filled.Payments, contentDescription = null) },
                    modifier = Modifier.clickable { showCurrencyDialog = true }
                )
            }

            // Same style as "Made in Turin" in the wallet.
            Text(
                text = "Developed by José Santos L.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showLanguageDialog) {
        val options = listOf("") + AppLocale.supported
        OptionsDialog(
            title = stringResource(R.string.settings_language),
            options = options,
            selected = language,
            label = { if (it.isEmpty()) stringResource(R.string.settings_language_system) else AppLocale.displayName(it) },
            onSelect = { tag ->
                showLanguageDialog = false
                if (tag != language) {
                    viewModel.setLanguage(tag)
                    onLanguageChanged()
                }
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            current = currency,
            onConfirm = { chosen ->
                showCurrencyDialog = false
                if (chosen != currency) {
                    scope.launch {
                        if (viewModel.hasReceipts()) {
                            pendingCurrency = chosen
                        } else {
                            viewModel.setCurrency(chosen, applyToExistingReceipts = false)
                        }
                    }
                }
            },
            onDismiss = { showCurrencyDialog = false },
        )
    }

    pendingCurrency?.let { chosen ->
        AlertDialog(
            onDismissRequest = { pendingCurrency = null },
            title = { Text(stringResource(R.string.settings_currency_scope_title)) },
            text = { Text(stringResource(R.string.settings_currency_scope_message, Money.symbol(chosen))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCurrency(chosen, applyToExistingReceipts = true)
                    pendingCurrency = null
                }) { Text(stringResource(R.string.settings_currency_scope_all)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingCurrency = null }) {
                        Text(stringResource(R.string.delete_cancel))
                    }
                    TextButton(onClick = {
                        viewModel.setCurrency(chosen, applyToExistingReceipts = false)
                        pendingCurrency = null
                    }) { Text(stringResource(R.string.settings_currency_scope_new)) }
                }
            },
        )
    }
}

/** Single choice list in a dialog, applied on tap. */
@Composable
private fun <T> OptionsDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    RadioRow(text = label(option), selected = option == selected, onClick = { onSelect(option) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_cancel)) }
        },
    )
}

/** Euro, dollar, Swiss franc, or a currency typed by the user (code or symbol). */
@Composable
private fun CurrencyDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isCustom = current !in Money.presets
    var choice by remember { mutableStateOf(if (isCustom) CUSTOM else current) }
    var custom by remember { mutableStateOf(if (isCustom) current else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            Column {
                Money.presets.forEach { code ->
                    RadioRow(text = Money.displayName(code), selected = choice == code, onClick = { choice = code })
                }
                RadioRow(
                    text = stringResource(R.string.settings_currency_custom),
                    selected = choice == CUSTOM,
                    onClick = { choice = CUSTOM }
                )
                if (choice == CUSTOM) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { if (it.length <= 5) custom = it },
                        label = { Text(stringResource(R.string.settings_currency_custom_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            val value = if (choice == CUSTOM) custom.trim() else choice
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) {
                Text(stringResource(R.string.add_receipt_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_cancel)) }
        },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text)
    }
}

private const val CUSTOM = "\u0000custom"
