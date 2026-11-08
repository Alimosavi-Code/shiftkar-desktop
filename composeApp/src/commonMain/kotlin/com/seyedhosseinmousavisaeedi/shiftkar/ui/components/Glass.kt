package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftCode
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftGroup
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.codeToKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.faLetter
import com.seyedhosseinmousavisaeedi.shiftkar.engine.shiftCodeIndex
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

/**
 * Shared Frost UI atoms (v6.5.3):
 *  - Added 2-stop gradient brushes on `KindColors` so Morning (`صبح‌کار`) and
 *    Evening (`عصرکار`) badges/cells have distinctly different gradients.
 *  - Added `ShiftDayOrdinalBadge` (elegant mini shift icon + shift day number `۱`, `۲`, etc.)
 *    for Calendar cells.
 */

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

fun Modifier.skOutline(active: Boolean, shape: RoundedCornerShape = RoundedCornerShape(16.dp)): Modifier = composed {
    val sk = LocalSk.current
    this
        .clip(shape)
        .background(if (active) sk.primarySoft else sk.surface)
        .border(2.dp, if (active) sk.primary else sk.borderSoft, shape)
}

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
            fontFamily = IRANSansX,
            brush = brush
        )
    )
}

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
                    fontFamily = IRANSansX,
                    color = if (active) sk.primary else sk.textMuted
                )
            }
        }
    }
}

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

/** Mini group badge — the group letter filled by that group's shift kind gradient. */
@Composable
fun MiniBadge(group: ShiftGroup, kind: ShiftKind) {
    val kc = kindColors(kind)
    Box(
        Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(kc.brush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            group.faLetter(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 8.sp,
            fontFamily = IRANSansX,
            color = kc.ink,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * نشان گروه + شماره روز نوبت در سلول‌های نمای «همهٔ گروه‌ها» (مثلاً A۱، B۲)
 */
@Composable
fun GroupNumBadge(group: ShiftGroup, code: ShiftCode) {
    val kind = codeToKind(code)
    val kc = kindColors(kind)
    val ord = toPersianDigits(shiftCodeIndex(code))
    Box(
        Modifier
            .size(width = 19.dp, height = 13.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(kc.brush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${group.faLetter()}$ord",
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 8.sp,
            fontFamily = IRANSansX,
            color = kc.ink,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Elegant mini icon + shift ordinal badge for Calendar cells (e.g., [☀️ ۱], [🌇 ۲], [🌙 ۱], [🛋️ ۴]):
 * Displays the vector shift icon alongside the Persian day number of that shift phase.
 */
@Composable
fun ShiftDayOrdinalBadge(
    kind: ShiftKind,
    ordinal: Int,
    isSwapped: Boolean = false
) {
    val kc = kindColors(kind)
    val numText = if (isSwapped) "*" else toPersianDigits(ordinal)
    Row(
        Modifier
            .height(19.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(kc.brush)
            .border(0.8.dp, kc.ink.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .padding(horizontal = 4.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(kindIcon(kind, 11), null, Modifier.size(11.dp), tint = kc.ink)
        Text(
            numText,
            fontSize = 9.sp,
            lineHeight = 9.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = IRANSansX,
            color = kc.ink
        )
    }
}

data class KindColors(
    val bg: Color,
    val bgEnd: Color,
    val ink: Color,
    val brush: Brush
)

@Composable
fun kindColors(kind: ShiftKind): KindColors {
    val sk = LocalSk.current
    return when (kind) {
        ShiftKind.DAY -> KindColors(
            bg = sk.daySoft,
            bgEnd = sk.daySoftEnd,
            ink = sk.dayInk,
            brush = Brush.linearGradient(listOf(sk.daySoft, sk.daySoftEnd))
        )
        ShiftKind.EVENING -> KindColors(
            bg = sk.eveningSoft,
            bgEnd = sk.eveningSoftEnd,
            ink = sk.eveningInk,
            brush = Brush.linearGradient(listOf(sk.eveningSoft, sk.eveningSoftEnd))
        )
        ShiftKind.NIGHT -> KindColors(
            bg = sk.nightSoft,
            bgEnd = sk.nightSoftEnd,
            ink = sk.nightInk,
            brush = Brush.linearGradient(listOf(sk.nightSoft, sk.nightSoftEnd))
        )
        ShiftKind.REST -> KindColors(
            bg = sk.restSoft,
            bgEnd = sk.restSoftEnd,
            ink = sk.restInk,
            brush = Brush.linearGradient(listOf(sk.restSoft, sk.restSoftEnd))
        )
    }
}

@Composable
fun kindIcon(kind: ShiftKind, size: Int = 16): ImageVector = when (kind) {
    ShiftKind.DAY -> SkIcons.Sun
    ShiftKind.EVENING -> SkIcons.Sunset
    ShiftKind.NIGHT -> SkIcons.Moon
    ShiftKind.REST -> SkIcons.Rest
}

fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    this.then(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    )
}
