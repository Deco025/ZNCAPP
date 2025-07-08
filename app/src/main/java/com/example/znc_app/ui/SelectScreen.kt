package com.example.znc_app.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.znc_app.data.ConnectionState
import com.example.znc_app.ui.theme.OnSurfaceVariant
import com.example.znc_app.ui.theme.PrimaryGreen
import com.example.znc_app.ui.theme.SelectedGreen
import com.example.znc_app.viewmodel.MainViewModel
import com.example.znc_app.viewmodel.UiState

@Composable
fun SelectScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ConnectionBar(
            uiState = uiState,
            onIpSegmentChanged = viewModel::onIpSegmentChanged,
            onConnectClick = viewModel::onConnectButtonClicked,
            connectionState = uiState.connectionState
        )
        Spacer(modifier = Modifier.height(32.dp)) // 增加间距
        ButtonGrid(
            selectedButtons = uiState.selectedButtons,
            onButtonClick = { col, row ->
                viewModel.onButtonSelected(col, row)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionBar(
    uiState: UiState,
    onIpSegmentChanged: (Int, String) -> Unit,
    onConnectClick: () -> Unit,
    connectionState: ConnectionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                IpAddressInput(
                    ipSegments = uiState.ipSegments,
                    onSegmentChange = onIpSegmentChanged // 直接传递函数引用
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.delay,
                    style = MaterialTheme.typography.labelMedium, // 使用 labelMedium 样式
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用 onSurfaceVariant 颜色
                    modifier = Modifier
                        .align(Alignment.Start) // 左对齐
                        .padding(start = 8.dp)   // 左侧内边距
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Spacer(modifier = Modifier.width(12.dp))
            // 在 Button 右侧添加状态指示灯
            StatusIndicator(connectionState = uiState.connectionState)
        }
    }
}

@Composable
fun ButtonGrid(
    selectedButtons: List<Int>,
    onButtonClick: (Int, Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(16) { index ->
            val col = index % 4
            val row = index / 4 + 1
            val isSelected = selectedButtons.getOrNull(col) == row

            GridButton(
                text = "${('A' + col)}${row}",
                isSelected = isSelected,
                onClick = { onButtonClick(col, row) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryGreen else OnSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 300), label = "borderColor"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) SelectedGreen else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 300), label = "bgColor"
    )

    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StatusIndicator(connectionState: ConnectionState) {
    val color by animateColorAsState(
        targetValue = when (connectionState) {
            is ConnectionState.Connected -> PrimaryGreen
            is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(500), label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "blinking_led")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Connected) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "led_alpha"
    )

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
    )
}