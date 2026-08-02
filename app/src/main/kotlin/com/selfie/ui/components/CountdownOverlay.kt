package com.selfie.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Large centered countdown number overlay with 90% transparency (alpha 0.1).
 */
@Composable
fun CountdownOverlay(
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = remainingSeconds.toString(),
            color = Color.White,
            fontSize = 200.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(0.1f)
        )
    }
}
