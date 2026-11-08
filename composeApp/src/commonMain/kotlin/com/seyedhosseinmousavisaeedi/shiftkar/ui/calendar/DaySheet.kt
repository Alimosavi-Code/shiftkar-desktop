package com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.seyedhosseinmousavisaeedi.shiftkar.util.ImageExporter
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Day sheet — 3-calendar header (Jalali+Hijri+Gregorian), holiday/occasions
 * collapsible chip, all four groups' shifts, Shift Swap & Overtime Tracker (v6.4.0),
 * personal note editor, and inline PNG card export.
 */

@Composable
fun DaySheet(
    vm: AppViewModel,
    dateKey: String?,
    onDismiss: () -> Unit
) {
    var lastKey by remember { mutableStateOf<String?>(null) }
    if (dateKey != null) lastKey = dateKey
    val sheetScroll = rememberScrollState()

    SkSheet(
        visible = dateKey != null,
        onDismiss = onDismiss,
        title = "جزئیات روز",
        bodyScroll = sheetScroll
    ) {
        lastKey?.let { key -> DaySheetBody(vm, key, sheetScroll) }
    }
}

@Composable
private fun DaySheetBody(
    vm: AppViewModel,
    dateKey: String,
    sheetScroll: ScrollState
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)

    val context: Any? = null
    val scope = rememberCoroutineScope()
    var note by remember(dateKey) { mutableStateOf("") }
    var saved by remember(dateKey) { mutableStateOf(false) }
    var eventsOpen by remember(dateKey) { mutableStateOf(false) }
    var exportDayOpen by remember(dateKey) { mutableStateOf(false) }

    val dayRecord = s.dayRecords[dateKey]
    val overtimeHours = dayRecord?.overtimeHours ?: 0f
    val swapKind = dayRecord?.swapKind

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
    val events = if (s.showEvents) Holidays.getDayEvents(j.jy, j.jm, j.jd) else emptyList()
    val holidays = events.filter { it.isHoliday }
    val occasions = events.filter { !it.isHoliday }
    val effGroup = s.myGroup.toShiftGroup()
    val mine = ShiftEngine.calculateShift(dateKey, effGroup)
    val mineKind = swapKind ?: codeToKind(mine.code)
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
                    if (swapKind != null) {
                        Text(
                            "جابه‌جایی: ${SHIFT_KIND_FA.getValue(swapKind)}",
                            Modifier
                                .clip(RoundedCornerShape(100))
                                .background(mineKc.bg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = mineKc.ink
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .glass(RoundedCornerShape(14.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            exportDayOpen = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Image, "خروجی تصویر روز", Modifier.size(19.dp), tint = sk.primary)
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
        }

        /* ---- holiday / occasions collapsible chip (expands ONLY when total events >= 2) ---- */
        if (holidays.isNotEmpty() || occasions.isNotEmpty()) {
            val isHolidayDay = holidays.isNotEmpty()
            val totalEvents = holidays.size + occasions.size
            val canExpand = totalEvents >= 2
            val extraOccasions = if (isHolidayDay) occasions else occasions.drop(1)

            Column(Modifier.padding(bottom = 16.dp)) {
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
                        .clickableNoRipple(enabled = canExpand) { eventsOpen = !eventsOpen }
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
                            if (isHolidayDay) holidays.first().title else occasions.first().title,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                            color = if (isHolidayDay) sk.holiday else sk.text,
                            maxLines = if (canExpand) 1 else 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (isHolidayDay || extraOccasions.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (isHolidayDay) {
                                    Text(
                                        if (holidays.size > 1) "تعطیل رسمی (+${toPersianDigits(holidays.size - 1)} تعطیلی دیگر)" else "تعطیل رسمی",
                                        fontSize = 11.sp, fontFamily = IRANSansX, color = sk.holiday
                                    )
                                }
                                if (extraOccasions.isNotEmpty()) {
                                    if (isHolidayDay) Text("·", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                    Text(
                                        "+${toPersianDigits(extraOccasions.size)} مناسبت",
                                        fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted
                                    )
                                }
                            }
                        }
                    }
                    if (canExpand) {
                        Icon(
                            if (eventsOpen) SkIcons.ChevronUp else SkIcons.ChevronDown,
                            null,
                            Modifier.size(15.dp),
                            tint = sk.textFaint
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = canExpand && eventsOpen) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (holidays.size > 1) {
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
                        if (extraOccasions.isNotEmpty()) {
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
                                    extraOccasions.forEach { o ->
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
                        val isMine = grp == effGroup
                        val kind = if (isMine && swapKind != null) swapKind else codeToKind(shift.code)
                        val kc = kindColors(kind)
                        Box(
                            Modifier
                                .weight(1f)
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
                                        if (isMine && swapKind != null) {
                                            Text(
                                                "جابه‌جایی: ${SHIFT_KIND_FA.getValue(swapKind)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = IRANSansX,
                                                color = kc.ink
                                            )
                                        } else {
                                            Text(shift.code.name, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary)
                                            Text(" · ${CODE_FA.getValue(shift.code)}", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (rowGroups.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (s.showEmployeesOnCalendar && s.employees.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(SkIcons.Group, null, Modifier.size(13.dp), tint = sk.primary)
                Text(
                    "وضعیت پرسنل شما در این روز (${toPersianDigits(s.employees.size)} نفر)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                s.employees.forEach { emp ->
                    val pat = if (emp.patternId == "ACTIVE") ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
                    else ShiftEngine.resolvePattern(emp.patternId, s.customPatterns)
                    val eCode = ShiftEngine.getShiftCodeForPattern(dateKey, emp.group, pat)
                    val eSchedKind = codeToKind(eCode)
                    val eLog = emp.dayLogs[dateKey]
                    val eKind: ShiftKind? = when (eLog?.status ?: com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SCHEDULED) {
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SCHEDULED -> eSchedKind
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.LEAVE -> null
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_DAY -> ShiftKind.DAY
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_EVENING -> ShiftKind.EVENING
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_NIGHT -> ShiftKind.NIGHT
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_REST -> ShiftKind.REST
                    }
                    val (eBg, eInk, eTxt) = when (eKind) {
                        ShiftKind.DAY -> Triple(sk.daySoft, sk.dayInk, CODE_FA.getValue(eCode))
                        ShiftKind.EVENING -> Triple(sk.eveningSoft, sk.eveningInk, CODE_FA.getValue(eCode))
                        ShiftKind.NIGHT -> Triple(sk.nightSoft, sk.nightInk, CODE_FA.getValue(eCode))
                        ShiftKind.REST -> Triple(sk.restSoft, sk.restInk, CODE_FA.getValue(eCode))
                        null -> Triple(sk.holidaySoft, sk.holiday, "مرخصی")
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(sk.surface)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${emp.name} (${GROUP_FA.getValue(emp.group)})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.text
                        )
                        Text(
                            eTxt + if ((eLog?.overtimeHours ?: 0f) > 0f) " · +${toPersianDigits(eLog!!.overtimeHours.toInt())}س" else "",
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(eBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = eInk
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        /* ---- v6.4.0: Shift Swap & Overtime Tracker ---- */
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(SkIcons.Refresh, null, Modifier.size(13.dp), tint = sk.primary)
            Text(
                "ثبت اضافه‌کاری و جابه‌جایی شیفت",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.textMuted
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /* Overtime row */
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ساعت اضافه‌کاری",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    Text(
                        if (overtimeHours > 0f) "ثبت‌شده برای این روز" else "بدون اضافه‌کاری",
                        fontSize = 10.5.sp,
                        fontFamily = IRANSansX,
                        color = if (overtimeHours > 0f) sk.primary else sk.textFaint
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(sk.surfaceStrong)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                            .clickableNoRipple(enabled = overtimeHours > 0f) {
                                haptic.tick()
                                vm.saveDayRecord(dateKey, (overtimeHours - 1f).coerceAtLeast(0f), swapKind)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "−",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = if (overtimeHours > 0f) sk.text else sk.textFaint
                        )
                    }
                    Box(
                        Modifier
                            .widthIn(min = 74.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (overtimeHours > 0f) sk.primarySoft else sk.surface)
                            .border(1.dp, if (overtimeHours > 0f) sk.primary else sk.borderSoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${toPersianDigits(overtimeHours.toInt())} ساعت",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = if (overtimeHours > 0f) sk.primary else sk.textMuted
                        )
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(sk.surfaceStrong)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                            .clickableNoRipple(enabled = overtimeHours < 24f) {
                                haptic.tick()
                                vm.saveDayRecord(dateKey, (overtimeHours + 1f).coerceAtMost(24f), swapKind)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = if (overtimeHours < 24f) sk.primary else sk.textFaint
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(sk.borderSoft))

            /* Shift Swap selector row */
            Text(
                "جابه‌جایی نوبت (وضعیت واقعی شما در این روز):",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.textMuted
            )
            val swapOptions: List<Pair<ShiftKind?, String>> = buildList {
                add(null to "برنامه")
                add(ShiftKind.DAY to "صبح‌کار")
                if (ShiftEngine.activePattern.eveningCount > 0 || swapKind == ShiftKind.EVENING) {
                    add(ShiftKind.EVENING to "عصرکار")
                }
                add(ShiftKind.NIGHT to "شب‌کار")
                add(ShiftKind.REST to "استراحت")
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                swapOptions.forEach { (kOpt, label) ->
                    val active = swapKind == kOpt
                    val (activeBg, activeFg) = when (kOpt) {
                        ShiftKind.DAY -> sk.daySoft to sk.dayInk
                        ShiftKind.EVENING -> sk.eveningSoft to sk.eveningInk
                        ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
                        ShiftKind.REST -> sk.restSoft to sk.restInk
                        null -> sk.primarySoft to sk.primary
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) activeBg else sk.surface)
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) activeFg else sk.borderSoft,
                                shape = RoundedCornerShape(11.dp)
                            )
                            .clickableNoRipple {
                                haptic.tick()
                                vm.saveDayRecord(dateKey, overtimeHours, kOpt)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = if (active) activeFg else sk.textMuted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        /* ---- note editor ---- */
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(SkIcons.Notes, null, Modifier.size(13.dp), tint = sk.primary)
            Text("یادداشت شخصی", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(16.dp))
                .border(1.dp, sk.border, RoundedCornerShape(16.dp))
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

        if (exportDayOpen) {
            Spacer(Modifier.height(16.dp))
            InlineExportCard(
                visible = exportDayOpen,
                title = "تصویر ${formatJalali(j.jy, j.jm, j.jd)}",
                fileName = "shiftkar-day-$dateKey-${effGroup.name}.png",
                hapticOn = s.hapticOn,
                produceBitmap = {
                    val exportNote = buildList {
                        if (overtimeHours > 0f) add("اضافه‌کاری: ${toPersianDigits(overtimeHours.toInt())} ساعت")
                        if (swapKind != null) add("جابه‌جایی نوبت: ${SHIFT_KIND_FA.getValue(swapKind)}")
                        if (note.isNotBlank()) add(note.trim())
                    }.joinToString(" · ").takeIf { it.isNotEmpty() }

                    ImageExporter.renderDayBitmap(
                        context = context,
                        sk = sk,
                        dateKey = dateKey,
                        myGroup = effGroup,
                        showEvents = s.showEvents,
                        note = exportNote
                    )
                },
                onReadyToScrollBottom = {
                    scope.launch {
                        runCatching { sheetScroll.animateScrollTo(sheetScroll.maxValue) }
                    }
                },
                onDismiss = { exportDayOpen = false }
            )
        }
    }
}
