package com.seyedhosseinmousavisaeedi.shiftkar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ThemeId
import com.seyedhosseinmousavisaeedi.shiftkar.platform.getSystemAccentColorRgb

/**
 * ShiftKar Neo — "Frost" design system tokens (v6.7.3 Desktop & Multiplatform):
 *  - Clearly differentiated Morning («صبح‌کار» — bright Canary/Solar Gold) vs
 *    Evening («عصرکار» — Sunset Coral-Crimson -> Twilight Magenta gradient)
 *  - 2-stop gradient tokens for each shift kind (`daySoft..daySoftEnd`, `eveningSoft..eveningSoftEnd`, etc.)
 *  - Native OS Dynamic Accent Color (`ThemeId.DYNAMIC`) for Windows 10/11 (DWM AccentColor),
 *    macOS, Linux KDE Plasma (`kdeglobals`), and GNOME (`accent-color`)!
 */

@Immutable
data class SkPalette(
    /* primary (theme-tinted) */
    val primary: Color,
    val primaryStrong: Color,
    val primarySoft: Color,
    val primaryInk: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    /* surfaces (mode-tinted) */
    val background: Color,
    val sheetSolid: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val border: Color,
    val borderSoft: Color,
    val glassHi: Color,
    val glassLo: Color,
    val specular: Color,
    val sheen: Color,
    val sheetBg: Color,
    /* text */
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    /* shift color language (mode-tinted + 2-stop gradients) */
    val day: Color,
    val daySoft: Color,
    val daySoftEnd: Color,
    val dayInk: Color,
    val evening: Color,
    val eveningSoft: Color,
    val eveningSoftEnd: Color,
    val eveningInk: Color,
    val night: Color,
    val nightSoft: Color,
    val nightSoftEnd: Color,
    val nightInk: Color,
    val rest: Color,
    val restSoft: Color,
    val restSoftEnd: Color,
    val restInk: Color,
    val holiday: Color,
    val holidaySoft: Color,
    val note: Color,
    val noteInk: Color,
    /* aurora */
    val auroraOpacity: Float
)

private fun c(rgb: Long, alpha: Float): Color =
    Color((rgb and 0xFFFFFF) or (((alpha * 255).toInt() and 0xFF).toLong() shl 24))
private fun c(argb: Long): Color = Color(argb)

private data class ThemePrimary(
    val primary: Long, val strong: Long, val ink: Long,
    val glowA: Pair<Long, Float>, val glowB: Pair<Long, Float>, val glowC: Pair<Long, Float>
)

private val PRIMARY_TOKENS = mapOf(
    ThemeId.DYNAMIC to ThemePrimary(0x3D6BF5, 0x2F55D4, 0x2547B8, Pair(0x3D6BF5, 0.5f), Pair(0x38BDF8, 0.35f), Pair(0x6366F1, 0.3f)),
    ThemeId.BLUE to ThemePrimary(0x3D6BF5, 0x2F55D4, 0x2547B8, Pair(0x3D6BF5, 0.5f), Pair(0x38BDF8, 0.35f), Pair(0x6366F1, 0.3f)),
    ThemeId.EMERALD to ThemePrimary(0x0F9D72, 0x0B7D5B, 0x0A6B4E, Pair(0x0F9D72, 0.5f), Pair(0xA3E635, 0.3f), Pair(0x14B8A6, 0.3f)),
    ThemeId.PURPLE to ThemePrimary(0x8B5CF6, 0x7C3AED, 0x6D28D9, Pair(0x8B5CF6, 0.5f), Pair(0x38BDF8, 0.35f), Pair(0xF472B6, 0.3f)),
    ThemeId.ORANGE to ThemePrimary(0xF46B16, 0xE05A0A, 0xC2410C, Pair(0xF46B16, 0.5f), Pair(0xFB7185, 0.4f), Pair(0xFACC15, 0.35f)),
    ThemeId.ROSE to ThemePrimary(0xF43F5E, 0xE11D48, 0xBE123C, Pair(0xF43F5E, 0.45f), Pair(0xD946EF, 0.35f), Pair(0xFB7185, 0.35f)),
    ThemeId.TEAL to ThemePrimary(0x0EA5A0, 0x0D8F8B, 0x0F766E, Pair(0x0EA5A0, 0.5f), Pair(0x38BDF8, 0.4f), Pair(0x10B981, 0.3f))
)

