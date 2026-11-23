package com.pepesantos.scontrino.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R

/** Colours of the hue bar (and of the "custom colour" circle), red to red. */
val hueColors: List<Color> = (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }

/**
 * Free colour choice: hue, saturation and brightness sliders plus a hex field, both in sync.
 * [initial] and the result are opaque ARGB longs, like the card colours.
 */
@Composable
fun ColorPickerDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, value)
    // Typed text, kept while it isn't a valid colour yet; replaced when a slider moves.
    var hex by remember { mutableStateOf(toHex(color)) }

    fun fromSliders(h: Float = hue, s: Float = saturation, v: Float = value) {
        hue = h
        saturation = s
        value = v
        hex = toHex(Color.hsv(h, s, v))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { typed ->
                            val clean = typed.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                            hex = "#$clean"
                            parseHex(clean)?.let { argb ->
                                val parts = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, parts)
                                hue = parts[0]
                                saturation = parts[1]
                                value = parts[2]
                            }
                        },
                        label = { Text(stringResource(R.string.wallet_color_hex)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                SliderLabel(stringResource(R.string.wallet_color_hue))
                GradientBar(hueColors)
                Slider(
                    value = hue,
                    onValueChange = { fromSliders(h = it) },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = Color.hsv(hue, 1f, 1f))
                )

                SliderLabel(stringResource(R.string.wallet_color_saturation))
                GradientBar(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)))
                Slider(
                    value = saturation,
                    onValueChange = { fromSliders(s = it) },
                    colors = SliderDefaults.colors(thumbColor = color)
                )

                SliderLabel(stringResource(R.string.wallet_color_brightness))
                GradientBar(listOf(Color.Black, Color.hsv(hue, saturation, 1f)))
                Slider(
                    value = value,
                    onValueChange = { fromSliders(v = it) },
                    colors = SliderDefaults.colors(thumbColor = color)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(toArgbLong(color)) }) {
                Text(stringResource(R.string.add_receipt_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.wallet_cancel)) }
        },
    )
}

@Composable
private fun SliderLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
}

/** What the slider below picks from. */
@Composable
private fun GradientBar(colors: List<Color>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(colors))
    )
}

private fun toArgbLong(color: Color): Long = colorToInt(color).toLong() and 0xFFFFFFFFL

private fun colorToInt(color: Color): Int =
    android.graphics.Color.argb(
        255,
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
    )

private fun toHex(color: Color): String = "#%06X".format(colorToInt(color) and 0xFFFFFF)

/** Opaque ARGB int of "RRGGBB", or null until all six digits are typed. */
private fun parseHex(digits: String): Int? =
    if (digits.length == 6) (0xFF000000.toInt() or digits.toInt(16)) else null
