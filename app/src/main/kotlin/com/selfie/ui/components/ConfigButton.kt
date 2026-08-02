package com.selfie.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Floating settings icon button with 80% transparency (alpha 0.2).
 * Used in MainScreen and CaptureScreen result state.
 */
@Composable
fun ConfigButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .alpha(0.2f)
            .size(56.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Configuración",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}
