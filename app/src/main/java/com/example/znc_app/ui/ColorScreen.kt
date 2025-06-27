package com.example.znc_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.znc_app.viewmodel.MainViewModel

@Composable
fun ColorScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colorState = uiState.colorState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sliders
        ColorSlider(
            label = "Red",
            value = colorState.sliders[0],
            onValueChange = { viewModel.onColorSliderChanged(0, it) },
            color = Color.Red
        )
        ColorSlider(
            label = "Yellow",
            value = colorState.sliders[1],
            onValueChange = { viewModel.onColorSliderChanged(1, it) },
            color = Color.Yellow
        )
        ColorSlider(
            label = "Blue",
            value = colorState.sliders[2],
            onValueChange = { viewModel.onColorSliderChanged(2, it) },
            color = Color.Blue
        )

        // Mode Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf("红", "黄", "堡", "紫")
            modes.forEachIndexed { index, text ->
                val modeId = index + 1
                Button(
                    onClick = { viewModel.onColorModeSelected(modeId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colorState.activeMode == modeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(text)
                }
            }
        }

        // Manual Input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = colorState.manualInputValue,
                onValueChange = { viewModel.onManualColorInput(it) },
                modifier = Modifier.width(100.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = { viewModel.onColorValueDecrement() }) { Text("-") }
            Button(onClick = { viewModel.onColorValueIncrement() }) { Text("+") }
        }
    }
}

@Composable
fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.width(60.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
        Text(text = value.toInt().toString(), modifier = Modifier.width(40.dp))
    }
}