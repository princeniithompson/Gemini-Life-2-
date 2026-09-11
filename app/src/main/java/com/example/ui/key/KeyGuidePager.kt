package com.example.ui.key

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun KeyGuidePager(
    modifier: Modifier = Modifier,
    onSuccess: () -> Unit = {},
    onSkip: () -> Unit = {}
) {
    KeySetupContent(
        onSuccess = onSuccess,
        onSkip = onSkip,
        modifier = modifier
    )
}

