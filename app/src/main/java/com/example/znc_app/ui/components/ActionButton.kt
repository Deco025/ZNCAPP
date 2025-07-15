package com.example.znc_app.ui.components

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun ActionButton(
    isPressed: Boolean,
    onPress: () -> Unit,
    onRelease: () +-> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { /* This is intentionally left empty, as we are handling gestures manually */ },
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Wait for a press event
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()

                    // Wait for the release or cancellation
                    waitForUpOrCancellation()
                    onRelease()
                }
            }
        }
    ) {
        Text(if (isPressed) "执行中..." else "按住执行")
    }
}