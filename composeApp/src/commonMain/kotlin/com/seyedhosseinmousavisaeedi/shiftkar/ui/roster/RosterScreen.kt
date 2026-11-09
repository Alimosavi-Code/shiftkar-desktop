package com.seyedhosseinmousavisaeedi.shiftkar.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ExportImageDialog
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SegmentedControl
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.kindColors
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.ClassicLoheRowData
import com.seyedhosseinmousavisaeedi.shiftkar.util.ImageExporter
import com.seyedhosseinmousavisaeedi.shiftkar.util.RosterAllShiftsMode
import com.seyedhosseinmousavisaeedi.shiftkar.util.buildClassic1405LoheData
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics

/**
 * Roster view — the لوحه (v6.5.3):
 *  - Fridays (`جمعه`) are NO LONGER colored red in normal Lohe views; only official
 *    holidays (`تعطیل رسمی`) use `sk.holiday` red so Fridays never interfere with events.
 *  - Uses `صبح‌کار` / `عصرکار` / `شب‌کار` / `استراحت` everywhere (never plain `روز` or `شب`).
 *  - Uses distinct 2-stop shift gradients (`kindColors(kind).brush`) matching Calendar & Export.
 *  - Includes the new 4-shift `CLASSIC_1405` mode («چارت کلاسیک ۱۴۰۵») with the exact
 *    layout and core colors of the 1404/1405 annual shift chart (6 first-half months on left,
 *    A/B/C/D cyan/amber/green/magenta columns in center, 6 second-half months + next Farvardin
 *    on right, 32 cycle rows) both in-app and in high-resolution PNG export.
 */

private val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
private const val MIN_YEAR = 1300
private const val MAX_YEAR = 1500

private data class RosterCellState(
    val d: Int,
    val kind: ShiftKind,
    val isOfficialHoliday: Boolean,
    val isFriday: Boolean,
    val isToday: Boolean
)

private data class AllShiftsDayCell(
    val d: Int,
    val isOfficialHoliday: Boolean,
    val isFriday: Boolean,
    val isToday: Boolean,
    val dayGroup: String,
    val eveningGroup: String,
    val nightGroup: String,
    val restGroups: String,
    val groupKinds: Map<ShiftGroup, ShiftKind>
)

