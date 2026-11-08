package com.pepesantos.scontrino.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.data.model.Category
import com.pepesantos.scontrino.ui.theme.displayColor
import com.pepesantos.scontrino.ui.theme.displayName
import com.pepesantos.scontrino.ui.theme.icon

/** List of categories with their icon; picking one closes the dialog. */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onSelect: (categoryId: Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.category_select_title),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(categories) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(category.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = category.displayName(),
                            tint = category.displayColor()
                        )
                        Text(category.displayName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_cancel))
            }
        }
    )
}
