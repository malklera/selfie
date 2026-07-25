package com.selfie.domain.model

import androidx.compose.ui.graphics.vector.ImageVector

data class ButtonSlot(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val visible: Boolean,
    val onClick: () -> Unit
)
