package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.seyedhosseinmousavisaeedi.shiftkar.platform.loadNoiseImageBitmap
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import kotlin.math.max

@Composable
fun rememberNoiseBitmap(): ImageBitmap? = remember { loadNoiseImageBitmap() }

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val sk = LocalSk.current
    val noise = rememberNoiseBitmap()

    Canvas(modifier.graphicsLayer { alpha = sk.auroraOpacity }) {
        val w = size.width
        val h = size.height

        fun pool(centerX: Float, centerY: Float, radiusFactor: Float, color: Color, fade: Float) {
            val vmax = max(w, h)
            val radius = vmax * radiusFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to color,
                        fade to color.copy(alpha = 0f),
                        1f to color.copy(alpha = 0f)
                    ),
                    center = Offset(centerX * w, centerY * h),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX * w, centerY * h)
            )
        }

        pool(0.88f, -0.08f, 0.72f, sk.glowA, 0.74f)
        pool(-0.14f, 0.24f, 0.60f, sk.glowB, 0.72f)
        pool(1.12f, 0.62f, 0.66f, sk.glowC, 0.74f)
        pool(0.14f, 1.08f, 0.64f, sk.glowA, 0.72f)
        pool(0.58f, 0.42f, 0.48f, sk.glowB, 0.76f)

        val vmax = max(w, h)
        val vRadius = vmax * 1.2f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.2f)
                ),
                center = Offset(w / 2f, h / 2f),
                radius = vRadius
            ),
            radius = vRadius,
            center = Offset(w / 2f, h / 2f)
        )

        if (noise != null) {
            val alpha = if (sk.auroraOpacity < 0.75f) 0.09f else 0.07f
            val tile = max(noise.width, 60)
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    drawImage(
                        image = noise,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(noise.width, noise.height),
                        dstOffset = IntOffset(x, y),
                        dstSize = IntSize(tile, tile),
                        alpha = alpha
                    )
                    x += tile
                }
                y += tile
            }
        }
    }
}
