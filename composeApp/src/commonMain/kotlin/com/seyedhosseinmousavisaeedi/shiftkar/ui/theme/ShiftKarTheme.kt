package com.seyedhosseinmousavisaeedi.shiftkar.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ThemeId
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ThemeMode
import com.seyedhosseinmousavisaeedi.shiftkar.platform.isSystemDarkMode
import kotlinx.coroutines.delay

@Immutable
data class SkColors(val palette: SkPalette)

val LocalSkColors = staticCompositionLocalOf<SkColors> {
    SkColors(skPalette(ThemeId.BLUE, dark = false))
}

val LocalSk = staticCompositionLocalOf { skPalette(ThemeId.BLUE, dark = false) }

private fun m3(p: SkPalette) = if (p.background.luminanceCompat() < 0.5f) darkColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    secondary = p.primary,
    background = p.background,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.glassHi,
    onSurfaceVariant = p.textMuted,
    outline = p.borderSoft,
    error = p.holiday
) else lightColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    secondary = p.primary,
    background = p.background,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.glassHi,
    onSurfaceVariant = p.textMuted,
    outline = p.borderSoft,
    error = p.holiday
)

private fun Color.luminanceCompat(): Float {
    val r = (toArgb() shr 16 and 0xFF) / 255f
    val g = (toArgb() shr 8 and 0xFF) / 255f
    val b = (toArgb() and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

@Composable
fun effectiveDark(mode: ThemeMode): Boolean {
    var sysDark by remember { mutableStateOf(isSystemDarkMode()) }
    LaunchedEffect(mode) {
        if (mode == ThemeMode.SYSTEM) {
            while (true) {
                val probed = isSystemDarkMode()
                if (probed != sysDark) sysDark = probed
                delay(2_500L)
            }
        }
    }
    return when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> sysDark
    }
}

@Composable
fun ShiftKarTheme(
    theme: ThemeId,
    dark: Boolean,
    content: @Composable () -> Unit
) {
    val palette = skPalette(theme, dark)
    CompositionLocalProvider(
        LocalSk provides palette,
        LocalSkColors provides SkColors(palette),
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = m3(palette),
            typography = ShiftKarTypography(),
            content = content
        )
    }
}

@Composable
fun SkSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sk = LocalSk.current
    Surface(modifier.fillMaxSize(), color = sk.background) {
        Box {
            content()
        }
    }
}
