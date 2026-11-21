package com.pepesantos.scontrino.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.ocr.OcrLanguage
import java.util.Locale

/**
 * Languages receipts are printed in, several at once (e.g. Italian receipts and the odd one
 * in English). The order is kept, the first one being the main language for the OCR: newly
 * ticked ones go last. Used from the settings and from the scan screen.
 */
@Composable
fun ReceiptLanguagesDialog(
    current: List<OcrLanguage>,
    onConfirm: (List<OcrLanguage>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_receipt_languages)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_receipt_languages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OcrLanguage.entries.forEach { language ->
                    val checked = language in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { chosen = if (it) chosen + language else chosen - language },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(language.displayName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }, enabled = chosen.isNotEmpty()) {
                Text(stringResource(R.string.add_receipt_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_cancel)) }
        },
    )
}

/** Name of the language in the app's language, e.g. "Italiano" in a Spanish UI. */
fun OcrLanguage.displayName(): String {
    val appLocale = Locale.getDefault()
    return Locale.forLanguageTag(isoCode).getDisplayLanguage(appLocale)
        .replaceFirstChar { it.titlecase(appLocale) }
}
