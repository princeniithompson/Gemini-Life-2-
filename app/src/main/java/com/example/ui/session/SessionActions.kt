package com.example.ui.session

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wake.WakePrefsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEndSession: () -> Unit,
    onOpenSettings: () -> Unit,
    isDoubleTapEnd: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isPopping by remember { mutableStateOf(false) }

    var lastTapTime by remember { mutableStateOf(0L) }
    var showHintPill by remember { mutableStateOf(false) }
    var hintJob by remember { mutableStateOf<Job?>(null) }

    val handleEndClick = {
        if (!isDoubleTapEnd) {
            onEndSession()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastTapTime <= 350L) {
                // Second tap within ~350ms window
                hintJob?.cancel()
                showHintPill = false
                lastTapTime = 0L
                val msg = "[SESSION] end: double-tap end fired"
                Log.i("WakeDetector", msg)
                WakePrefsManager.logWakeEvent(msg)
                onEndSession()
            } else {
                // First tap or tap outside 350ms window
                lastTapTime = now
                hintJob?.cancel()
                hintJob = scope.launch {
                    delay(350L)
                    // Countdown expired without a second tap (single-tap attempt failed)
                    val msg = "[SESSION] end: single-tap hint shown"
                    Log.i("WakeDetector", msg)
                    WakePrefsManager.logWakeEvent(msg)
                    showHintPill = true
                    delay(2500L) // Hold ~2.5s
                    showHintPill = false
                }
            }
        }
    }

    val heartScale by animateFloatAsState(
        targetValue = if (isPopping) 1.25f else 1.0f,
        animationSpec = tween(125),
        finishedListener = {
            if (isPopping) {
                isPopping = false
            }
        },
        label = "heartScalePop"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Frosted-glass hint pill anchored directly above End button
        AnimatedVisibility(
            visible = showHintPill,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200)),
            modifier = Modifier.offset(y = (-52).dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFDFCF8).copy(alpha = 0.75f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0x80E8E0D4))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = Color(0xFFB4574E),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Double-tap to end session",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2C2420)
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 48dp Circle Heart Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(heartScale)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE8E0D4), CircleShape)
                    .clickable {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (_: Exception) {}
                        isPopping = true
                        onToggleFavorite()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite session",
                    tint = if (isFavorite) Color(0xFFB4574E) else Color(0xFF8B7E72),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(modifier = Modifier.size(20.dp))

            // Center: "End session" Text-Only Pill
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB4574E))
                    .clickable { handleEndClick() }
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "End session",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }

            Box(modifier = Modifier.size(20.dp))

            // Right: 48dp Circle Menu Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE8E0D4), CircleShape)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Settings menu",
                    tint = Color(0xFF2C2420),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
