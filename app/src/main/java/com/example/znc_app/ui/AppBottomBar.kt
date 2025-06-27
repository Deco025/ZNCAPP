package com.example.znc_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.znc_app.data.ConnectionState
import com.example.znc_app.viewmodel.UiState

@Composable
fun AppBottomBar(
    uiState: UiState,
    onConnectClick: () -> Unit,
    onNavigate: (Int) -> Unit,
    currentPage: Int
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Navigation Part
            NavigationBar(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentPage == 0,
                    onClick = { onNavigate(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "选择") },
                    label = { Text("选择") }
                )
                NavigationBarItem(
                    selected = currentPage == 1,
                    onClick = { onNavigate(1) },
                    icon = { Icon(Icons.Default.ColorLens, contentDescription = "参数") },
                    label = { Text("参数") }
                )
            }

            // Spacer and Divider
            Spacer(modifier = Modifier.width(8.dp))
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Connection Part
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusIndicator(connectionState = uiState.connectionState)
                Button(
                    onClick = onConnectClick,
                    enabled = !uiState.isBusy,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = when (uiState.connectionState) {
                            is ConnectionState.Connected -> "断开"
                            is ConnectionState.Connecting -> "连接中..."
                            else -> "连接"
                        }
                    )
                }
            }
        }
    }
}