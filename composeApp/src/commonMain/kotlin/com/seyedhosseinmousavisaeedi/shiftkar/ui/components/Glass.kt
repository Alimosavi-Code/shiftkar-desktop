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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.JalaliDate
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftCode
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftGroup
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.codeToKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.faLetter
import com.seyedhosseinmousavisaeedi.shiftkar.engine.shiftCodeIndex
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.engine.todayJalaali
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import kotlinx.coroutines.delay

/**
 * Shared Frost UI atoms & primitives (v6.7.3 Desktop & Multiplatform):
 *  - Glass surfaces (`glass`, `glassHero`, `glassStrong`, `cardSoft`, `skTile`, `skOutline`)
 *  - Typography & controls (`GradientText`, `SkIconButton`, `GlassButton`, `SegmentedControl`, `SkSwitch`, `SectionTitle`)
 *  - Shift badges (`CheckBadge`, `MiniBadge`, `GroupNumBadge`, `ShiftDayOrderBadge`, `kindColors`, `kindIcon`)
 *  - Desktop hover & midnight auto-refresh (`clickableNoRipple`, `rememberLiveToday`)
 */

fun Modifier.glass(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    tint: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val sk = LocalSk.current
    val fillBrush = remember(sk.glassHi, sk.glassLo) {
        Brush.verticalGradient(listOf(sk.glassHi, sk.glassLo))
    }
    this
        .clip(shape)
        .background(fillBrush)
        .let { m -> if (tint != null) m.background(tint) else m }
        .drawBehind {
            drawLine(
                color = sk.specular,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1.dp.toPx()
            )
        }
        .border(borderWidth, borderColor ?: sk.border, shape)
}

fun Modifier.glassHero(
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    tintBrush: Brush? = null
): Modifier = composed {
    val sk = LocalSk.current
    val base = remember(sk.glassHi, sk.glassLo) {
        Brush.linearGradient(
            colors = listOf(sk.glassHi, sk.glassLo),
            start = Offset.Zero,
            end = Offset(900f, 900f)
        )
    }
    this
        .clip(shape)
        .background(base)
        .let { m -> if (tintBrush != null) m.background(tintBrush) else m }
        .border(1.dp, sk.border, shape)
        .drawBehind {
            drawLine(
                color = sk.specular,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1.2.dp.toPx()
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(sk.sheen, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.46f
                )
            )
        }
}

fun Modifier.glassStrong(
    shape: RoundedCornerShape = RoundedCornerShape(22.dp)
): Modifier = composed {
    val sk = LocalSk.current
    val fillBrush = remember(sk.glassHi, sk.glassLo) {
        Brush.verticalGradient(
            listOf(
                sk.glassHi.copy(alpha = (sk.glassHi.alpha * 1.25f).coerceAtMost(0.95f)),
                sk.glassLo.copy(alpha = (sk.glassLo.alpha * 1.35f).coerceAtMost(0.90f))
            )
        )
    }
    this
        .clip(shape)
        .background(fillBrush)
        .drawBehind {
            drawLine(
                color = sk.specular,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1.dp.toPx()
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
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            fontFamily = IRANSansX,
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

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val sk = LocalSk.current
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (active) sk.primarySoft else sk.surface)
            .border(1.dp, if (active) sk.primary else sk.borderSoft, shape)
            .clickableNoRipple(onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
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
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(sk.surface)
            .border(1.dp, sk.borderSoft, RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
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
                    color = if (active) sk.primary else sk.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val sk = LocalSk.current
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(100))
            .background(if (checked) sk.primary else sk.surface)
            .border(1.dp, if (checked) sk.primary else sk.borderSoft, RoundedCornerShape(100))
            .clickableNoRipple { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val sk = LocalSk.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(sk.primary)
        )
        Text(
            text = text,
            style = TextStyle(
                fontFamily = IRANSansX,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                color = sk.textMuted
            )
        )
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

/** Mini group badge — the group letter filled by that group's shift kind gradient. */
@Composable
fun MiniBadge(group: ShiftGroup, kind: ShiftKind) {
    val kc = kindColors(kind)
    Box(
        Modifier
            .size(13.dp)
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
 * Group + ordinal badge inside "All Groups" calendar cells (`A1`, `B2`, `C4`, `D1`).
 */
@Composable
fun GroupNumBadge(group: ShiftGroup, code: ShiftCode) {
    val kind = codeToKind(code)
    val kc = kindColors(kind)
    val ord = toPersianDigits(shiftCodeIndex(code))
    Box(
        Modifier
            .size(width = 25.dp, height = 16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(kc.brush)
            .border(0.6.dp, kc.ink.copy(alpha = 0.28f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${group.faLetter()}$ord",
            fontSize = 8.8.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 9.5.sp,
            fontFamily = IRANSansX,
            color = kc.ink,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ShiftDayOrderBadge(
    code: ShiftCode,
    modifier: Modifier = Modifier
) {
    val kind = codeToKind(code)
    val kc = kindColors(kind)
    val icon = kindIcon(kind)
    val numText = toPersianDigits(shiftCodeIndex(code))

    Row(
        modifier = modifier
            .height(17.dp)
            .clip(RoundedCornerShape(100))
            .background(kc.brush)
            .border(0.75.dp, kc.ink.copy(alpha = 0.28f), RoundedCornerShape(100))
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = kc.ink
        )
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
            .height(22.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(kc.brush)
            .border(0.8.dp, kc.ink.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(kindIcon(kind, 13), null, Modifier.size(13.dp), tint = kc.ink)
        Text(
            numText,
            fontSize = 10.5.sp,
            lineHeight = 11.sp,
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

/**
 * Live Jalali date state that automatically refreshes across midnight so long-running
 * desktop windows never freeze on yesterday's date/shift.
 */
@Composable
fun rememberLiveToday(): JalaliDate {
    val state = produceState(initialValue = todayJalaali()) {
        while (true) {
            delay(30_000L)
            val now = todayJalaali()
            if (now != value) value = now
        }
    }
    return state.value
}
