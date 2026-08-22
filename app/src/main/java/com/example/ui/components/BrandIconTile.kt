package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Brand Color Tokens
private val TerracottaLight = Color(0xFFC9706A)
private val TerracottaBase = Color(0xFFB4574E)
private val TerracottaDark = Color(0xFF9E463F)
private val CreamWhite = Color(0xFFF2EFE6)
private val WarmDarkBrown = Color(0xFF2C2420)

// SVG Path Data from ic_fire_logo.xml (viewBox 188 x 255)
private const val FLAME_OUTER_PATH =
    "M187.899,164.809 C185.803,214.868 144.574,254.812 94.000,254.812 C42.085,254.812 -0.000,211.312 -0.000,160.812 C-0.000,154.062 -0.121,140.572 10.000,117.812 C16.057,104.191 19.856,95.634 22.000,87.812 C23.178,83.513 25.469,76.683 32.000,87.812 C35.851,94.374 36.000,103.812 36.000,103.812 C36.000,103.812 50.328,92.817 60.000,71.812 C74.179,41.019 62.866,22.612 59.000,9.812 C57.662,5.384 56.822,-2.574 66.000,0.812 C75.352,4.263 100.076,21.570 113.000,39.812 C131.445,65.847 138.000,90.812 138.000,90.812 C138.000,90.812 143.906,83.482 146.000,75.812 C148.365,67.151 148.400,58.573 155.999,67.813 C163.226,76.600 173.959,93.113 180.000,108.812 C190.969,137.321 187.899,164.809 187.899,164.809 Z"

private const val FLAME_INNER_PATH =
    "M94.000,254.812 C58.101,254.812 29.000,225.711 29.000,189.812 C29.000,168.151 37.729,155.000 55.896,137.166 C67.528,125.747 78.415,111.722 83.042,102.172 C83.953,100.292 86.026,90.495 94.019,101.966 C98.212,107.982 104.785,118.681 109.000,127.812 C116.266,143.555 118.000,158.812 118.000,158.812 C118.000,158.812 125.121,154.616 130.000,143.812 C131.573,140.330 134.753,127.148 143.643,140.328 C150.166,150.000 159.127,167.390 159.000,189.812 C159.000,225.711 129.898,254.812 94.000,254.812 Z"

private const val FLAME_CORE_PATH =
    "M95.000,183.812 C104.250,183.812 104.250,200.941 116.000,223.812 C123.824,239.041 112.121,254.812 95.000,254.812 C77.879,254.812 69.000,240.933 69.000,223.812 C69.000,206.692 85.750,183.812 95.000,183.812 Z"

/**
 * BrandIconTile: A glossy 3D "liquid glass terracotta lollipop" app icon tile.
 * Terracotta vertical gradient body (#C9706A -> #9E463F) with CREAM fire (#F2EFE6),
 * white top-half shine, rim light, floating warm shadow, and balloon specular highlight.
 */
@Composable
fun BrandIconTile(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    iconSize: Dp = (size.value * 0.55f).dp
) {
    val cornerRadius = size * 0.28f
    val shape = RoundedCornerShape(cornerRadius)

    // Pre-parse vector paths once
    val outerPath = remember { PathParser().parsePathString(FLAME_OUTER_PATH).toPath() }
    val innerPath = remember { PathParser().parsePathString(FLAME_INNER_PATH).toPath() }
    val corePath = remember { PathParser().parsePathString(FLAME_CORE_PATH).toPath() }

    Box(
        modifier = modifier
            .padding(bottom = 6.dp) // Room for soft shadow
            .size(size)
            .shadow(
                elevation = 11.dp,
                shape = shape,
                ambientColor = WarmDarkBrown.copy(alpha = 0.25f),
                spotColor = WarmDarkBrown.copy(alpha = 0.32f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        TerracottaLight,
                        TerracottaBase,
                        TerracottaDark
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height

            // 1. Glass Shine: white linear gradient fading to 0 by ~55% height
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.35f),
                    0.55f to Color.White.copy(alpha = 0.0f),
                    1.0f to Color.Transparent
                ),
                size = Size(canvasWidth, canvasHeight)
            )

            // 2. Micro-detail: blurred white specular highlight near top-left like light on a balloon
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(canvasWidth * 0.25f, canvasHeight * 0.22f),
                    radius = canvasWidth * 0.24f
                ),
                center = Offset(canvasWidth * 0.25f, canvasHeight * 0.22f),
                radius = canvasWidth * 0.24f
            )

            // 3. Flame Vector: CREAM fire centered at ~55% of tile size with subtle 1.5dp dark drop shadow
            val targetHeight = canvasHeight * 0.55f
            val scale = targetHeight / 255f
            val flameWidth = 188f * scale
            val flameHeight = 255f * scale
            val left = (canvasWidth - flameWidth) / 2f
            val top = (canvasHeight - flameHeight) / 2f

            // 3a. Subtle dark drop shadow (1.5dp offset down-right)
            val shadowOffset = 1.5.dp.toPx()
            translate(left = left, top = top + shadowOffset) {
                drawContext.canvas.save()
                drawContext.canvas.scale(scale, scale)
                drawPath(path = outerPath, color = WarmDarkBrown.copy(alpha = 0.30f))
                drawPath(path = innerPath, color = WarmDarkBrown.copy(alpha = 0.30f))
                drawContext.canvas.restore()
            }

            // 3b. Flame Body in CREAM #F2EFE6 with Terracotta core cutout
            translate(left = left, top = top) {
                drawContext.canvas.save()
                drawContext.canvas.scale(scale, scale)
                drawPath(path = outerPath, color = CreamWhite)
                drawPath(path = innerPath, color = CreamWhite)
                // Core cutout in rich terracotta to let glass depth show through
                drawPath(path = corePath, color = TerracottaBase)
                drawContext.canvas.restore()
            }
        }
    }
}
