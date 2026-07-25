package com.selfie.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selfie.domain.model.ButtonSlot

@Composable
fun FloatingButtonRow(
    buttons: List<ButtonSlot>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        buttons.filter { it.visible }.forEach { button ->
            ExtendedFloatingActionButton(
                onClick = button.onClick,
                icon = {
                    Icon(
                        imageVector = button.icon,
                        contentDescription = button.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                text = { Text(button.label) }
            )
        }
    }
}
