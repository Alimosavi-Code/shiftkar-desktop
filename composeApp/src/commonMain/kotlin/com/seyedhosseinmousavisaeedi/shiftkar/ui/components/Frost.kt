package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seyedhosseinmousavisaeedi.shiftkar.platform.supportsBackdropBlur
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import kotlin.math.roundToInt

/**
 * FrostBarBackdrop — PWA-style Translucent Frosted Glass Bottom Navigation Bar (`sk-bottom-nav` parity):
 *  - Uses a deep 32.dp Skia blur (`Modifier.blur(32.dp)`) of the underlying Aurora + primary ambient glow.
 *  - Translucent glass gradient (`alpha = 0.62f` in dark mode, `0.68f` in light mode — matching
 *    the PWA's `--sk-nav-hi: rgba(22, 29, 52, 0.68)` / `--sk-nav-lo: rgba(15, 20, 38, 0.56)`!)
 *    instead of the old opaque `0.90f` paint that hid the blur.
 *  - Top specular highlight line + subtle outer floating shadow.
 */
@Composable
fun FrostBarBackdrop(
    auroraSizePx: IntSize,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val sk = LocalSk.current
    val dark = sk.background.luminance() < 0.5f
    val density = LocalDensity.current
    val canBlur = supportsBackdropBlur

    var myTopLeft by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier
            .shadow(
                elevation = if (dark) 24.dp else 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.65f else 0.22f),
                spotColor = sk.glowA.copy(alpha = if (dark) 0.45f else 0.25f)
            )
            .clip(shape)
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                myTopLeft = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
    ) {
        /* 1. Blurred Aurora + Ambient Glow Backdrop (32.dp Skia Gaussian Blur) */
        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
        ) {
            if (auroraSizePx != IntSize.Zero) {
                val auroraW = with(density) { auroraSizePx.width.toFloat().toDp() }
                val auroraH = with(density) { auroraSizePx.height.toFloat().toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(-myTopLeft.x, -myTopLeft.y) }
                        .size(auroraW, auroraH)
                        .then(if (canBlur) Modifier.blur(32.dp) else Modifier)
                ) {
                    AuroraBackground(Modifier.fillMaxSize())
                }
            }
        }

        /* 2. PWA-Matching Translucent Glass Tint (`--sk-nav-hi` -> `--sk-nav-lo` + Specular Top Line) */
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val navHi = if (dark) sk.sheetSolid.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.72f)
                    val navLo = if (dark) sk.background.copy(alpha = 0.54f) else Color.White.copy(alpha = 0.56f)
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(navHi, navLo),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(sk.glassHi, sk.glassLo, sk.primary.copy(alpha = if (dark) 0.08f else 0.05f)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                    // Crisp top specular reflection line (matching CSS `inset 0 1px 0 var(--sk-specular)`)
                    drawLine(
                        color = sk.specular,
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1.2f
                    )
                }
        )

        /* 3. Glass Border */
        Box(Modifier.matchParentSize().border(1.dp, sk.border, shape))

        /* 4. Sharp Foreground Content */
        Box(Modifier, content = content)
    }
}
