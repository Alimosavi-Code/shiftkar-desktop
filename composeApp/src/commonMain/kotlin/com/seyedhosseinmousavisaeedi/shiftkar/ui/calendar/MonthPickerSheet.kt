package com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkSheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics

/**
 * Month/year + Exact Day picker (v6.4.0):
 *  - Year stepper (1300–1500)
 *  - 4×3 Month grid
 *  - Exact Day jumper (1..monthLen) with live shift preview & "Open Day Details" shortcut
 */

private const val MIN_YEAR = 1300
private const val MAX_YEAR = 1500

@Composable
fun MonthPickerSheet(
    vm: AppViewModel,
    visible: Boolean,
    onOpenDay: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val today = remember { todayJalaali() }

    var year by remember(s.viewYear) { mutableIntStateOf(s.viewYear) }
    var month by remember(s.viewMonth) { mutableIntStateOf(s.viewMonth) }
    var day by remember { mutableIntStateOf(today.jd) }

    LaunchedEffect(visible, s.viewYear, s.viewMonth) {
        if (visible) {
            year = s.viewYear
            month = s.viewMonth
            val maxD = jalaaliMonthLength(year, month)
            day = today.jd.coerceIn(1, maxD)
        }
    }

    val maxDaysInMonth = jalaaliMonthLength(year, month)
    val safeDay = day.coerceIn(1, maxDaysInMonth)
    val previewKey = makeDateKey(year, month, safeDay)
    val myGroup = s.myGroup.toShiftGroup()
    val previewCode = ShiftEngine.getShiftCode(previewKey, myGroup)
    val previewKind = s.dayRecords[previewKey]?.swapKind ?: codeToKind(previewCode)
    val previewWeekday = formatWeekday(year, month, safeDay)

    SkSheet(visible = visible, onDismiss = onDismiss, title = "انتخاب تاریخ و پرش سریع") {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {

            /* ---- year stepper ---- */
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .glass(RoundedCornerShape(12.dp))
                        .clickableNoRipple(enabled = year > MIN_YEAR) { haptic.tick(); year-- },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.ChevronRight, "سال قبل", Modifier.size(17.dp), tint = if (year > MIN_YEAR) sk.textMuted else sk.textFaint.copy(alpha = 0.4f))
                }
                Text(
                    toPersianDigits(year),
                    Modifier.padding(horizontal = 16.dp).widthIn(min = 96.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 19.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .glass(RoundedCornerShape(12.dp))
                        .clickableNoRipple(enabled = year < MAX_YEAR) { haptic.tick(); year++ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.ChevronLeft, "سال بعد", Modifier.size(17.dp), tint = if (year < MAX_YEAR) sk.textMuted else sk.textFaint.copy(alpha = 0.4f))
                }
            }

            Spacer(Modifier.height(14.dp))

            /* ---- month grid (4×3) ---- */
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JALALI_MONTHS.toList().chunked(3).forEachIndexed { rowIdx, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { col, name ->
                            val idx = rowIdx * 3 + col + 1
                            val active = idx == month
                            val isNow = today.jy == year && today.jm == idx
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .glass(
                                        RoundedCornerShape(15.dp),
                                        tint = if (active) sk.primary.copy(alpha = 0.14f) else null
                                    )
                                    .border(
                                        if (active) 1.5.dp else 0.dp,
                                        if (active) sk.primary else Color.Transparent,
                                        RoundedCornerShape(15.dp)
                                    )
                                    .clickableNoRipple {
                                        haptic.tick()
                                        month = idx
                                        vm.setViewMonth(year, idx)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = if (active) sk.primary else sk.text
                                )
                                if (isNow && !active) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(sk.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            /* ---- v6.4.0: Exact Day Selector + Live Shift Preview ---- */
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "انتخاب روز برای بررسی سریع:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(sk.surfaceStrong)
                                .clickableNoRipple(enabled = safeDay > 1) {
                                    haptic.tick()
                                    day = (safeDay - 1).coerceAtLeast(1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SkIcons.ChevronRight, null, Modifier.size(14.dp), tint = sk.text)
                        }
                        Text(
                            "روز ${toPersianDigits(safeDay)}",
                            Modifier.widthIn(min = 58.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(sk.surfaceStrong)
                                .clickableNoRipple(enabled = safeDay < maxDaysInMonth) {
                                    haptic.tick()
                                    day = (safeDay + 1).coerceAtMost(maxDaysInMonth)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SkIcons.ChevronLeft, null, Modifier.size(14.dp), tint = sk.text)
                        }
                    }
                }

                val (pBg, pInk) = when (previewKind) {
                    ShiftKind.DAY -> sk.daySoft to sk.dayInk
                    ShiftKind.EVENING -> sk.eveningSoft to sk.eveningInk
                    ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
                    ShiftKind.REST -> sk.restSoft to sk.restInk
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(pBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$previewWeekday ${formatJalali(year, month, safeDay)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    Text(
                        "${GROUP_FA.getValue(myGroup)}: ${CODE_FA.getValue(previewCode)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = pInk
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(sk.surfaceStrong)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                            .clickableNoRipple {
                                haptic.tick()
                                vm.setViewMonth(year, month)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "نمایش این ماه",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.text
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(sk.primary)
                            .clickableNoRipple {
                                haptic.tick()
                                vm.setViewMonth(year, month)
                                onDismiss()
                                onOpenDay(previewKey)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "جزئیات این روز",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.sheetSolid
                        )
                    }
                }
            }
        }
    }
}
