package com.BWPStudio.JITAIWizard.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SliderWithInput(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    step: Float = 1f,
    label: String
) {
    var textValue by remember(value) { mutableStateOf(value.toInt().toString()) }
    var sliderPosition by remember { mutableFloatStateOf(value) }

    // No outer padding of its own: the only caller sits inside a card that already has some.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                onValueChange(newValue)
                textValue = newValue.toInt().toString()
                sliderPosition = newValue
            },
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) / step).toInt() - 1,
            // Same reason as GameSettingsScreen: the theme's green secondary container makes an
            // empty track look like a full one.
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                // Ticks off: this one runs 1..60, and sixty dots read as noise, not as a scale.
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        TextField(
            value = textValue,
            onValueChange = { input ->
                textValue = input
                input.toFloatOrNull()?.let { floatVal ->
                    val clamped = floatVal.coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(clamped)
                    sliderPosition = clamped
                }
            },
            singleLine = true,
            modifier = Modifier.width(72.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
