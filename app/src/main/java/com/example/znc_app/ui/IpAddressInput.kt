package com.example.znc_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.znc_app.ui.theme.BrightBlue

@Composable
fun IpAddressInput(
    ipSegments: List<String>,
    onSegmentChange: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember { List(4) { FocusRequester() } }

    Row(
        modifier = modifier.width(IntrinsicSize.Max), // Ensure the Row has a bounded width
        verticalAlignment = Alignment.CenterVertically
    ) {
        ipSegments.forEachIndexed { index, segment ->
            BasicTextField(
                value = segment,
                onValueChange = { newValue ->
                    if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                        onSegmentChange(index, newValue)
                        if (newValue.length == 3 && index < 3) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .weight(1f) // Use weight to distribute space
                    .defaultMinSize(minWidth = 40.dp), // Ensure a minimum size
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = BrightBlue,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        innerTextField()
                        Spacer(modifier = Modifier.height(2.dp))
                        Divider(color = Color.White, thickness = 1.dp)
                    }
                }
            )

            if (index < 3) {
                Text(
                    text = ".",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp) // Reduced padding
                )
            }
        }
    }
}