package com.seyedhosseinmousavisaeedi.shiftkar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ThemeId

/**
 * ShiftKar Neo — "Frost" design system tokens.
 * 1:1 transcription of the PWA's globals.css custom properties
 * (light + dark + the six ShiftKar palettes).
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
    /* shift color language (mode-tinted) */
    val day: Color,
    val daySoft: Color,
    val dayInk: Color,
    val night: Color,
    val nightSoft: Color,
    val nightInk: Color,
    val rest: Color,
    val restSoft: Color,
    val restInk: Color,
    val holiday: Color,
    val holidaySoft: Color,
    val note: Color,
    val noteInk: Color,
    /* aurora */
    val auroraOpacity: Float
)

/* Compose's Color(ULong) is the RAW PACKED constructor: bits 0..5 = color-space
 * id, bits 32..63 = ARGB. Feeding a plain 0xRRGGBBAA there makes Compose read
 * the RGB bytes as the space id → ArrayIndexOutOfBounds on the FIRST frame
 * (e.g. 0xFFEEF0FA → id 58 → crash). Always build colors via Color(Long),
 * which packs ARGB into the high bits with the SRGB id. */
private fun c(rgb: Long, alpha: Float): Color =
    Color((rgb and 0xFFFFFF) or (((alpha * 255).toInt() and 0xFF).toLong() shl 24))
private fun c(argb: Long): Color = Color(argb)

/** Per-theme primary tokens — the original ShiftKar six. */
private data class ThemePrimary(
    val primary: Long, val strong: Long, val ink: Long,
    val glowA: Pair<Long, Float>, val glowB: Pair<Long, Float>, val glowC: Pair<Long, Float>
)

private val PRIMARY_TOKENS = mapOf(
    ThemeId.BLUE to ThemePrimary(0x3D6BF5, 0x2F55D4, 0x2547B8, Pair(0x3D6BF5, 0.5f), Pair(0x38BDF8, 0.35f), Pair(0x6366F1, 0.3f)),
    ThemeId.EMERALD to ThemePrimary(0x0F9D72, 0x0B7D5B, 0x0A6B4E, Pair(0x0F9D72, 0.5f), Pair(0xA3E635, 0.3f), Pair(0x14B8A6, 0.3f)),
    ThemeId.PURPLE to ThemePrimary(0x8B5CF6, 0x7C3AED, 0x6D28D9, Pair(0x8B5CF6, 0.5f), Pair(0x38BDF8, 0.35f), Pair(0xF472B6, 0.3f)),
    ThemeId.ORANGE to ThemePrimary(0xF46B16, 0xE05A0A, 0xC2410C, Pair(0xF46B16, 0.5f), Pair(0xFB7185, 0.4f), Pair(0xFACC15, 0.35f)),
    ThemeId.ROSE to ThemePrimary(0xF43F5E, 0xE11D48, 0xBE123C, Pair(0xF43F5E, 0.45f), Pair(0xD946EF, 0.35f), Pair(0xFB7185, 0.35f)),
    ThemeId.TEAL to ThemePrimary(0x0EA5A0, 0x0D8F8B, 0x0F766E, Pair(0x0EA5A0, 0.5f), Pair(0x38BDF8, 0.4f), Pair(0x10B981, 0.3f))
)

fun skPalette(theme: ThemeId, dark: Boolean): SkPalette {
    val p = PRIMARY_TOKENS.getValue(theme)
    return if (dark) SkPalette(
        primary = c(p.primary or 0xFF000000),
        primaryStrong = c(p.strong or 0xFF000000),
        primarySoft = c(p.primary, 0.20f),      // dark hover/wash recipe: 20% (14% reads muddy on navy)
        primaryInk = c(0xC4B5FD),                // dark accent-foreground
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
        day = c(0xFFFBBF24), daySoft = c(0xFBBF24, 0.14f), dayInk = c(0xFFFCD34D),
        night = c(0xFF818CF8), nightSoft = c(0x818CF8, 0.15f), nightInk = c(0xFFC7D2FE),
        rest = c(0xFF34D399), restSoft = c(0x34D399, 0.13f), restInk = c(0xFF6EE7B7),
        holiday = c(0xFFFB7185), holidaySoft = c(0xFB7185, 0.13f),
        note = c(0xFDE047, 0.10f), noteInk = c(0xFFFDE68A),
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
        day = c(0xFFD97706), daySoft = c(0xD97706, 0.13f), dayInk = c(0xFF92400E),
        night = c(0xFF6366F1), nightSoft = c(0x6366F1, 0.13f), nightInk = c(0xFF4338CA),
        rest = c(0xFF10B981), restSoft = c(0x10B981, 0.13f), restInk = c(0xFF047857),
        holiday = c(0xFFE11D48), holidaySoft = c(0xE11D48, 0.10f),
        note = c(0xFACC15, 0.14f), noteInk = c(0xFF854D0E),
        auroraOpacity = 1f     // PWA light mode: no .aurora-bg opacity rule
    )
}
