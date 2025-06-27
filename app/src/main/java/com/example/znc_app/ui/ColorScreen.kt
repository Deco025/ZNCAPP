package com.example.znc_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.znc_app.viewmodel.ColorScreenState
import com.example.znc_app.viewmodel.MainViewModel

@Composable
fun ColorScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colorState = uiState.colorState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ColorSlidersSection(
            colorState = colorState,
            onSliderChange = { index, value -> viewModel.onColorSliderChanged(index, value) }
        )
        ModeSelectionSection(
            colorState = colorState,
            onModeSelect = { viewModel.onColorModeSelected(it) }
        )
        ManualInputSection(
            colorState = colorState,
            onValueChange = { viewModel.onManualColorInput(it) },
            onDecrement = { viewModel.onColorValueDecrement() },
            onIncrement = { viewModel.onColorValueIncrement() }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun ColorSlidersSection(colorState: ColorScreenState, onSliderChange: (Int, Float) -> Unit) {
    SectionCard(title = "颜色调节") {
        val sliderLabels = listOf("b*", "a*", "rec[9]")
        sliderLabels.forEachIndexed { index, label ->
            ColorSlider(
                label = label,
                value = colorState.sliders[index],
                onValueChange = { onSliderChange(index, it) }
            )
            if (index < sliderLabels.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ModeSelectionSection(colorState: ColorScreenState, onModeSelect: (Int) -> Unit) {
    SectionCard(title = "掩码画面") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf("黄", "红", "紫", "堡")
            modes.forEachIndexed { index, text ->
                val modeId = index + 1
                val isSelected = colorState.activeMode == modeId
                if (isSelected) {
                    Button(
                        onClick = { onModeSelect(modeId) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeSelect(modeId) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualInputSection(
    colorState: ColorScreenState,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    SectionCard(title = "精确调整") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onDecrement, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrement")
            }
            OutlinedTextField(
                value = colorState.manualInputValue,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Button(onClick = onIncrement, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increment")
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toInt().toString(),
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}