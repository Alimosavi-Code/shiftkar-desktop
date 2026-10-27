package com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics

/**
 * Day sheet — port of day-sheet.tsx: 3-calendar header
 * (Jalali+Hijri+Gregorian), holiday/occasions collapsible chip,
 * all four groups' shifts (code + ordinal, «شما» marker) and the
 * personal note editor (Room-backed).
 *
 * v2.2.3: stays composed with a `visible` flag so SkSheet can run its
 * slide-up/down open+close animations; the LAST opened day keeps rendering
 * through the exit animation (no blank flash while the sheet slides out).
 */

@Composable
fun DaySheet(
    vm: AppViewModel,
    dateKey: String?,
    onDismiss: () -> Unit
) {
    var lastKey by remember { mutableStateOf<String?>(null) }
    if (dateKey != null) lastKey = dateKey

    SkSheet(visible = dateKey != null, onDismiss = onDismiss, title = "جزئیات روز") {
        lastKey?.let { key -> DaySheetBody(vm, key) }
    }
}

@Composable
private fun DaySheetBody(
    vm: AppViewModel,
    dateKey: String
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)

    var note by remember(dateKey) { mutableStateOf("") }
    var saved by remember(dateKey) { mutableStateOf(false) }
    var eventsOpen by remember(dateKey) { mutableStateOf(false) }

    LaunchedEffect(dateKey) {
        note = vm.notesRepo.get(dateKey)?.text ?: ""
        saved = false
    }

    val j = parseDateKey(dateKey)
    val weekday = formatWeekday(j.jy, j.jm, j.jd)
    val g = toGregorian(j.jy, j.jm, j.jd)
    val hijri = jdnToIslamic(gregorianToJdn(g.gy, g.gm, g.gd))
    val today = todayJalaali()
    val isTodayDate = today.jy == j.jy && today.jm == j.jm && today.jd == j.jd
    val events = Holidays.getDayEvents(j.jy, j.jm, j.jd)
    val holidays = events.filter { it.isHoliday }
    val occasions = events.filter { !it.isHoliday }
    val effGroup = s.myGroup.toShiftGroup()
    val mine = ShiftEngine.calculateShift(dateKey, effGroup)
    val mineKind = codeToKind(mine.code)
    val mineKc = kindColors(mineKind)

    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {

            /* ---- header: 3 calendars + my shift ---- */
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(SkIcons.Calendar, null, Modifier.size(13.dp), tint = sk.primary)
                        Text(weekday, fontSize = 13.sp, fontFamily = IRANSansX, color = sk.textMuted)
                        if (isTodayDate) {
                            Text(
                                "امروز",
                                Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(sk.primarySoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.primary
                            )
                        }
                    }
                    Text(
                        formatJalali(j.jy, j.jm, j.jd),
                        Modifier.padding(top = 2.dp),
                        fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                    )
                    Text(
                        "${toPersianDigits(hijri.hd)} ${com.seyedhosseinmousavisaeedi.shiftkar.engine.HIJRI_MONTHS[hijri.hm - 1]} ${toPersianDigits(hijri.hy)} قمری · ${formatGregorian(g.gy, g.gm, g.gd)} میلادی",
                        Modifier.padding(top = 4.dp),
                        fontSize = 11.5.sp, lineHeight = 20.sp, fontFamily = IRANSansX, color = sk.textFaint
                    )
                }
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(mineKc.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(kindIcon(mineKind), CODE_FA.getValue(mine.code), Modifier.size(22.dp), tint = mineKc.ink)
                }
            }

            /* ---- holiday / occasions collapsible chip ---- */
            if (holidays.isNotEmpty() || occasions.isNotEmpty()) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    val isHolidayDay = holidays.isNotEmpty()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (isHolidayDay) Modifier
                                    .background(sk.holidaySoft)
                                    .border(2.dp, sk.holiday.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                else Modifier.glass(RoundedCornerShape(16.dp))
                            )
                            .clickableNoRipple { eventsOpen = !eventsOpen }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isHolidayDay) sk.holiday else sk.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                SkIcons.Holiday, null, Modifier.size(16.dp),
                                tint = if (isHolidayDay) sk.sheetSolid else sk.primary
                            )
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                if (isHolidayDay) holidays.joinToString("، ") { it.title } else occasions.first().title,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                color = if (isHolidayDay) sk.holiday else sk.text,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (occasions.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isHolidayDay) Text("تعطیل رسمی · ", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.holiday)
                                    Text("+${toPersianDigits(occasions.size)} مناسبت", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                }
                            }
                        }
                        Icon(
                            SkIcons.ChevronDown, null, Modifier.size(15.dp),
                            tint = sk.textFaint
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = eventsOpen) {
                        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (holidays.isNotEmpty()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(sk.holidaySoft)
                                        .border(1.dp, sk.holiday.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(sk.holiday),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(SkIcons.Holiday, null, Modifier.size(15.dp), tint = sk.sheetSolid)
                                    }
                                    Text(
                                        "تعطیل رسمی — " + holidays.joinToString("، ") { it.title },
                                        Modifier.padding(start = 10.dp),
                                        fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.holiday, lineHeight = 24.sp
                                    )
                                }
                            }
                            if (occasions.isNotEmpty()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .glass(RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                ) {
                                    Box(
                                        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(sk.primarySoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(SkIcons.Holiday, null, Modifier.size(14.dp), tint = sk.primary)
                                    }
                                    Column(Modifier.padding(start = 10.dp)) {
                                        occasions.forEach { o ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Box(
                                                    Modifier.padding(top = 10.dp).size(4.dp).clip(CircleShape).background(sk.textFaint)
                                                )
                                                Text(
                                                    o.title,
                                                    Modifier.padding(start = 8.dp),
                                                    fontSize = 12.sp, lineHeight = 24.sp, fontFamily = IRANSansX, color = sk.textMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* ---- all four groups ---- */
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(SkIcons.Group, null, Modifier.size(13.dp), tint = sk.primary)
                Text("شیفت گروه‌ها", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GROUPS.chunked(2).forEach { rowGroups ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowGroups.forEach { grp ->
                            val shift = ShiftEngine.calculateShift(dateKey, grp)
                            val kind = codeToKind(shift.code)
                            val kc = kindColors(kind)
                            val isMine = grp == effGroup
                            Box(
                                Modifier
                                    .weight(1f)
                                    /* v2.2.4: شیشه‌ای مثل بقیهٔ سطوح — حلقهٔ «شما» حفظ شد */
                                    .glass(
                                        RoundedCornerShape(16.dp),
                                        tint = if (isMine) sk.primary.copy(alpha = 0.10f) else null
                                    )
                                    .border(
                                        2.dp,
                                        if (isMine) sk.primary else Color.Transparent,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(kc.bg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(kindIcon(kind), null, Modifier.size(16.dp), tint = kc.ink)
                                    }
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(GROUP_FA.getValue(grp), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                                            if (isMine) {
                                                Text(
                                                    "شما",
                                                    Modifier
                                                        .padding(start = 4.dp)
                                                        .clip(RoundedCornerShape(100))
                                                        .background(sk.primarySoft)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(shift.code.name, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary)
                                            Text(" · ${CODE_FA.getValue(shift.code)}", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        if (rowGroups.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            /* ---- note editor ---- */
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(SkIcons.Notes, null, Modifier.size(13.dp), tint = sk.primary)
                Text("یادداشت شخصی", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    /* v2.2.4: میدان یادداشت شیشه‌ای — هویت زردِ یادداشت زیر شیشه حفظ شد */
                    .glass(RoundedCornerShape(16.dp), tint = sk.note)
                    .border(1.dp, sk.noteInk.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp, lineHeight = 24.sp, fontFamily = IRANSansX,
                        color = sk.text, textAlign = TextAlign.Start
                    ),
                    cursorBrush = SolidColor(sk.primary),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) {
                                Text(
                                    "چیزی برای این روز بنویسید… (خالی = حذف)",
                                    fontSize = 14.sp, fontFamily = IRANSansX, color = sk.textFaint
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${toPersianDigits(note.length)}/${toPersianDigits(500)}",
                        fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint
                    )
                    Spacer(Modifier.weight(1f))
                    if (note.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickableNoRipple {
                                    haptic.tick(12)
                                    note = ""
                                    vm.saveNote(dateKey, "")
                                    saved = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SkIcons.Trash, "حذف یادداشت", Modifier.size(16.dp), tint = sk.holiday)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(sk.primary)
                            .clickableNoRipple {
                                haptic.tick(12)
                                vm.saveNote(dateKey, note)
                                saved = true
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (saved) Icon(SkIcons.Check, null, Modifier.size(14.dp), tint = sk.sheetSolid)
                            Text(
                                if (saved) "ذخیره شد" else "ذخیره",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                color = sk.sheetSolid
                            )
                        }
                    }
                }
            }
        }
}
