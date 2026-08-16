package ar.mimbi.Selfie.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SecretSettingsButton(
    onNavigateToConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastFirstTapTime by remember { mutableStateOf(0L) }

    LaunchedEffect(lastFirstTapTime) {
        if (lastFirstTapTime > 0) {
            delay(8000)
            tapCount = 0
            lastFirstTapTime = 0
        }
    }

    IconButton(
        onClick = {
            if (tapCount == 0) {
                lastFirstTapTime = System.currentTimeMillis()
            }
            tapCount++
            if (tapCount >= 5) {
                onNavigateToConfig()
                tapCount = 0
                lastFirstTapTime = 0
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Configuración",
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(43.dp)
        )
    }
}
