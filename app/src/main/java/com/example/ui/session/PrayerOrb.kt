package com.example.ui.session

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun PrayerOrb(
    isGeminiSpeaking: Boolean,
    isAudioPlaying: Boolean,
    isMicSending: Boolean,
    micRmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAiActive = isGeminiSpeaking || isAudioPlaying
    val isUserActive = !isAiActive && (isMicSending || micRmsLevel > 800f)

    // Hardcoded AI Colors
    val aiCore = Color(0xFFB4574E)
    val aiSoft = Color(0xFFD98A84)
    val aiEdge = Color(0xFFA85A54)

    // Dynamic User Colors
    val fallbackUserColor = Color(0xFF4CAF50)
    val userPrimaryColor = remember(context) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                dynamicLightColorScheme(context).primary
            } catch (_: Throwable) {
                fallbackUserColor
            }
        } else {
            fallbackUserColor
        }
    }

    // Color Transitions
    val coreColor by animateColorAsState(
        targetValue = if (isUserActive) userPrimaryColor else aiCore,
        animationSpec = tween(500),
        label = "coreColor"
    )
    val softColor by animateColorAsState(
        targetValue = if (isUserActive) userPrimaryColor.copy(alpha = 0.7f) else aiSoft,
        animationSpec = tween(500),
        label = "softColor"
    )
    val edgeColor by animateColorAsState(
        targetValue = if (isUserActive) userPrimaryColor.copy(alpha = 0.9f) else aiEdge,
        animationSpec = tween(500),
        label = "edgeColor"
    )

    // Animation Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "orbAnimations")

    // AI Scale: 1.0 <-> 1.05 over 4000ms LinearEasing Reverse
    val aiScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aiScale"
    )

    // User Scale: 1.0 <-> 1.03 <-> 0.98 over 1400ms FastOutSlowIn Restart
    val userScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "userScale"
    )

    val currentScale = when {
        isUserActive -> userScale
        isAiActive -> aiScale
        else -> 1.0f
    }

    // Wave bars jitter for AI speaking mode
    val idleJitterTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idleJitter"
    )

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring 1.5dp @ 30% opacity scaling in sync
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(currentScale)
                .border(
                    width = 1.5.dp,
                    color = coreColor.copy(alpha = 0.30f),
                    shape = CircleShape
                )
        )

        // Radial-gradient Core Orb (170dp)
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(currentScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(softColor, coreColor, edgeColor)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // 5 white wave bars inside
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barMultipliers = listOf(0.5f, 0.85f, 1.0f, 0.75f, 0.45f)
                val rmsFactor = (micRmsLevel / 2500f).coerceIn(0f, 1f)

                barMultipliers.forEachIndexed { index, multiplier ->
                    val barHeightDp = when {
                        isUserActive -> (12 + (36 * rmsFactor * multiplier)).dp
                        isAiActive -> (14 + (10 * sin(idleJitterTime + index * 1.2f).coerceIn(0f, 1f) * multiplier)).dp
                        else -> 12.dp
                    }

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barHeightDp)
                            .background(
                                color = Color.White.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
