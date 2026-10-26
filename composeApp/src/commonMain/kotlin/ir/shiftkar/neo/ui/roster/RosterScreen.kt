package ir.shiftkar.neo.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import ir.shiftkar.neo.engine.*
import ir.shiftkar.neo.ui.AppViewModel
import ir.shiftkar.neo.ui.components.SkIcons
import ir.shiftkar.neo.ui.components.clickableNoRipple
import ir.shiftkar.neo.ui.components.glass
import ir.shiftkar.neo.ui.theme.IRANSansX
import ir.shiftkar.neo.ui.theme.LocalSk
import ir.shiftkar.neo.util.rememberHaptics

/**
 * Roster view — the لوحه (roster-view.tsx port): the WHOLE YEAR always
 * visible as a compact 2-column grid of 12 month blocks. Cells are tinted
 * by the user's own shift group, official holidays are red, today is ringed.
 * Year navigation: ‹ year › stepper at the top.
 */

private val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

@Composable
fun RosterScreen(vm: AppViewModel) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val today = remember { todayJalaali() }
    val group = s.myGroup.toShiftGroup()

    var year by remember(s.viewYear) { mutableIntStateOf(s.viewYear) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("لوحه سالانه", fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
            Spacer(Modifier.weight(1f))
            // year stepper
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(sk.surface)
                    .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                    .clickableNoRipple { haptic.tick(); year -= 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.ChevronRight, "سال قبل", Modifier.size(15.dp), tint = sk.textMuted)
            }
            Text(
                toPersianDigits(year),
                Modifier.padding(horizontal = 12.dp).widthIn(min = 72.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(sk.surface)
                    .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                    .clickableNoRipple { haptic.tick(); year += 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.ChevronLeft, "سال بعد", Modifier.size(15.dp), tint = sk.textMuted)
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 108.dp)
        ) {
            items(12) { idx ->
                val jm = idx + 1
                MonthBlock(year, jm, today, group)
            }
        }
    }
}

@Composable
private fun MonthBlock(jy: Int, jm: Int, today: JalaliDate, group: ShiftGroup) {
    val sk = LocalSk.current
    val len = remember(jy, jm) { jalaaliMonthLength(jy, jm) }
    val firstWeekday = remember(jy, jm) { jalaaliWeekday(jy, jm, 1) }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        Text(
            "${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IRANSansX, color = sk.text
        )
        Spacer(Modifier.height(4.dp))
        // weekday header
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEachIndexed { i, w ->
                Text(
                    w, Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                    color = if (i == 6) sk.holiday else sk.textFaint
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        val cells: List<Int?> = List(firstWeekday) { null } + (1..len).toList()
        val rows = (cells.size + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until 7) {
                        val d = cells.getOrNull(r * 7 + c)
                        if (d == null) {
                            Box(Modifier.weight(1f).height(17.dp))
                        } else {
                            val key = makeDateKey(jy, jm, d)
                            val code = ShiftEngine.getShiftCode(key, group)
                            val kind = codeToKind(code)
                            val isHoliday = Holidays.isHoliday(jy, jm, d)
                            val isFriday = jalaaliWeekday(jy, jm, d) == 6
                            val isToday = today.jy == jy && today.jm == jm && today.jd == d
                            val bg = when {
                                isHoliday || isFriday -> sk.holiday.copy(alpha = 0.18f)
                                else -> when (kind) {
                                    ShiftKind.DAY -> sk.daySoft
                                    ShiftKind.NIGHT -> sk.nightSoft
                                    ShiftKind.REST -> sk.restSoft
                                }
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(17.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bg)
                                    .then(if (isToday) Modifier.border(1.5.dp, sk.primary, RoundedCornerShape(4.dp)) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    toPersianDigits(d),
                                    fontSize = 7.5.sp,
                                    lineHeight = 8.sp,
                                    fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = if (isHoliday || isFriday) sk.holiday else sk.text
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
