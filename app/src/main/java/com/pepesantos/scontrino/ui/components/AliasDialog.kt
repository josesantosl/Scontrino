package com.pepesantos.scontrino.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.alias.AliasRules
import com.pepesantos.scontrino.data.model.Product

/**
 * Adds, changes or removes the alias of the product called [productName]. Suggests the
 * aliases already in use and the products without alias (never aliased products, which
 * would chain aliases), the closest to [productName] first.
 *
 * [onConfirm] receives what was typed or picked (null to remove the alias); the caller
 * resolves it with [AliasRules.resolve].
 */
@Composable
fun AliasDialog(
    productName: String,
    currentAlias: String?,
    products: List<Product>,
    onConfirm: (alias: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentAlias.orEmpty()) }
    val suggestions = remember(text, products, productName) {
        AliasRules.suggestions(text, productName, products)
            .filterNot { it.text.equals(text.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alias_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.alias_field)) },
                    placeholder = { Text(stringResource(R.string.alias_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.alias_suggestions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { text = suggestion.text }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (suggestion.isExistingAlias) Icons.AutoMirrored.Filled.Label else Icons.Outlined.ShoppingBag,
                                contentDescription = stringResource(
                                    if (suggestion.isExistingAlias) R.string.alias_suggestion_alias
                                    else R.string.alias_suggestion_product
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = suggestion.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(AliasRules.normalize(text)) }) {
                Text(stringResource(R.string.alias_save))
            }
        },
        dismissButton = {
            Row {
                if (currentAlias != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.alias_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        },
    )
}