@Composable
fun RosterScreen(
    vm: AppViewModel,
    onOpenMonth: (Int, Int) -> Unit = { _, _ -> }
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val context: Any? = null
    val today = remember { todayJalaali() }

    val hasGroups = ShiftEngine.activePattern.hasGroups
    var filter by remember(s.myGroup, hasGroups) {
        mutableStateOf(if (!hasGroups) GroupFilter.A else s.myGroup)
    }
    var allShiftsMode by remember { mutableStateOf(RosterAllShiftsMode.CLASSIC_1405) }
    var year by remember(s.viewYear) { mutableIntStateOf(s.viewYear) }
    var exportRosterOpen by remember { mutableStateOf(false) }

    val filterLabels = remember(s.activePatternId, s.customPatterns) {
        GROUP_FILTERS.map { g ->
            if (g == GroupFilter.ALL) "همه" else GROUP_FA.getValue(g.toShiftGroup())
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "لوحه سالانه",
                Modifier.align(Alignment.CenterStart),
                fontSize = 18.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = sk.text
            )

            /* Centralized year stepper with Calendar-style glass hover pill */
            Row(
                Modifier
                    .align(Alignment.Center)
                    .glass(RoundedCornerShape(14.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                        .clickableNoRipple(enabled = year > MIN_YEAR) { haptic.tick(); year -= 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SkIcons.ChevronRight, "سال قبل", Modifier.size(15.dp),
                        tint = if (year > MIN_YEAR) sk.textMuted else sk.textFaint.copy(alpha = 0.4f)
                    )
                }
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(100))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.border, RoundedCornerShape(100))
                        .clickableNoRipple {
                            if (year != today.jy) {
                                haptic.tick()
                                year = today.jy
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        toPersianDigits(year),
                        textAlign = TextAlign.Center,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                        .clickableNoRipple(enabled = year < MAX_YEAR) { haptic.tick(); year += 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SkIcons.ChevronLeft, "سال بعد", Modifier.size(15.dp),
                        tint = if (year < MAX_YEAR) sk.textMuted else sk.textFaint.copy(alpha = 0.4f)
                    )
                }
            }

            /* دکمهٔ خروجی تصویر لوحه */
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .height(34.dp)
                    .glass(RoundedCornerShape(11.dp), tint = sk.primary.copy(alpha = 0.14f))
                    .border(1.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                    .clickableNoRipple {
                        haptic.tick()
                        exportRosterOpen = true
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(SkIcons.Image, "خروجی تصویر لوحه", Modifier.size(14.dp), tint = sk.primary)
                Text(
                    "تصویر",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        /* سوئیچر گروه کاری: همه | شیفت A | شیفت B | شیفت C | شیفت D */
        if (hasGroups) {
            SegmentedControl(
                options = filterLabels,
                selectedIndex = GROUP_FILTERS.indexOf(filter).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            ) { idx ->
                haptic.tick()
                filter = GROUP_FILTERS[idx]
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sk.primarySoft)
                    .border(1.dp, sk.primary, RoundedCornerShape(14.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "لوحه سالانه — شیفت فردی (بدون گروه)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }
        }

        if (filter == GroupFilter.ALL && hasGroups) {
            Spacer(Modifier.height(6.dp))
            SegmentedControl(
                options = listOf("لوحه کلاسیک", "لوحه رسمی (نوبت‌ها)", "ماتریس ۴ شیفت"),
                selectedIndex = when (allShiftsMode) {
                    RosterAllShiftsMode.CLASSIC_1405 -> 0
                    RosterAllShiftsMode.BY_SHIFT_ROLE -> 1
                    RosterAllShiftsMode.BY_GROUP_ROW -> 2
                },
                modifier = Modifier.fillMaxWidth()
            ) { idx ->
                haptic.tick()
                allShiftsMode = when (idx) {
                    0 -> RosterAllShiftsMode.CLASSIC_1405
                    1 -> RosterAllShiftsMode.BY_SHIFT_ROLE
                    else -> RosterAllShiftsMode.BY_GROUP_ROW
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filter != GroupFilter.ALL || !hasGroups) {
            val singleGroup = filter.toShiftGroup()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 108.dp)
            ) {
                items(12) { idx ->
                    val jm = idx + 1
                    MonthBlock(year, jm, today, singleGroup, s.showEvents, s.activePatternId, s.customPatterns.size) {
                        haptic.tick()
                        onOpenMonth(year, jm)
                    }
                }
            }
        } else if (allShiftsMode == RosterAllShiftsMode.CLASSIC_1405) {
            Classic1405LoheView(
                jy = year,
                today = today,
                showEvents = s.showEvents,
                patternKey = s.activePatternId,
                customCount = s.customPatterns.size,
                onOpenMonth = { y, m ->
                    haptic.tick()
                    onOpenMonth(y, m)
                }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 108.dp)
            ) {
                items(12) { idx ->
                    val jm = idx + 1
                    AllShiftsMonthBlock(
                        jy = year,
                        jm = jm,
                        today = today,
                        showEvents = s.showEvents,
                        patternKey = s.activePatternId,
                        customCount = s.customPatterns.size,
                        mode = allShiftsMode,
                        onClick = {
                            haptic.tick()
                            onOpenMonth(year, jm)
                        }
                    )
                }
            }
        }
    }

    val exportTitle = if (filter == GroupFilter.ALL && hasGroups) {
        when (allShiftsMode) {
            RosterAllShiftsMode.CLASSIC_1405 -> "تصویر لوحه کلاسیک سال ${toPersianDigits(year)}"
            RosterAllShiftsMode.BY_SHIFT_ROLE -> "تصویر لوحه سالانه ${toPersianDigits(year)} (رسمی)"
            RosterAllShiftsMode.BY_GROUP_ROW -> "تصویر لوحه سالانه ${toPersianDigits(year)} (ماتریس ۴ شیفت)"
        }
    } else {
        "تصویر لوحه سالانه ${toPersianDigits(year)} (${GROUP_FA.getValue(filter.toShiftGroup())})"
    }
    val exportFileName = if (filter == GroupFilter.ALL && hasGroups) {
        "shiftkar-roster-$year-ALL-${allShiftsMode.name.lowercase()}.png"
    } else {
        "shiftkar-roster-$year-${filter.toShiftGroup().name}.png"
    }

    ExportImageDialog(
        visible = exportRosterOpen,
        title = exportTitle,
        fileName = exportFileName,
        hapticOn = s.hapticOn,
        modeKey = if (filter == GroupFilter.ALL && hasGroups) allShiftsMode else filter,
        headerExtra = if (filter == GroupFilter.ALL && hasGroups) {
            {
                SegmentedControl(
                    options = listOf("لوحه کلاسیک", "لوحه رسمی", "ماتریس ۴ شیفت"),
                    selectedIndex = when (allShiftsMode) {
                        RosterAllShiftsMode.CLASSIC_1405 -> 0
                        RosterAllShiftsMode.BY_SHIFT_ROLE -> 1
                        RosterAllShiftsMode.BY_GROUP_ROW -> 2
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { idx ->
                    haptic.tick()
                    allShiftsMode = when (idx) {
                        0 -> RosterAllShiftsMode.CLASSIC_1405
                        1 -> RosterAllShiftsMode.BY_SHIFT_ROLE
                        else -> RosterAllShiftsMode.BY_GROUP_ROW
                    }
                }
            }
        } else null,
        produceBitmap = {
            if (filter == GroupFilter.ALL && hasGroups) {
                ImageExporter.renderAllShiftsRosterBitmap(
                    context = context,
                    sk = sk,
                    jy = year,
                    showEvents = s.showEvents,
                    mode = allShiftsMode
                )
            } else {
                ImageExporter.renderRosterBitmap(
                    context = context,
                    sk = sk,
                    jy = year,
                    group = filter.toShiftGroup(),
                    showEvents = s.showEvents
                )
            }
        },
        onDismiss = { exportRosterOpen = false }
    )
}

/**
 * Single-group MonthBlock:
 *  - Uses exact 2-stop shift gradient (`kindColors(cell.kind).brush`)
 *  - Official holidays (`isOfficialHoliday`) are highlighted in red (`sk.holiday`)
 *  - Fridays (`isFriday`) are NOT red; they keep their shift gradient and use a subtle
 *    muted text tone so Fridays never interfere with official holidays.
 */
@Composable
private fun MonthBlock(
    jy: Int,
    jm: Int,
    today: JalaliDate,
    group: ShiftGroup,
    showEvents: Boolean,
    patternKey: String = "",
    customCount: Int = 0,
    onClick: () -> Unit
) {
    val sk = LocalSk.current
    val cells = remember(jy, jm, today, group, showEvents, patternKey, customCount) {
        val len = jalaaliMonthLength(jy, jm)
        val firstWeekday = jalaaliWeekday(jy, jm, 1)
        List<RosterCellState?>(firstWeekday) { null } + (1..len).map { d ->
            val key = makeDateKey(jy, jm, d)
            val code = ShiftEngine.getShiftCode(key, group)
            val kind = codeToKind(code)
            val isHoliday = showEvents && Holidays.isHoliday(jy, jm, d)
            val isFriday = ((firstWeekday + d - 1) % 7) == 6
            val isToday = today.jy == jy && today.jm == jm && today.jd == d
            RosterCellState(d, kind, isHoliday, isFriday, isToday)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .clickableNoRipple { onClick() }
            .padding(8.dp)
    ) {
        Text(
            "${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = IRANSansX,
            color = sk.text
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEachIndexed { i, w ->
                Text(
                    w, Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                    color = if (i == 6) sk.textMuted else sk.textFaint
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        val rows = (cells.size + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until 7) {
                        val cell = cells.getOrNull(r * 7 + c)
                        if (cell == null) {
                            Box(Modifier.weight(1f).height(17.dp))
                        } else {
                            val kc = kindColors(cell.kind)
                            val mod = Modifier
                                .weight(1f)
                                .height(17.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    if (cell.isOfficialHoliday) {
                                        Modifier.background(sk.holiday.copy(alpha = 0.22f))
                                    } else {
                                        Modifier.background(kc.brush)
                                    }
                                )
                                .then(
                                    if (cell.isToday) Modifier.border(1.5.dp, sk.primary, RoundedCornerShape(4.dp))
                                    else Modifier
                                )
                            Box(mod, contentAlignment = Alignment.Center) {
                                Text(
                                    toPersianDigits(cell.d),
                                    fontSize = 7.5.sp,
                                    lineHeight = 8.sp,
                                    fontWeight = if (cell.isToday) FontWeight.Black else FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = when {
                                        cell.isOfficialHoliday -> sk.holiday
                                        cell.isFriday -> kc.ink.copy(alpha = 0.85f)
                                        else -> sk.text
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Classic 1404/1405 4-Shift Annual Chart View («چارت کلاسیک ۱۴۰۵» — exact layout & colors of 81361.png):
 *  - Reworked modern card with green header banner («لوحه نوبتکاری سال ۱۴۰۵»)
 *  - Left 6 columns: فروردین، اردیبهشت، خرداد، تیر، مرداد، شهریور
 *  - Center 4 columns: A (Cyan), B (Amber/Orange), C (Bright Green), D (Magenta/Pink) showing M1, M2, N1, N2, R1..R4
 *  - Right 7 columns: مهر، آبان، آذر، دی، بهمن، اسفند، فروردین سال بعد
 *  - 32 cycle rows grouped in 2-row blocks with bold separator lines
 *  - Red cells for official holidays (`تعطیل رسمی`) and Yellow cells for Fridays (`جمعه`)
 */
@Composable
private fun Classic1405LoheView(
    jy: Int,
    today: JalaliDate,
    showEvents: Boolean,
    patternKey: String,
    customCount: Int,
    onOpenMonth: (Int, Int) -> Unit
) {
    val sk = LocalSk.current
    val calSyncRev = CalendarSync.revision.collectAsState().value
    val rowsData: List<ClassicLoheRowData> = remember(jy, showEvents, patternKey, customCount, calSyncRev) {
        buildClassic1405LoheData(jy, showEvents)
    }

    val isDark = sk.sheetSolid.luminance() < 0.5f
    /* Crisp, high-contrast Frost palette for the 4 center columns (A=Cyan, B=Amber, C=Emerald, D=Magenta) — zero white haze! */
    val colHeaderA = if (isDark) Color(0xFF0284C7).copy(alpha = 0.55f) else Color(0xFF38BDF8).copy(alpha = 0.45f)
    val colCellA = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.22f) else Color(0xFFBAE6FD).copy(alpha = 0.65f)
    val colInkA = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)

    val colHeaderB = if (isDark) Color(0xFFD97706).copy(alpha = 0.55f) else Color(0xFFFBBF24).copy(alpha = 0.50f)
    val colCellB = if (isDark) Color(0xFFFBBF24).copy(alpha = 0.22f) else Color(0xFFFDE68A).copy(alpha = 0.70f)
    val colInkB = if (isDark) Color(0xFFFDE047) else Color(0xFF92400E)

    val colHeaderC = if (isDark) Color(0xFF059669).copy(alpha = 0.55f) else Color(0xFF34D399).copy(alpha = 0.48f)
    val colCellC = if (isDark) Color(0xFF34D399).copy(alpha = 0.22f) else Color(0xFFA7F3D0).copy(alpha = 0.70f)
    val colInkC = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)

    val colHeaderD = if (isDark) Color(0xFFDB2777).copy(alpha = 0.55f) else Color(0xFFF472B6).copy(alpha = 0.48f)
    val colCellD = if (isDark) Color(0xFFF472B6).copy(alpha = 0.22f) else Color(0xFFFBCFE8).copy(alpha = 0.70f)
    val colInkD = if (isDark) Color(0xFFF9A8D4) else Color(0xFF9D174D)

    val redHolBg = if (isDark) sk.holiday.copy(alpha = 0.78f) else Color(0xFFEF4444)
    val yellowFriBg = if (isDark) sk.day.copy(alpha = 0.34f) else Color(0xFFFDE047)
    val yellowFriInk = if (isDark) sk.dayInk else Color(0xFF713F12)
    val normalCellBg = if (isDark) Color(0xFF151C2F) else sk.surface
    val headerRowBg = if (isDark) Color(0xFF0E1424) else sk.surfaceStrong
    val cardBaseBg = if (isDark) Color(0xFF0B1020) else sk.sheetSolid
    val gridLineColor = sk.borderSoft
    val thickLineColor = sk.border

    val firstHalfMonths = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور")
    val secondHalfMonths = listOf("مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowH = ((maxHeight - 190.dp) / 32.5f).coerceIn(20.dp, 26.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /* 1. Sleek Glassy Pill Title Header */
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .glass(RoundedCornerShape(100), tint = sk.primarySoft)
                    .border(1.2.dp, sk.primary.copy(alpha = 0.55f), RoundedCornerShape(100))
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(sk.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Roster, null, Modifier.size(12.dp), tint = sk.sheetSolid)
                }
                Text(
                    "لوحه نوبت‌کاری سال ${toPersianDigits(jy)}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.text
                )
            }

            /* 2. Full-Width Deep Dark Chart Card (Zero White Haze!) */
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBaseBg)
                        .border(1.2.dp, sk.border, RoundedCornerShape(18.dp))
                        .padding(4.dp)
                ) {
                    val mWeight = 1f
                    val gWeight = 1.22f

                    /* Header Row: 6 Left Months | A B C D | 6 Right Months */
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp))
                            .background(headerRowBg)
                            .border(0.8.dp, thickLineColor, RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        firstHalfMonths.forEachIndexed { idx, mName ->
                            Box(
                                Modifier
                                    .weight(mWeight)
                                    .fillMaxHeight()
                                    .border(0.4.dp, gridLineColor)
                                    .clickableNoRipple { onOpenMonth(jy, idx + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    mName,
                                    fontSize = 8.2.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = IRANSansX,
                                    color = sk.text,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = -90f
                                        transformOrigin = TransformOrigin.Center
                                    }
                                )
                            }
                        }

                        val grpHeaders = listOf(
                            Triple(ShiftGroup.A.faLetter(), colHeaderA, colInkA),
                            Triple(ShiftGroup.B.faLetter(), colHeaderB, colInkB),
                            Triple(ShiftGroup.C.faLetter(), colHeaderC, colInkC),
                            Triple(ShiftGroup.D.faLetter(), colHeaderD, colInkD)
                        )
                        grpHeaders.forEach { (gLbl, gColor, gInk) ->
                            Box(
                                Modifier
                                    .weight(gWeight)
                                    .fillMaxHeight()
                                    .background(gColor)
                                    .border(0.8.dp, thickLineColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    gLbl,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = IRANSansX,
                                    color = gInk
                                )
                            }
                        }

                        secondHalfMonths.forEachIndexed { idx, mName ->
                            val targetMonth = idx + 7
                            Box(
                                Modifier
                                    .weight(mWeight)
                                    .fillMaxHeight()
                                    .border(0.4.dp, gridLineColor)
                                    .clickableNoRipple { onOpenMonth(jy, targetMonth) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    mName,
                                    fontSize = 8.2.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = IRANSansX,
                                    color = sk.text,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = -90f
                                        transformOrigin = TransformOrigin.Center
                                    }
                                )
                            }
                        }
                    }

                    /* 31 Cycle Rows */
                    rowsData.forEachIndexed { rIdx, row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(rowH),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            /* Left 6 month day cells (months 1..6) */
                            for (mIdx in 0 until 6) {
                                val cell = row.monthCells[mIdx]
                                val isToday = cell != null && today.jy == cell.jy && today.jm == cell.jm && today.jd == cell.day
                                val cellBg = when {
                                    cell == null -> Color.Transparent
                                    cell.isOfficialHoliday -> redHolBg
                                    cell.isFriday -> yellowFriBg
                                    else -> normalCellBg
                                }
                                val cellFg = when {
                                    cell == null -> Color.Transparent
                                    cell.isOfficialHoliday -> Color.White
                                    cell.isFriday -> yellowFriInk
                                    else -> sk.text
                                }
                                Box(
                                    Modifier
                                        .weight(mWeight)
                                        .fillMaxHeight()
                                        .background(cellBg)
                                        .border(
                                            width = if (isToday) 1.5.dp else 0.4.dp,
                                            color = if (isToday) sk.primary else gridLineColor
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cell != null) {
                                        Text(
                                            toPersianDigits(cell.day),
                                            fontSize = 8.2.sp,
                                            lineHeight = 8.5.sp,
                                            fontWeight = if (isToday || cell.isOfficialHoliday) FontWeight.Black else FontWeight.Bold,
                                            fontFamily = IRANSansX,
                                            color = cellFg
                                        )
                                    }
                                }
                            }

                            /* Center 4 Group Columns (A, B, C, D) */
                            val grpCols = listOf(
                                Triple(row.codeA, colCellA, colInkA),
                                Triple(row.codeB, colCellB, colInkB),
                                Triple(row.codeC, colCellC, colInkC),
                                Triple(row.codeD, colCellD, colInkD)
                            )
                            grpCols.forEach { (code, bgCol, inkCol) ->
                                Box(
                                    Modifier
                                        .weight(gWeight)
                                        .fillMaxHeight()
                                        .background(bgCol)
                                        .border(0.6.dp, thickLineColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        code.name,
                                        fontSize = 8.sp,
                                        lineHeight = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = IRANSansX,
                                        color = inkCol
                                    )
                                }
                            }

                            /* Right 6 month day cells (months 7..12) */
                            for (mIdx in 6 until 12) {
                                val cell = row.monthCells[mIdx]
                                val isToday = cell != null && today.jy == cell.jy && today.jm == cell.jm && today.jd == cell.day
                                val cellBg = when {
                                    cell == null -> Color.Transparent
                                    cell.isOfficialHoliday -> redHolBg
                                    cell.isFriday -> yellowFriBg
                                    else -> normalCellBg
                                }
                                val cellFg = when {
                                    cell == null -> Color.Transparent
                                    cell.isOfficialHoliday -> Color.White
                                    cell.isFriday -> yellowFriInk
                                    else -> sk.text
                                }
                                Box(
                                    Modifier
                                        .weight(mWeight)
                                        .fillMaxHeight()
                                        .background(cellBg)
                                        .border(
                                            width = if (isToday) 1.5.dp else 0.4.dp,
                                            color = if (isToday) sk.primary else gridLineColor
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cell != null) {
                                        Text(
                                            toPersianDigits(cell.day),
                                            fontSize = 8.2.sp,
                                            lineHeight = 8.5.sp,
                                            fontWeight = if (isToday || cell.isOfficialHoliday) FontWeight.Black else FontWeight.Bold,
                                            fontFamily = IRANSansX,
                                            color = cellFg
                                        )
                                    }
                                }
                            }
                        }
                        /* Bold divider every 2 rows matching the classic 2-2-4 block rhythm */
                        if (rIdx % 2 == 1 && rIdx < rowsData.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.2.dp)
                                    .background(thickLineColor)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            /* 3. Glassy Legend Bar */
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(redHolBg))
                    Text("تعطیل رسمی", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(yellowFriBg).border(0.8.dp, yellowFriInk, RoundedCornerShape(3.dp)))
                    Text("جمعه", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                }
                Text(
                    if (ShiftEngine.activePattern.eveningCount > 0) "M: صبح‌کار · E: عصرکار · N: شب‌کار · R: استراحت"
                    else "M: صبح‌کار · N: شب‌کار · R: استراحت",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
            }
        }
    }
}

/**
 * 4-Shift («همه») MonthBlock for لوحه سالانه:
 *  - BY_SHIFT_ROLE: Official printed chart layout (صبح‌کار / عصرکار / شب‌کار / استراحت).
 *  - BY_GROUP_ROW: 4-group stacked matrix (تاریخ / A / B / C / D).
 *  - Fridays are NOT colored red; only official holidays (`isOfficialHoliday`) are red.
 */
@Composable
private fun AllShiftsMonthBlock(
    jy: Int,
    jm: Int,
    today: JalaliDate,
    showEvents: Boolean,
    patternKey: String = "",
    customCount: Int = 0,
    mode: RosterAllShiftsMode,
    onClick: () -> Unit
) {
    val sk = LocalSk.current
    val days = remember(jy, jm, today, showEvents, patternKey, customCount) {
        val len = jalaaliMonthLength(jy, jm)
        val firstWeekday = jalaaliWeekday(jy, jm, 1)
        (1..len).map { d ->
            val key = makeDateKey(jy, jm, d)
            val allCodes = ShiftEngine.getAllGroupsForDate(key)
            val kinds = GROUPS.associateWith { g -> codeToKind(allCodes.getValue(g)) }
            val dayG = GROUPS.filter { kinds[it] == ShiftKind.DAY }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
            val eveG = GROUPS.filter { kinds[it] == ShiftKind.EVENING }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
            val nightG = GROUPS.filter { kinds[it] == ShiftKind.NIGHT }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
            val restG = GROUPS.filter { kinds[it] == ShiftKind.REST }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
            val isHoliday = showEvents && Holidays.isHoliday(jy, jm, d)
            val isFriday = ((firstWeekday + d - 1) % 7) == 6
            val isToday = today.jy == jy && today.jm == jm && today.jd == d
            AllShiftsDayCell(
                d = d,
                isOfficialHoliday = isHoliday,
                isFriday = isFriday,
                isToday = isToday,
                dayGroup = dayG,
                eveningGroup = eveG,
                nightGroup = nightG,
                restGroups = restG,
                groupKinds = kinds
            )
        }
    }

    val tier1 = remember(days) { days.take(16) }
    val tier2 = remember(days) { days.drop(16) }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .clickableNoRipple { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = sk.text
            )
            Text(
                "برای مشاهده در تقویم لمس کنید ‹",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.primary
            )
        }

        AllShiftsHalfStrip(tier = tier1, mode = mode)
        Spacer(Modifier.height(2.dp))
        AllShiftsHalfStrip(tier = tier2, mode = mode)
    }
}

@Composable
private fun AllShiftsHalfStrip(
    tier: List<AllShiftsDayCell>,
    mode: RosterAllShiftsMode
) {
    val sk = LocalSk.current
    val slots = 16
    val hasEvening = ShiftEngine.activePattern.eveningCount > 0

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        /* 1. Day Number Header Row (۱..۱۶ or ۱۷..۳۱) */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AllShiftsRowLabel("تاریخ", Brush.linearGradient(listOf(sk.surfaceStrong, sk.surface)), sk.textMuted)
            for (i in 0 until slots) {
                val cell = tier.getOrNull(i)
                if (cell == null) {
                    Box(Modifier.weight(1f).height(16.dp))
                } else {
                    val bg = when {
                        cell.isOfficialHoliday -> sk.holiday.copy(alpha = 0.22f)
                        cell.isFriday -> sk.surface
                        else -> sk.surfaceStrong
                    }
                    val fg = when {
                        cell.isOfficialHoliday -> sk.holiday
                        cell.isFriday -> sk.textMuted
                        else -> sk.text
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg)
                            .then(
                                if (cell.isToday) Modifier.border(1.3.dp, sk.primary, RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            toPersianDigits(cell.d),
                            fontSize = 7.5.sp,
                            lineHeight = 8.sp,
                            fontWeight = if (cell.isToday) FontWeight.Black else FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = fg
                        )
                    }
                }
            }
        }

        if (mode == RosterAllShiftsMode.BY_SHIFT_ROLE) {
            /* Official Chart Layout: صبح‌کار / (عصرکار) / شب‌کار / استراحت */
            val dayKc = kindColors(ShiftKind.DAY)
            val eveKc = kindColors(ShiftKind.EVENING)
            val nightKc = kindColors(ShiftKind.NIGHT)
            val restKc = kindColors(ShiftKind.REST)

            val roleRows = buildList {
                add(Triple("صبح‌کار", dayKc, { c: AllShiftsDayCell -> c.dayGroup }))
                if (hasEvening) {
                    add(Triple("عصرکار", eveKc, { c: AllShiftsDayCell -> c.eveningGroup }))
                }
                add(Triple("شب‌کار", nightKc, { c: AllShiftsDayCell -> c.nightGroup }))
                add(Triple("استراحت", restKc, { c: AllShiftsDayCell -> c.restGroups }))
            }
            roleRows.forEach { (label, kc, extractor) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AllShiftsRowLabel(label, kc.brush, kc.ink)
                    for (i in 0 until slots) {
                        val cell = tier.getOrNull(i)
                        if (cell == null) {
                            Box(Modifier.weight(1f).height(16.dp))
                        } else {
                            val txt = extractor(cell)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(kc.brush)
                                    .then(
                                        if (cell.isToday) Modifier.border(1.dp, sk.primary.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    txt,
                                    fontSize = if (txt.length > 1) 6.8.sp else 7.8.sp,
                                    lineHeight = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = IRANSansX,
                                    color = kc.ink
                                )
                            }
                        }
                    }
                }
            }
        } else {
            /* 4-Group Matrix Layout: A / B / C / D rows */
            GROUPS.forEach { g ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AllShiftsRowLabel(g.faLetter(), Brush.linearGradient(listOf(sk.primarySoft, sk.primarySoft)), sk.primary)
                    for (i in 0 until slots) {
                        val cell = tier.getOrNull(i)
                        if (cell == null) {
                            Box(Modifier.weight(1f).height(15.dp))
                        } else {
                            val kind = cell.groupKinds.getValue(g)
                            val kc = kindColors(kind)
                            val shortFa = when (kind) {
                                ShiftKind.DAY -> "ص"
                                ShiftKind.EVENING -> "ع"
                                ShiftKind.NIGHT -> "ش"
                                ShiftKind.REST -> "ا"
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(15.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(kc.brush)
                                    .then(
                                        if (cell.isToday) Modifier.border(1.dp, sk.primary.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    shortFa,
                                    fontSize = 7.sp,
                                    lineHeight = 7.5.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = IRANSansX,
                                    color = kc.ink
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllShiftsRowLabel(
    text: String,
    brush: Brush,
    fg: Color
) {
    Box(
        Modifier
            .width(40.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 6.8.sp,
            lineHeight = 7.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = IRANSansX,
            color = fg,
            maxLines = 1
        )
    }
}
