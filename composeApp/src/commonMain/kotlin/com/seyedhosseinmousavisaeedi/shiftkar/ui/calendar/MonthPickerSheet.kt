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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.JALALI_MONTHS
import com.seyedhosseinmousavisaeedi.shiftkar.engine.todayJalaali
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkSheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits

/**
 * Month/year picker — port of month-picker.tsx: a bottom sheet with a year
 * stepper (‹ year ›, years 1300–1500 guarded) and a 4×3 month grid.
 */

private const val MIN_YEAR = 1300
private const val MAX_YEAR = 1500

@Composable
fun MonthPickerSheet(
    vm: AppViewModel,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val today = remember { todayJalaali() }

    var year by remember { mutableIntStateOf(s.viewYear) }

    /* v2.2.3: `visible` flag → SkSheet owns the open/close slide animation;
     * the year state survives through the exit animation (no flash). */
    SkSheet(visible = visible, onDismiss = onDismiss, title = "انتخاب ماه و سال") {
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
                        .glass(RoundedCornerShape(12.dp)) /* v2.2.4: شیشه‌ای */
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
                        .glass(RoundedCornerShape(12.dp)) /* v2.2.4: شیشه‌ای */
                        .clickableNoRipple(enabled = year < MAX_YEAR) { haptic.tick(); year++ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.ChevronLeft, "سال بعد", Modifier.size(17.dp), tint = if (year < MAX_YEAR) sk.textMuted else sk.textFaint.copy(alpha = 0.4f))
                }
            }

            Spacer(Modifier.height(16.dp))

            /* ---- month grid (4×3) ---- */
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JALALI_MONTHS.toList().chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { col, name ->
                            val idx = rowIndex * 3 + col + 1
                            val active = idx == s.viewMonth && year == s.viewYear
                            val isNow = today.jy == year && today.jm == idx
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    /* v2.2.4: خانه‌های ماه شیشه‌ای — ماهِ فعال با هاله و حلقهٔ اصلی */
                                    .glass(
                                        RoundedCornerShape(16.dp),
                                        tint = if (active) sk.primary.copy(alpha = 0.12f) else null
                                    )
                                    .border(
                                        if (active) 1.5.dp else 0.dp,
                                        if (active) sk.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickableNoRipple {
                                        haptic.tick()
                                        vm.setViewMonth(year, idx)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = if (active) sk.primary else sk.text)
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

            Text(
                "پیمایش سال نامحدود است — همهٔ سال‌ها ۱۳۰۰ تا ۱۵۰۰ در دسترس‌اند",
                Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = TextAlign.Center,
                fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint
            )
        }
    }
}
