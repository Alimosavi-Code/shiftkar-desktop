package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftGroup
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.faLetter
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

/**
 * Shared Frost UI atoms — glass surfaces, tiles, chips, badges,
 * segmented control, outlined option cards. Each modifier mirrors the
 * exact CSS recipe from globals.css.
 */

/** `.glass` — translucent gradient fill + border + specular top edge.
 * v2.2.4: optional `tint` draws OVER the gradient (note-yellow fields keep
 * their identity while turning glassy — «make it glassy like the rest»). */
fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(16.dp), tint: Color? = null): Modifier = composed {
    val sk = LocalSk.current
    this
        .clip(shape)
        .drawBehind {
            drawRect(Brush.linearGradient(listOf(sk.glassHi, sk.glassLo), start = Offset.Zero, end = Offset(0.55f, 1f)))
            tint?.let { drawRect(it) }
            drawLine(
                color = sk.specular,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1f
            )
        }
        .border(1.dp, sk.border, shape)
}

/** `.card-soft` — same fill without any blur layer (large scrolling surfaces). */
fun Modifier.cardSoft(shape: RoundedCornerShape = RoundedCornerShape(16.dp)): Modifier = composed {
    val sk = LocalSk.current
    this
        .clip(shape)
        .background(Brush.linearGradient(listOf(sk.glassHi, sk.glassLo), start = Offset.Zero, end = Offset(0.55f, 1f)))
        .border(1.dp, sk.border, shape)
        .drawBehind {
            drawLine(
                color = sk.specular,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1f
            )
        }
}

/** `.sk-tile` — frosted day cell (radial sheen at top + glass fill). */
fun Modifier.skTile(shape: RoundedCornerShape = RoundedCornerShape(12.dp)): Modifier = composed {
    val sk = LocalSk.current
    this
        .clip(shape)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(sk.sheen, Color.Transparent), startY = 0f, endY = size.height * 0.6f))
            drawRect(Brush.linearGradient(listOf(sk.glassHi, sk.glassLo), start = Offset.Zero, end = Offset(0.55f, 1f)))
            drawLine(
                color = sk.specular,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1f
            )
        }
        .border(1.dp, sk.border, shape)
}

/** `.sk-outline` — settings signature 2px outlined option card. */
fun Modifier.skOutline(active: Boolean, shape: RoundedCornerShape = RoundedCornerShape(16.dp)): Modifier = composed {
    val sk = LocalSk.current
    this
        .clip(shape)
        .background(if (active) sk.primarySoft else sk.surface)
        .border(2.dp, if (active) sk.primary else sk.borderSoft, shape)
}

/** Text gradient — `.text-gradient` (primary → glowB 55% → glowA). */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 16,
    fontWeight: FontWeight = FontWeight.ExtraBold
) {
    val sk = LocalSk.current
    val brush = remember(sk) {
        Brush.linearGradient(listOf(sk.primary, sk.glowB.copy(alpha = 1f), sk.glowA.copy(alpha = 1f)))
    }
    Text(
        text,
        modifier,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            fontFamily = com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX,
            brush = brush
        )
    )
}

/** `.sk-sheet-btn` — the shared 38–40dp icon button of the chrome rows. */
@Composable
fun SkIconButton(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    iconSize: Int = 18,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val sk = LocalSk.current
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(sk.surface)
            .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(iconSize.dp), tint = tint ?: sk.textMuted)
    }
}

/** `.sk-seg` segmented control (settings group picker) — v2.2.1: rounder pile. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val sk = LocalSk.current
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(sk.surface)
            .border(1.dp, sk.borderSoft, RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (active) sk.surfaceStrong else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (active) sk.primary else Color.Transparent,
                        shape = RoundedCornerShape(15.dp)
                    )
                    .clickableNoRipple { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX,
                    color = if (active) sk.primary else sk.textMuted
                )
            }
        }
    }
}

/** `.sk-check-badge` — the 18dp circular check of outlined pickers. */
@Composable
fun CheckBadge(modifier: Modifier = Modifier) {
    val sk = LocalSk.current
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(sk.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(SkIcons.Check, null, Modifier.size(11.dp), tint = Color.White)
    }
}

/** Mini group badge — the Persian letter filled by that group's shift kind. */
@Composable
fun MiniBadge(group: ShiftGroup, kind: ShiftKind) {
    val sk = LocalSk.current
    val (bg, ink) = when (kind) {
        ShiftKind.DAY -> sk.daySoft to sk.dayInk
        ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
        ShiftKind.REST -> sk.restSoft to sk.restInk
    }
    Box(
        Modifier
            .size(13.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            group.faLetter(), // v2.2.4: حرف فارسی
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 8.sp,
            fontFamily = com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX,
            color = ink,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * نشان گروه در سلول‌های نمای «همهٔ گروه‌ها» — v2.2.4: حرف فارسی (آ/ب/ج/د)
 * با رنگ وضعیت شیفت آن گروه (مالک: «Instead of group a wrote شیفت آ»). چیدمان ۲×۲ داخل سلول دست‌نخورده.
 */
@Composable
fun GroupNumBadge(group: ShiftGroup, code: com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftCode) {
    val sk = LocalSk.current
    val kind = com.seyedhosseinmousavisaeedi.shiftkar.engine.codeToKind(code)
    val (bg, ink) = when (kind) {
        ShiftKind.DAY -> sk.daySoft to sk.dayInk
        ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
        ShiftKind.REST -> sk.restSoft to sk.restInk
    }
    Box(
        Modifier
            .size(width = 17.dp, height = 13.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            group.faLetter(), // v2.2.4: حرف فارسی
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 8.5.sp,
            fontFamily = com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX,
            color = ink,
            textAlign = TextAlign.Center
        )
    }
}

/** Kind chip colors — DAY/NIGHT/REST soft+ink pairs. */
data class KindColors(val bg: Color, val ink: Color)

@Composable
fun kindColors(kind: ShiftKind): KindColors {
    val sk = LocalSk.current
    return when (kind) {
        ShiftKind.DAY -> KindColors(sk.daySoft, sk.dayInk)
        ShiftKind.NIGHT -> KindColors(sk.nightSoft, sk.nightInk)
        ShiftKind.REST -> KindColors(sk.restSoft, sk.restInk)
    }
}

@Composable
fun kindIcon(kind: ShiftKind, size: Int = 16): ImageVector = when (kind) {
    ShiftKind.DAY -> SkIcons.Sun
    ShiftKind.NIGHT -> SkIcons.Moon
    ShiftKind.REST -> SkIcons.Rest
}

/** Clickable without ripple (the PWA's tap-highlight-free feel). */
fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    this.then(
        if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
    ).then(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    )
}
