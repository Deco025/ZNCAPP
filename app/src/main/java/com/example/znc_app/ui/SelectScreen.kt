package com.example.znc_app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.znc_app.data.ConnectionState
import com.example.znc_app.viewmodel.MainViewModel
import com.example.znc_app.viewmodel.UiState

@Composable
fun SelectScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Connection Bar
        ConnectionBar(
            uiState = uiState,
            onIpChange = { viewModel.onIpAddressChanged(it) },
            onConnectClick = { viewModel.connect() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Button Grid
        ButtonGrid(
            selectedButtons = uiState.selectedButtons,
            onButtonClick = { col, row ->
                viewModel.onButtonSelected(col, row)
            }
        )
    }
}

@Composable
fun ConnectionBar(
    uiState: UiState,
    onIpChange: (String) -> Unit,
    onConnectClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = uiState.ipAddress,
            onValueChange = onIpChange,
            label = { Text("Server IP") },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onConnectClick) {
            Text(
                when (uiState.connectionState) {
                    is ConnectionState.Connected -> "Disconnect"
                    is ConnectionState.Connecting -> "Connecting..."
                    else -> "Connect"
                }
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        StatusIndicator(connectionState = uiState.connectionState)
    }
    Text(text = uiState.delay, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun ButtonGrid(
    selectedButtons: List<Int>,
    onButtonClick: (Int, Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(16) { index ->
            val col = index % 4
            val row = index / 4 + 1 // Row is 1-based in logic
            val isSelected = selectedButtons.getOrNull(col) == row

            GridButton(
                text = "${('A' + col)}${row}",
                isSelected = isSelected,
                onClick = { onButtonClick(col, row) }
            )
        }
    }
}

@Composable
fun GridButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(if (isPressed) 0.9f else 1.0f)
    val color by animateColorAsState(
        if (isSelected) com.example.znc_app.ui.theme.ButtonGreen else MaterialTheme.colorScheme.surfaceVariant
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        interactionSource = interactionSource
    ) {
        Text(text = text, fontSize = 20.sp)
    }
}
@Composable
fun StatusIndicator(connectionState: ConnectionState) {
    val color = when (connectionState) {
        is ConnectionState.Connected -> com.example.znc_app.ui.theme.ButtonGreen
        is ConnectionState.Connecting -> Color.Yellow
        else -> com.example.znc_app.ui.theme.AccentRed
    }

    val infiniteTransition = rememberInfiniteTransition(label = "blinking_led")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ), label = "led_alpha"
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                color = if (connectionState is ConnectionState.Connected) color.copy(alpha = alpha) else color,
                shape = CircleShape
            )
            .border(1.dp, Color.White, CircleShape)
    )
}