private fun scaleRgbChannel(rgb: Long, factor: Float): Long {
    val r = (((rgb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255).toLong()
    val g = (((rgb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255).toLong()
    val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255).toLong()
    return (r shl 16) or (g shl 8) or b
}

private fun resolveDesktopDynamicThemePrimary(dark: Boolean): ThemePrimary? {
    val sysRgb = getSystemAccentColorRgb() ?: return null
    val pRgb = if (dark) scaleRgbChannel(sysRgb, 1.12f) else sysRgb
    val sRgb = scaleRgbChannel(sysRgb, 0.85f)
    val inkRgb = if (dark) scaleRgbChannel(sysRgb, 1.25f) else scaleRgbChannel(sysRgb, 0.72f)
    return ThemePrimary(
        primary = pRgb,
        strong = sRgb,
        ink = inkRgb,
        glowA = Pair(pRgb, 0.50f),
        glowB = Pair(0x38BDF8L, 0.36f),
        glowC = Pair(sRgb, 0.32f)
    )
}

fun skPalette(theme: ThemeId, dark: Boolean): SkPalette {
    val p = if (theme == ThemeId.DYNAMIC) {
        resolveDesktopDynamicThemePrimary(dark) ?: PRIMARY_TOKENS.getValue(ThemeId.BLUE)
    } else {
        PRIMARY_TOKENS[theme] ?: PRIMARY_TOKENS.getValue(ThemeId.BLUE)
    }
    return if (dark) SkPalette(
        primary = c(p.primary or 0xFF000000),
        primaryStrong = c(p.strong or 0xFF000000),
        primarySoft = c(p.primary, 0.20f),
        primaryInk = c(p.ink or 0xFF000000),
        glowA = c(p.glowA.first, p.glowA.second),
        glowB = c(p.glowB.first, p.glowB.second),
        glowC = c(p.glowC.first, p.glowC.second),
        background = c(0xFF0A0E1F),
        sheetSolid = c(0xFF131829),
        surface = c(0xFFFFFF, 0.07f),
        surfaceStrong = c(0xFFFFFF, 0.16f),
        border = c(0xFFFFFF, 0.17f),
        borderSoft = c(0xFFFFFF, 0.07f),
        glassHi = c(0xFFFFFF, 0.135f),
        glassLo = c(0xFFFFFF, 0.05f),
        specular = c(0xFFFFFF, 0.18f),
        sheen = c(0xFFFFFF, 0.08f),
        sheetBg = c(0x0F1326, 0.92f),
        text = c(0xFFEEF0FB),
        textMuted = c(0xFFA3AACB),
        textFaint = c(0xFF6B7394),
        day = c(0xFFFACC15),
        daySoft = c(0xFDE047, 0.22f),
        daySoftEnd = c(0xEAB308, 0.12f),
        dayInk = c(0xFFFEF08A),
        evening = c(0xFFFF5722),
        eveningSoft = c(0xFF5722, 0.25f),
        eveningSoftEnd = c(0xD946EF, 0.19f),
        eveningInk = c(0xFFFF9E80),
        night = c(0xFF818CF8),
        nightSoft = c(0x818CF8, 0.22f),
        nightSoftEnd = c(0x4F46E5, 0.13f),
        nightInk = c(0xFFC7D2FE),
        rest = c(0xFF34D399),
        restSoft = c(0x34D399, 0.20f),
        restSoftEnd = c(0x059669, 0.11f),
        restInk = c(0xFF6EE7B7),
        holiday = c(0xFFFB7185),
        holidaySoft = c(0xFB7185, 0.16f),
        note = c(0xFFFFFF, 0.07f),
        noteInk = c(p.primary or 0xFF000000),
        auroraOpacity = 0.5f
    ) else SkPalette(
        primary = c(p.primary or 0xFF000000),
        primaryStrong = c(p.strong or 0xFF000000),
        primarySoft = c(p.primary, 0.14f),
        primaryInk = c(p.ink or 0xFF000000),
        glowA = c(p.glowA.first, p.glowA.second),
        glowB = c(p.glowB.first, p.glowB.second),
        glowC = c(p.glowC.first, p.glowC.second),
        background = c(0xFFEEF0FA),
        sheetSolid = c(0xFFFFFFFF),
        surface = c(0xFFFFFF, 0.62f),
        surfaceStrong = c(0xFFFFFF, 0.94f),
        border = c(0xFFFFFF, 0.75f),
        borderSoft = c(0x171326, 0.08f),
        glassHi = c(0xFFFFFF, 0.60f),
        glassLo = c(0xFFFFFF, 0.26f),
        specular = c(0xFFFFFF, 0.95f),
        sheen = c(0xFFFFFF, 0.20f),
        sheetBg = c(0xFFFFFF, 0.88f),
        text = c(0xFF191430),
        textMuted = c(0xFF5F5A7A),
        textFaint = c(0xFF8D88A8),
        day = c(0xFFEAB308),
        daySoft = c(0xFACC15, 0.24f),
        daySoftEnd = c(0xCA8A04, 0.12f),
        dayInk = c(0xFF854D0E),
        evening = c(0xFFEA580C),
        eveningSoft = c(0xFF5722, 0.20f),
        eveningSoftEnd = c(0xC026D3, 0.14f),
        eveningInk = c(0xFF9A3412),
        night = c(0xFF6366F1),
        nightSoft = c(0x6366F1, 0.18f),
        nightSoftEnd = c(0x4338CA, 0.10f),
        nightInk = c(0xFF3730A3),
        rest = c(0xFF10B981),
        restSoft = c(0x10B981, 0.18f),
        restSoftEnd = c(0x059669, 0.10f),
        restInk = c(0xFF065F46),
        holiday = c(0xFFE11D48),
        holidaySoft = c(0xE11D48, 0.12f),
        note = c(0xFFFFFF, 0.62f),
        noteInk = c(p.primary or 0xFF000000),
        auroraOpacity = 1f
    )
}
