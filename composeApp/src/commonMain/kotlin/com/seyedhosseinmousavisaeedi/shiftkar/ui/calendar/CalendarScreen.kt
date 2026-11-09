package com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.tourTarget

private val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

data class DayInfo(
    val d: Int, val key: String, val holiday: Boolean, val holidayNames: List<String>,
    val occasions: List<String>, val isToday: Boolean, val isFriday: Boolean,
    val weekday: Int, val label: String, val note: String?,
    val all: Map<ShiftGroup, ShiftCode>,
    val overtimeHours: Float = 0f,
    val swapKind: ShiftKind? = null,
    val colleagueSummary: String = "",
    val hasColleagueSharedRest: Boolean = false
)

/* ================= Calendar screen ================= */

@Composable
fun CalendarScreen(
    vm: AppViewModel,
    onOpenDay: (String) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMonthPicker: () -> Unit,
    onTableFullscreen: (Boolean) -> Unit = {}
) {
    val s = vm.settings.collectAsState().value
    val notes = vm.notesFlow.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)

    var dir by remember { mutableIntStateOf(1) }
    var tableFs by remember { mutableStateOf(false) }
    var chipCollapsed by remember { mutableStateOf(false) }
    var chipSessionStarted by remember { mutableStateOf(false) }
    var offCount by remember { mutableIntStateOf(0) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var compareGroup by remember { mutableStateOf<ShiftGroup?>(null) }
    var exportMonthOpen by remember { mutableStateOf(false) }
    var exportMonthMode by remember { mutableStateOf(s.viewType) }
    val context: Any? = null

    val today = remember { todayJalaali() }
    val todayKey = remember(today) { makeDateKey(today.jy, today.jm, today.jd) }
    val isCurrentMonth = s.viewYear == today.jy && s.viewMonth == today.jm

    /* v6.0.0 (مالک: «delete the navbar from full screen table view»):
     * وضعیت جدول تمام‌صفحه به MainActivity گزارش می‌شود تا نوار پایین
     * با انیمیشن مخفی شود. */
    LaunchedEffect(tableFs) { onTableFullscreen(tableFs) }

    LaunchedEffect(isCurrentMonth) {
        if (isCurrentMonth) { chipCollapsed = false; chipSessionStarted = false; offCount = 0 }
    }
    LaunchedEffect(s.viewYear, s.viewMonth, isCurrentMonth) {
        if (!isCurrentMonth && !chipCollapsed) {
            offCount += 1
            val hold = if (chipSessionStarted) 900L else 2400L
            chipSessionStarted = true
            kotlinx.coroutines.delay(if (offCount <= 1) hold else 900L)
            chipCollapsed = true
        }
    }

    fun goMonth(delta: Int) {
        haptic.tick()
        dir = delta
        var y = s.viewYear
        var m = s.viewMonth + delta
        if (m > 12) { m = 1; y += 1 }
        if (m < 1) { m = 12; y -= 1 }
        vm.setViewMonth(y, m)
    }

    fun goToday() {
        haptic.tick()
        dir = if (today.jy * 12 + today.jm > s.viewYear * 12 + s.viewMonth) 1 else -1
        vm.setViewMonth(today.jy, today.jm)
    }

    fun setFilter(g: GroupFilter) {
        haptic.tick()
        if (g == GroupFilter.ALL) {
            vm.set { it.copy(filterGroup = GroupFilter.ALL) }
        } else {
            if (compareGroup == g.toShiftGroup()) compareGroup = null
            vm.set { it.copy(filterGroup = g, myGroup = g) }
        }
    }

    /* ---- month data (shared by grid + table) ---- */
    val monthLen = jalaaliMonthLength(s.viewYear, s.viewMonth)
    val firstWeekday = jalaaliWeekday(s.viewYear, s.viewMonth, 1)
    val calSyncRev = CalendarSync.revision.collectAsState().value
    val dayInfo = remember(
        s.viewYear, s.viewMonth, notes, s.showEvents, s.dayRecords,
        s.activePatternId, s.customPatterns, s.employees, s.showEmployeesOnCalendar, s.myGroup, calSyncRev
    ) {
        val myGrp = s.myGroup.toShiftGroup()
        (1..monthLen).map { d ->
            val key = makeDateKey(s.viewYear, s.viewMonth, d)
            val events = if (s.showEvents) Holidays.getDayEvents(s.viewYear, s.viewMonth, d) else emptyList()
            val allMap = ShiftEngine.calculateAllShifts(key).associate { it.group to it.code }
            val myEffKind = s.dayRecords[key]?.swapKind ?: codeToKind(allMap.getValue(myGrp))

            var sharedRestEmp = false
            val empSummary = if (s.showEmployeesOnCalendar && s.employees.isNotEmpty()) {
                s.employees.joinToString(" · ") { emp ->
                    val pat = if (emp.patternId == "ACTIVE") ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
                    else ShiftEngine.resolvePattern(emp.patternId, s.customPatterns)
                    val eCode = ShiftEngine.getShiftCodeForPattern(key, emp.group, pat)
                    val eLog = emp.dayLogs[key]
                    val eKind: ShiftKind? = when (eLog?.status ?: com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SCHEDULED) {
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SCHEDULED -> codeToKind(eCode)
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.LEAVE -> null
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_DAY -> ShiftKind.DAY
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_EVENING -> ShiftKind.EVENING
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_NIGHT -> ShiftKind.NIGHT
                        com.seyedhosseinmousavisaeedi.shiftkar.data.EmployeeDayStatus.SWAP_REST -> ShiftKind.REST
                    }
                    if (myEffKind == ShiftKind.REST && eKind == ShiftKind.REST) {
                        sharedRestEmp = true
                    }
                    val statusFa = eKind?.let { SHIFT_KIND_FA.getValue(it) } ?: "مرخصی"
                    "${emp.name}: $statusFa"
                }
            } else ""

            DayInfo(
                d = d, key = key,
                holiday = events.any { it.isHoliday },
                holidayNames = events.filter { it.isHoliday }.map { it.title },
                occasions = events.filter { !it.isHoliday }.map { it.title },
                isToday = key == todayKey,
                isFriday = jalaaliWeekday(s.viewYear, s.viewMonth, d) == 6,
                weekday = jalaaliWeekday(s.viewYear, s.viewMonth, d),
                label = "${formatWeekday(s.viewYear, s.viewMonth, d)} ${formatJalali(s.viewYear, s.viewMonth, d)}",
                note = notes[key],
                all = allMap,
                overtimeHours = s.dayRecords[key]?.overtimeHours ?: 0f,
                swapKind = s.dayRecords[key]?.swapKind,
                colleagueSummary = empSummary,
                hasColleagueSharedRest = sharedRestEmp
            )
        }
    }

    /* v2.2.9: آستانهٔ ژست افقی — هم برای عنوان ماه، هم برای محتوای نمای تقویم */
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThreshold = with(density) { 48.dp.toPx() }

    val hasGroups = ShiftEngine.activePattern.hasGroups
    val effectiveFilterGroup = if (!hasGroups) GroupFilter.A else s.filterGroup

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    ) {
        val isWideDesktop = maxWidth >= 860.dp && s.viewType == ViewType.GRID

        Column(Modifier.fillMaxSize()) {
            if (!isWideDesktop) {
                if (effectiveFilterGroup != GroupFilter.ALL) {
                    TodayBanner(vm, todayKey, onOpenDay)
                } else {
                    AllTodayBanner(todayKey, onOpenDay)
                }
                Spacer(Modifier.height(8.dp))
            }

            /* ---- Top Control Row: Month Navigation + Segmented Group Capsule + Quick Actions ---- */
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp))
                    .tourTarget("month-nav")
                    .then(
                        Modifier.pointerInput(s.viewYear, s.viewMonth) {
                            var totalX = 0f
                            var totalY = 0f
                            detectDragGestures(
                                onDragStart = { totalX = 0f; totalY = 0f },
                                onDragEnd = {
                                    if (kotlin.math.abs(totalX) > swipeThreshold &&
                                        kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.4f
                                    ) {
                                        goMonth(if (totalX > 0) 1 else -1)
                                    }
                                },
                                onDragCancel = { totalX = 0f; totalY = 0f }
                            ) { change, dragAmount ->
                                change.consume()
                                totalX += dragAmount.x
                                totalY += dragAmount.y
                            }
                        }
                    )
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SkIconButton(SkIcons.ChevronRight, "ماه قبل", iconSize = 18) { goMonth(-1) }
                SkIconButton(SkIcons.Notes, "همه یادداشت‌ها", iconSize = 18) { onOpenNotes() }
                Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                    TitlePill("${JALALI_MONTHS[s.viewMonth - 1]} ${toPersianDigits(s.viewYear)}") { onOpenMonthPicker() }
                }
                SkIconButton(
                    icon = if (s.viewType == ViewType.GRID) SkIcons.Table else SkIcons.Grid,
                    desc = if (s.viewType == ViewType.GRID) "نمای جدولی" else "نمای تقویم",
                    iconSize = 16,
                    modifier = Modifier.tourTarget("view-toggle")
                ) {
                    haptic.tick()
                    vm.setViewType(if (s.viewType == ViewType.GRID) ViewType.TABLE else ViewType.GRID)
                }
                SkIconButton(SkIcons.ChevronLeft, "ماه بعد", iconSize = 18) { goMonth(1) }
            }

            Spacer(Modifier.height(8.dp))

            /* ---- Unified Segmented Capsule for همه / A / B / C / D + Legend + Action Buttons ---- */
            Row(
                Modifier
                    .fillMaxWidth()
                    .tourTarget("group-filter"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val skChip = LocalSk.current
                if (!hasGroups) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(skChip.surfaceStrong)
                            .border(1.5.dp, skChip.primary, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "شیفت فردی (بدون گروه)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = skChip.primary
                        )
                    }
                } else {
                    /* Unified PWA-style Segmented Capsule for همه / A / B / C / D */
                    Row(
                        Modifier
                            .glass(RoundedCornerShape(14.dp))
                            .padding(3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        GROUP_FILTERS.forEach { g ->
                            val active = effectiveFilterGroup == g
                            val isCompared = g != GroupFilter.ALL && compareGroup == g.toShiftGroup()
                            val label = if (g == GroupFilter.ALL) "همه" else g.toShiftGroup().faLetter()
                            @OptIn(ExperimentalFoundationApi::class)
                            Box(
                                Modifier
                                    .height(32.dp)
                                    .widthIn(min = if (g == GroupFilter.ALL) 48.dp else 38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        when {
                                            active -> Modifier
                                                .background(skChip.primarySoft)
                                                .border(1.4.dp, skChip.primary, RoundedCornerShape(10.dp))
                                            isCompared -> Modifier
                                                .background(skChip.restSoft)
                                                .border(1.4.dp, skChip.restInk, RoundedCornerShape(10.dp))
                                            else -> Modifier
                                        }
                                    )
                                    .combinedClickable(
                                        onClick = { setFilter(g) },
                                        onLongClick = {
                                            if (g != GroupFilter.ALL) {
                                                haptic.tick(18)
                                                val baseG = (if (s.filterGroup == GroupFilter.ALL) s.myGroup else s.filterGroup).toShiftGroup()
                                                val targetG = g.toShiftGroup()
                                                if (compareGroup == targetG || targetG == baseG) {
                                                    compareGroup = null
                                                    ToastCenter.show("حالت مقایسهٔ دو شیفت غیرفعال شد")
                                                } else {
                                                    compareGroup = targetG
                                                    ToastCenter.show("مقایسه استراحت مشترک: شیفت ${baseG.faLetter()} و شیفت ${targetG.faLetter()}")
                                                }
                                            }
                                        }
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (active || isCompared) FontWeight.Black else FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = when {
                                        active -> skChip.primary
                                        isCompared -> skChip.restInk
                                        else -> skChip.textMuted
                                    }
                                )
                            }
                        }
                    }
                }

                if (s.viewType == ViewType.GRID && effectiveFilterGroup == GroupFilter.ALL) {
                    Row(
                        Modifier.padding(start = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendDot(LocalSk.current.day, "صبح‌کار")
                        if (ShiftEngine.activePattern.eveningCount > 0) {
                            LegendDot(LocalSk.current.evening, "عصرکار")
                        }
                        LegendDot(LocalSk.current.night, "شب‌کار")
                        LegendDot(LocalSk.current.rest, "استراحت")
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .size(38.dp)
                        .glass(RoundedCornerShape(12.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            exportMonthMode = s.viewType
                            exportMonthOpen = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Image, "خروجی تصویر ماه", Modifier.size(16.dp), tint = LocalSk.current.primary)
                }
                if (s.viewType == ViewType.TABLE) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .glass(RoundedCornerShape(12.dp))
                            .clickableNoRipple { tableFs = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Expand, "جدول تمام‌صفحه", Modifier.size(16.dp), tint = LocalSk.current.primary)
                    }
                }
                if (!isCurrentMonth) {
                    Box(
                        Modifier
                            .height(38.dp)
                            .glass(RoundedCornerShape(12.dp))
                            .clickableNoRipple { goToday() }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(SkIcons.Calendar, "برو به امروز", Modifier.size(15.dp), tint = LocalSk.current.primary)
                            AnimatedVisibility(
                                visible = !chipCollapsed,
                                enter = expandHorizontally(animationSpec = tween(300)) + fadeIn(tween(300)),
                                exit = shrinkHorizontally(animationSpec = tween(300)) + fadeOut(tween(180))
                            ) {
                                Text(
                                    "امروز",
                                    Modifier.padding(start = 6.dp),
                                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                    color = LocalSk.current.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            /* ---- Main Content Area (Wide Desktop Side-by-Side OR Stacked) ---- */
            Crossfade(
                targetState = Triple(s.viewYear, s.viewMonth, s.viewType),
                animationSpec = tween(180),
                label = "month",
                modifier = Modifier.weight(1f)
            ) { (y, m, vt) ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (vt == ViewType.GRID) Modifier.pointerInput(y, m) {
                                var totalX = 0f
                                var totalY = 0f
                                detectDragGestures(
                                    onDragStart = { totalX = 0f; totalY = 0f },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalX) > swipeThreshold &&
                                            kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.4f
                                        ) {
                                            goMonth(if (totalX > 0) 1 else -1)
                                        }
                                    },
                                    onDragCancel = { totalX = 0f; totalY = 0f }
                                ) { change, dragAmount ->
                                    change.consume()
                                    totalX += dragAmount.x
                                    totalY += dragAmount.y
                                }
                            } else Modifier
                        )
                ) {
                    when {
                        vt == ViewType.TABLE -> TableView(dayInfo, effectiveFilterGroup, s.myGroup, onOpenDay)
                        isWideDesktop -> {
                            val summaryGroup = if (effectiveFilterGroup == GroupFilter.ALL) s.myGroup.toShiftGroup() else effectiveFilterGroup.toShiftGroup()
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 82.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                /* Right side (in RTL): Calendar Grid filling 100% of height */
                                Box(
                                    Modifier
                                        .weight(1.95f)
                                        .fillMaxHeight()
                                        .glass(RoundedCornerShape(22.dp))
                                        .padding(10.dp)
                                ) {
                                    GridView(
                                        dayInfo = dayInfo,
                                        firstWeekday = firstWeekday,
                                        filterGroup = effectiveFilterGroup,
                                        myGroup = s.myGroup,
                                        compareGroup = compareGroup,
                                        todayKey = todayKey,
                                        selectedKey = selectedKey,
                                        showBottomSummary = false
                                    ) { k -> selectedKey = k; onOpenDay(k) }
                                }

                                /* Left side (in RTL): Today Shift Banner + Large Monthly Summary Cards */
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (effectiveFilterGroup != GroupFilter.ALL) {
                                        TodayBanner(vm, todayKey, onOpenDay)
                                    } else {
                                        AllTodayBanner(todayKey, onOpenDay)
                                    }

                                    DesktopMonthlyStatsPanel(
                                        monthName = JALALI_MONTHS[s.viewMonth - 1],
                                        year = s.viewYear,
                                        dayInfo = dayInfo,
                                        effGroup = summaryGroup,
                                        myGroup = s.myGroup.toShiftGroup(),
                                        compareGroup = compareGroup,
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )
                                }
                            }
                        }
                        else -> GridView(
                            dayInfo = dayInfo,
                            firstWeekday = firstWeekday,
                            filterGroup = effectiveFilterGroup,
                            myGroup = s.myGroup,
                            compareGroup = compareGroup,
                            todayKey = todayKey,
                            selectedKey = selectedKey,
                            showBottomSummary = true
                        ) { k -> selectedKey = k; onOpenDay(k) }
                    }
                }
            }
        }
    }

    if (tableFs) {
        TableFullscreen(
            viewYear = s.viewYear, viewMonth = s.viewMonth, dayInfo = dayInfo,
            filterGroup = s.filterGroup, myGroup = s.myGroup, todayKey = todayKey, isCurrentMonth = isCurrentMonth,
            onGoMonth = ::goMonth, onGoToday = ::goToday, onOpenDay = onOpenDay,
            onOpenPicker = onOpenMonthPicker,
            onExportMonth = {
                exportMonthMode = ViewType.TABLE
                exportMonthOpen = true
            },
            onClose = { tableFs = false }
        )
    }

    val skForExport = LocalSk.current
    val modeSuffix = if (exportMonthMode == ViewType.TABLE) "table" else "grid"
    ExportImageDialog(
        visible = exportMonthOpen,
        title = "تصویر ${if (exportMonthMode == ViewType.TABLE) "جدول" else "تقویم"} ${JALALI_MONTHS[s.viewMonth - 1]} ${toPersianDigits(s.viewYear)}",
        fileName = "shiftkar-month-${s.viewYear}-${pad2(s.viewMonth)}-${s.filterGroup.name}-$modeSuffix.png",
        hapticOn = s.hapticOn,
        modeKey = exportMonthMode,
        headerExtra = {
            SegmentedControl(
                options = listOf("قالب تقویم", "قالب جدولی"),
                selectedIndex = if (exportMonthMode == ViewType.GRID) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) { idx ->
                haptic.tick()
                exportMonthMode = if (idx == 0) ViewType.GRID else ViewType.TABLE
            }
        },
        produceBitmap = {
            if (exportMonthMode == ViewType.TABLE) {
                ImageExporter.renderMonthTableBitmap(
                    context = context,
                    sk = skForExport,
                    jy = s.viewYear,
                    jm = s.viewMonth,
                    filterGroup = s.filterGroup,
                    dayInfo = dayInfo
                )
            } else {
                ImageExporter.renderMonthBitmap(
                    context = context,
                    sk = skForExport,
                    jy = s.viewYear,
                    jm = s.viewMonth,
                    filterGroup = s.filterGroup,
                    dayInfo = dayInfo,
                    firstWeekday = firstWeekday
                )
            }
        },
        onDismiss = { exportMonthOpen = false }
    )
}

/* ================= title pill ================= */

/**
 * قرص عنوان ماه — v2.2.3: نشانگر.dropdown (caret) به «انتهای قرص» منتقل شد
 * (پاریتی دقیق sk-title-btn در PWA): متن با weight وسط‌چین می‌ماند و caret
 * چسبیده به لبهٔ پایانی قرص است — دیگر به عدد سال نچسبیده.
 */
@Composable
fun TitlePill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sk = LocalSk.current
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(100))
            .background(sk.surfaceStrong)
            .border(1.dp, sk.border, RoundedCornerShape(100))
            .clickableNoRipple { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            Modifier.weight(1f),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = IRANSansX,
            color = sk.text,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Icon(SkIcons.ChevronDown, "انتخاب ماه و سال", Modifier.size(15.dp), tint = sk.primary)
    }
}

/* ================= today banner ================= */

/**
 * بنر امروز — فقط برای فیلتر تک‌گروهی. v6.0.0: شاخهٔ چهارگروهی حذف شد
 * (مالک: «Wtf is this banner!» — در نمای «همه» تقویم خودش همه‌چیز را دارد).
 */
@Composable
private fun TodayBanner(vm: AppViewModel, todayKey: String, onOpenDay: (String) -> Unit) {
    val s = vm.settings.collectAsState().value
    val sk = LocalSk.current
    val today = remember { todayJalaali() }
    val weekday = formatWeekday(today.jy, today.jm, today.jd)
    val dateLine = "$weekday ${toPersianDigits(today.jd)} ${JALALI_MONTHS[today.jm - 1]}"
    val effGroup = s.filterGroup.toShiftGroup()

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .tourTarget("today-banner")
            .clickableNoRipple { onOpenDay(todayKey) }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(dateLine, fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted)
        Spacer(Modifier.height(5.dp))
        val todayRec = s.dayRecords[todayKey]
        val isMineGroup = effGroup == s.myGroup.toShiftGroup()
        val swapped = if (isMineGroup) todayRec?.swapKind else null
        val ot = if (isMineGroup) (todayRec?.overtimeHours ?: 0f) else 0f
        val code = ShiftEngine.getShiftCode(todayKey, effGroup)
        val kind = swapped ?: codeToKind(code)
        val shiftTitle = buildString {
            if (swapped != null) append("جابه‌جایی: ${SHIFT_KIND_FA.getValue(swapped)}")
            else append(CODE_FA.getValue(code))
            if (ot > 0f) append(" (+${toPersianDigits(ot.toInt())}س)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("شیفت امروز: ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IRANSansX, color = sk.text)
                GradientText(shiftTitle, fontSize = 16)
            }
            val kc = kindColors(kind)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(kc.brush),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(kind), shiftTitle, Modifier.size(20.dp), tint = kc.ink)
            }
        }
    }
}

/* ================= today banner (all groups) ================= */

/**
 * v6.0.1 (مالک: «Bring back the banner for the 4 shift view but in a correct
 * view and elegant beauty») — بنر «شیفت امروز» مخصوص نمای چهارگروهی؛ طراحی
 * تازه به‌جای نسخهٔ حذف‌شدهٔ v2.2.9:
 *  • یک کارت شیشه‌ای R16 هم‌زبان با کارت‌های راه‌های ارتباطی و «تور راهنمای
 *    برنامه» — تیتر با آیکون تقویم در جعبهٔ رنگ‌پرایمری + تاریخ کامل امروز.
 *  • چهار کاشی هم‌عرض A/B/C/D، هر کدام با پس‌زمینهٔ ملایمِ همان رنگِ راهنما
 *    (صبح‌کار کهربایی، شب‌کار نیلی، استراحت زمردی) — حرف لاتین گروه، آیکون
 *    نوع شیفت و برچسب «صبح‌کار ۱» سبک v2.2.9، همه با جوهرِ همان رنگ.
 * کل کارت لمس‌پذیر است و برگهٔ همان روز را باز می‌کند.
 */
@Composable
private fun AllTodayBanner(todayKey: String, onOpenDay: (String) -> Unit) {
    val sk = LocalSk.current
    val today = remember { todayJalaali() }
    val weekday = formatWeekday(today.jy, today.jm, today.jd)
    val dateLine = "$weekday ${toPersianDigits(today.jd)} ${JALALI_MONTHS[today.jm - 1]}"

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .tourTarget("today-banner")
            .clickableNoRipple { onOpenDay(todayKey) }
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        /* ---- header: title + full today date ---- */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(sk.primarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.Calendar, null, Modifier.size(14.dp), tint = sk.primary)
            }
            Text(
                "شیفت امروز",
                Modifier.padding(start = 8.dp),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IRANSansX,
                color = sk.text
            )
            Spacer(Modifier.weight(1f))
            Text(dateLine, fontSize = 10.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
        }
        Spacer(Modifier.height(9.dp))
        /* ---- four kind-tinted tiles, A rightmost (RTL) like the chips ---- */
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GROUPS.forEach { g ->
                val code = ShiftEngine.getShiftCode(todayKey, g)
                val kind = codeToKind(code)
                val kc = kindColors(kind)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(kc.brush)
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        g.faLetter(),
                        fontSize = 10.sp, fontWeight = FontWeight.Black,
                        lineHeight = 11.sp, fontFamily = IRANSansX, color = kc.ink
                    )
                    Spacer(Modifier.height(3.dp))
                    Icon(kindIcon(kind), CODE_FA.getValue(code), Modifier.size(15.dp), tint = kc.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CODE_NUM_FA.getValue(code),
                        fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp, fontFamily = IRANSansX, color = kc.ink,
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
        }
    }
}

/* ================= grid ================= */

@Composable
fun GridView(
    dayInfo: List<DayInfo>,
    firstWeekday: Int,
    filterGroup: GroupFilter,
    myGroup: GroupFilter,
    compareGroup: ShiftGroup? = null,
    todayKey: String,
    selectedKey: String?,
    showBottomSummary: Boolean = true,
    onOpenDay: (String) -> Unit
) {
    val sk = LocalSk.current
    val cells: List<DayInfo?> = List(firstWeekday) { null } + dayInfo
    val rows = (cells.size + 6) / 7

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val navClearance = if (showBottomSummary) 80.dp else 0.dp
        val vGap = 5.dp
        val headerAndGaps = 24.dp + vGap * (rows - 1)
        val summaryReserved = if (showBottomSummary) 54.dp else 0.dp
        val availForGrid = (maxHeight - navClearance - headerAndGaps - summaryReserved).coerceAtLeast(240.dp)
        val minCell = 44.dp
        val cellH = (availForGrid / rows).coerceIn(minCell, 128.dp)

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth()) {
                WEEKDAY_SHORT.forEachIndexed { i, w ->
                    Text(
                        w,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IRANSansX,
                        color = if (i == 6) sk.holiday else sk.textMuted
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().height(cellH), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (c in 0 until 7) {
                            val info = cells.getOrNull(r * 7 + c)
                            if (info == null) {
                                Box(Modifier.weight(1f).fillMaxHeight())
                            } else {
                                val isTourCell = info.isToday || (dayInfo.none { it.isToday } && info.d == 14)
                                DayCell(
                                    info = info,
                                    filterGroup = filterGroup,
                                    myGroup = myGroup,
                                    compareGroup = compareGroup,
                                    isSelected = selectedKey == info.key,
                                    onOpenDay = onOpenDay,
                                    introDelayMs = kotlin.math.min((info.d + 3) * 6, 200),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(if (isTourCell) Modifier.tourTarget("today-cell") else Modifier)
                                )
                            }
                        }
                    }
                }
            }
            if (showBottomSummary) {
                Spacer(Modifier.height(8.dp))
                val summaryGroup = if (filterGroup == GroupFilter.ALL) myGroup.toShiftGroup() else filterGroup.toShiftGroup()
                MonthlySummaryStrip(dayInfo = dayInfo, effGroup = summaryGroup, myGroup = myGroup.toShiftGroup(), compareGroup = compareGroup)
                Spacer(Modifier.height(navClearance))
            }
        }
    }
}

private fun computeMonthlyCounts(
    dayInfo: List<DayInfo>,
    effGroup: ShiftGroup,
    myGroup: ShiftGroup,
    compareGroup: ShiftGroup?
): IntArray {
    var day = 0
    var evening = 0
    var night = 0
    var officialHolidays = 0
    var rest = 0
    var sharedRest = 0
    dayInfo.forEach { info ->
        if (info.holiday) officialHolidays++
        val k = if (effGroup == myGroup && info.swapKind != null) info.swapKind else codeToKind(info.all.getValue(effGroup))
        when (k) {
            ShiftKind.DAY -> day++
            ShiftKind.EVENING -> evening++
            ShiftKind.NIGHT -> night++
            ShiftKind.REST -> rest++
        }
        if (compareGroup != null) {
            val k2 = codeToKind(info.all.getValue(compareGroup))
            if (k == ShiftKind.REST && k2 == ShiftKind.REST) sharedRest++
        }
    }
    return intArrayOf(day, evening, night, officialHolidays, rest, sharedRest)
}

@Composable
private fun DesktopMonthlyStatsPanel(
    monthName: String,
    year: Int,
    dayInfo: List<DayInfo>,
    effGroup: ShiftGroup,
    myGroup: ShiftGroup,
    compareGroup: ShiftGroup?,
    modifier: Modifier = Modifier
) {
    val sk = LocalSk.current
    val counts = remember(dayInfo, effGroup, myGroup, compareGroup) {
        computeMonthlyCounts(dayInfo, effGroup, myGroup, compareGroup)
    }
    val restLabel = if (compareGroup != null) "استراحت مشترک ${effGroup.faLetter()}∩${compareGroup.faLetter()}" else "استراحت"
    val restValue = if (compareGroup != null) counts[5] else counts[4]

    val items = buildList {
        add(Triple("صبح‌کار", counts[0], Triple(sk.daySoft, sk.dayInk, SkIcons.Sun)))
        if (counts[1] > 0) {
            add(Triple("عصرکار", counts[1], Triple(sk.eveningSoft, sk.eveningInk, SkIcons.Sunset)))
        }
        add(Triple("شب‌کار", counts[2], Triple(sk.nightSoft, sk.nightInk, SkIcons.Moon)))
        add(Triple("تعطیل رسمی", counts[3], Triple(sk.holidaySoft, sk.holiday, SkIcons.Holiday)))
        add(Triple(restLabel, restValue, Triple(sk.restSoft, sk.restInk, SkIcons.Rest)))
    }

    Column(
        modifier
            .tourTarget("summary-strip")
            .glass(RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "آمار ماهانه ($monthName ${toPersianDigits(year)})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.text
                )
                Text(
                    "مجموع ${toPersianDigits(dayInfo.size)} روز · ${GROUP_FA.getValue(effGroup)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(100))
                    .background(sk.primarySoft)
                    .border(1.dp, sk.primary.copy(alpha = 0.4f), RoundedCornerShape(100))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    GROUP_FA.getValue(effGroup),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.chunked(2).forEach { rowItems ->
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { (label, count, style) ->
                        val (bg, ink, icon) = style
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(18.dp))
                                .background(bg)
                                .border(1.2.dp, ink.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ink.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, null, Modifier.size(20.dp), tint = ink)
                                }
                                Text(
                                    "${toPersianDigits(count)} روز",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = IRANSansX,
                                    color = ink
                                )
                            }
                            Text(
                                label,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = IRANSansX,
                                color = ink,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryStrip(
    dayInfo: List<DayInfo>,
    effGroup: ShiftGroup,
    myGroup: ShiftGroup = effGroup,
    compareGroup: ShiftGroup? = null
) {
    val sk = LocalSk.current
    val counts = remember(dayInfo, effGroup, myGroup, compareGroup) {
        computeMonthlyCounts(dayInfo, effGroup, myGroup, compareGroup)
    }

    val restLabel = if (compareGroup != null) "مشترک ${effGroup.faLetter()}∩${compareGroup.faLetter()}" else "استراحت"
    val restValue = if (compareGroup != null) counts[5] else counts[4]

    val items = buildList {
        add(Triple("صبح‌کار", counts[0], Triple(sk.daySoft, sk.dayInk, SkIcons.Sun)))
        if (counts[1] > 0) {
            add(Triple("عصرکار", counts[1], Triple(sk.eveningSoft, sk.eveningInk, SkIcons.Sunset)))
        }
        add(Triple("شب‌کار", counts[2], Triple(sk.nightSoft, sk.nightInk, SkIcons.Moon)))
        add(Triple("تعطیل", counts[3], Triple(sk.holidaySoft, sk.holiday, SkIcons.Holiday)))
        add(Triple(restLabel, restValue, Triple(sk.restSoft, sk.restInk, SkIcons.Rest)))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .tourTarget("summary-strip")
            .glass(RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, count, style) ->
            val (bg, ink, icon) = style
            Row(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, ink.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(14.dp), tint = ink)
                Text(
                    "$label ${toPersianDigits(count)}",
                    Modifier.padding(start = 5.dp),
                    fontSize = 11.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = IRANSansX,
                    color = ink,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    info: DayInfo,
    filterGroup: GroupFilter,
    myGroup: GroupFilter = filterGroup,
    compareGroup: ShiftGroup? = null,
    isSelected: Boolean,
    onOpenDay: (String) -> Unit,
    introDelayMs: Int = 0,
    modifier: Modifier = Modifier
) {
    val sk = LocalSk.current
    val holiday = info.holiday
    val effShiftGroup = (if (filterGroup == GroupFilter.ALL) myGroup else filterGroup).toShiftGroup()
    val kind = if (effShiftGroup == myGroup.toShiftGroup() && info.swapKind != null) {
        info.swapKind
    } else {
        codeToKind(info.all.getValue(effShiftGroup))
    }
    val isSharedRest = compareGroup != null &&
        kind == ShiftKind.REST &&
        codeToKind(info.all.getValue(compareGroup)) == ShiftKind.REST

    /* v2.2.3: ورود پلکانی سلول‌ها هنگام تعویض ماه — پاریتی PWA (sk-cell-in):
     * فقط opacity (بدون transform تا پس‌زمینهٔ شیشه‌ای دچار پرش نشود)،
     * تأخیر (d+3)×۶ms حداکثر ۲۰۰ms، فید ۱۶۰ms. Crossfade تعویض ماه زیردرخت
     * را از نو می‌سازد، پس هر ماه یک‌بار اجرا می‌شود؛ تغییر فیلتر گروه
     * سلول‌ها را از نو نمی‌سازد و انیمیشن دوباره اجرا نمی‌شود (درست مثل وب). */
    val intro = remember { Animatable(0f) }
    LaunchedEffect(info.key) {
        kotlinx.coroutines.delay(introDelayMs.toLong())
        intro.animateTo(1f, tween(160))
    }

    /* v2.2.1: کمی گردتر (12 → 14dp) */
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .graphicsLayer { alpha = intro.value }
            .then(
                if (holiday) Modifier
                    .clip(shape)
                    .background(sk.holidaySoft)
                    .border(1.dp, sk.holiday.copy(alpha = 0.2f), shape)
                else Modifier.skTile(shape)
            )
            .then(
                when {
                    info.isToday -> Modifier.border(2.dp, sk.primary, shape)
                    isSharedRest -> Modifier.border(1.8.dp, sk.restInk, shape)
                    isSelected -> Modifier.border(2.dp, sk.primary.copy(alpha = 0.4f), shape)
                    else -> Modifier
                }
            )
            .clickableNoRipple { onOpenDay(info.key) }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                toPersianDigits(info.d),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = when {
                    holiday -> sk.holiday
                    info.isFriday -> sk.textMuted
                    else -> sk.text
                }
            )
            if (info.note != null) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(sk.primary))
            }
            if (info.overtimeHours > 0f || info.swapKind != null) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(sk.primary))
            }
            if (info.hasColleagueSharedRest) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(sk.restInk))
            }
        }
        Spacer(Modifier.height(if (filterGroup == GroupFilter.ALL) 2.dp else 4.dp))
        if (filterGroup == GroupFilter.ALL) {
            /* شبکهٔ ۲×۲ نشان‌های عددی — هر چهار گروه (A,B / C,D در ترتیب راست‌به‌چپ)
             * داخل عرض سلول جا می‌شوند؛ قبلاً ۴ حرف در یک ردیف جا نمی‌شد و گروه
             * D از عرض بیرون می‌زد (گزارش «اعداد در سلول‌ها ناقص‌اند»). */
            val colsPerRow = if (GROUPS.size > 4) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GROUPS.take(6).chunked(colsPerRow).forEach { rowGroups ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowGroups.forEach { g ->
                            GroupNumBadge(g, info.all.getValue(g))
                        }
                    }
                }
            }
        } else {
            val cellCode = info.all.getValue(effShiftGroup)
            val isSwapped = effShiftGroup == myGroup.toShiftGroup() && info.swapKind != null
            ShiftDayOrdinalBadge(
                kind = kind,
                ordinal = shiftCodeIndex(cellCode),
                isSwapped = isSwapped
            )
        }
    }
}

/* ================= table ================= */

/**
 * v6.0.0 (مالک: «make horizontally scrolling work on the table view and the
 * full screen table view»): عرض جدول = عرض واقعیِ بلندترین متنِ «مناسبت و
 * یادداشت» + ستون‌های ثابت. سطرها تک‌خطی می‌مانند و متن بلند با کشیدن افقی
 * خوانده می‌شود — حداقل عرض ۴۸۰dp.
 */
@Composable
private fun rememberTableWidth(dayInfo: List<DayInfo>): Dp {
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(dayInfo, density) {
        val noteStyle = TextStyle(fontSize = 11.5.sp, fontFamily = IRANSansX)
        var maxPx = 0f
        dayInfo.forEach { info ->
            val h = info.holidayNames.joinToString("، ")
            if (h.isNotEmpty()) {
                maxPx = maxOf(maxPx, tm.measure(h, noteStyle.copy(fontWeight = FontWeight.Bold)).size.width.toFloat())
            }
            val occ = info.occasions.joinToString("، ")
            if (occ.isNotEmpty()) {
                maxPx = maxOf(maxPx, tm.measure(occ, noteStyle).size.width.toFloat())
            }
            if (info.overtimeHours > 0f || info.swapKind != null) {
                val recStr = buildList {
                    if (info.overtimeHours > 0f) add("اضافه‌کاری: ${toPersianDigits(info.overtimeHours.toInt())} ساعت")
                    if (info.swapKind != null) add("جابه‌جایی: ${SHIFT_KIND_FA.getValue(info.swapKind)}")
                }.joinToString(" · ")
                maxPx = maxOf(maxPx, tm.measure(recStr, noteStyle.copy(fontWeight = FontWeight.Bold)).size.width.toFloat())
            }
            info.note?.let { n ->
                maxPx = maxOf(maxPx, tm.measure(n, noteStyle).size.width.toFloat())
            }
        }
        val noteW = with(density) { maxPx.toDp() }
        (48 + 64 + 96 + 12 + 8).dp + noteW + 24.dp
    }.coerceAtLeast(480.dp)
}

@Composable
fun TableView(
    dayInfo: List<DayInfo>,
    filterGroup: GroupFilter,
    myGroup: GroupFilter = filterGroup,
    onOpenDay: (String) -> Unit
) {
    val sk = LocalSk.current
    val all = filterGroup == GroupFilter.ALL
    val tableW = rememberTableWidth(dayInfo)

    Column(
        Modifier
            .fillMaxSize()
            .cardSoft(RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (all) {
                LegendDot(sk.day, "صبح‌کار")
                if (ShiftEngine.activePattern.eveningCount > 0) {
                    LegendDot(sk.evening, "عصرکار")
                }
                LegendDot(sk.night, "شب‌کار")
                LegendDot(sk.rest, "استراحت")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(SkIcons.Info, null, Modifier.size(13.dp), tint = sk.textFaint)
                    Text("برای جزئیات، روی هر روز ضربه بزنید", fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
                }
            }
        }
        /* v6.0.0: اسکرول افقی — سرستون و ردیف‌ها با هم می‌لغزند */
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column {
                // header
                val headerBg = sk.glassHi
                Row(
                    Modifier
                        .width(tableW)
                        .background(headerBg)
                        .padding(vertical = 8.dp)
                ) {
                    Text("روز", Modifier.width(48.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                    Text("هفته", Modifier.width(64.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                    Text(if (all) "شیفت گروه‌ها" else "شیفت", Modifier.width(96.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                    Text("مناسبت و یادداشت", Modifier.weight(1f).padding(start = 12.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                }
                /* v6.0.3: هم‌ترازِ GridView — آخرین ردیف جدول هم باید بتواند از
                 * زیر نوار شیشه‌ای پایین رد شود (contentPadding = 88dp). */
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(dayInfo.size) { i ->
                        val info = dayInfo[i]
                        val holiday = info.holiday
                        val effShiftGroup = (if (all) myGroup else filterGroup).toShiftGroup()
                        val isMineSelected = effShiftGroup == myGroup.toShiftGroup()
                        val swappedKind = if (isMineSelected) info.swapKind else null
                        val kind = swappedKind ?: codeToKind(info.all.getValue(effShiftGroup))
                        val kc = kindColors(kind)
                        Row(
                            Modifier
                                .width(tableW)
                                .then(when {
                                    holiday -> Modifier.background(sk.holidaySoft.copy(alpha = 0.6f))
                                    info.isFriday -> Modifier.background(sk.surfaceStrong.copy(alpha = 0.35f))
                                    else -> Modifier
                                })
                                .then(
                                    if (info.isToday) Modifier.background(sk.primarySoft)
                                    else Modifier
                                )
                                .clickableNoRipple { onOpenDay(info.key) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                toPersianDigits(info.d.toString().padStart(2, '۰')),
                                Modifier.width(48.dp), textAlign = TextAlign.Center,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                color = when {
                                    info.isToday -> sk.primary
                                    holiday -> sk.holiday
                                    else -> sk.text
                                }
                            )
                            Text(
                                WEEKDAYS[info.weekday],
                                Modifier.width(64.dp), textAlign = TextAlign.Center,
                                fontSize = 12.sp, fontFamily = IRANSansX,
                                color = if (holiday) sk.holiday else sk.textMuted
                            )
                            Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                                if (all) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        GROUPS.forEach { g ->
                                            val gKind = if (g == myGroup.toShiftGroup() && info.swapKind != null) info.swapKind else codeToKind(info.all.getValue(g))
                                            MiniBadge(g, gKind)
                                        }
                                    }
                                } else {
                                    val badgeText = when (swappedKind) {
                                        ShiftKind.DAY -> "صبح‌کار*"
                                        ShiftKind.EVENING -> "عصرکار*"
                                        ShiftKind.NIGHT -> "شب‌کار*"
                                        ShiftKind.REST -> "استراحت*"
                                        null -> CODE_NUM_FA.getValue(info.all.getValue(effShiftGroup))
                                    }
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(kc.brush)
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            badgeText,
                                            fontSize = 10.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = kc.ink
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                if (info.overtimeHours > 0f || info.swapKind != null) {
                                    val recLine = buildList {
                                        if (info.overtimeHours > 0f) add("اضافه‌کاری: ${toPersianDigits(info.overtimeHours.toInt())} ساعت")
                                        if (info.swapKind != null) add("جابه‌جایی: ${SHIFT_KIND_FA.getValue(info.swapKind)}")
                                    }.joinToString(" · ")
                                    Text(
                                        recLine,
                                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.primary,
                                        maxLines = 1, softWrap = false
                                    )
                                }
                                if (info.holidayNames.isNotEmpty()) {
                                    Text(
                                        info.holidayNames.joinToString("، "),
                                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.holiday,
                                        maxLines = 1, softWrap = false
                                    )
                                }
                                if (info.occasions.isNotEmpty()) {
                                    Text(
                                        info.occasions.joinToString("، "),
                                        fontSize = 11.sp, fontFamily = IRANSansX,
                                        color = sk.textMuted,
                                        maxLines = 1, softWrap = false
                                    )
                                }
                                if (info.colleagueSummary.isNotEmpty()) {
                                    Text(
                                        "همکاران: ${info.colleagueSummary}",
                                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.restInk,
                                        maxLines = 1, softWrap = false
                                    )
                                }
                                if (info.note != null) {
                                    Text(
                                        info.note,
                                        fontSize = 11.5.sp, fontFamily = IRANSansX,
                                        color = sk.textMuted,
                                        maxLines = 1, softWrap = false
                                    )
                                }
                                if (info.overtimeHours <= 0f && info.swapKind == null && info.holidayNames.isEmpty() && info.occasions.isEmpty() && info.note == null && info.colleagueSummary.isEmpty()) {
                                    Text("—", fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.5.sp, fontFamily = IRANSansX, color = LocalSk.current.textMuted)
    }
}

/* ================= fullscreen table ================= */

@Composable
private fun TableFullscreen(
    viewYear: Int, viewMonth: Int, dayInfo: List<DayInfo>, filterGroup: GroupFilter,
    myGroup: GroupFilter = filterGroup, todayKey: String, isCurrentMonth: Boolean,
    onGoMonth: (Int) -> Unit, onGoToday: () -> Unit, onOpenDay: (String) -> Unit,
    onOpenPicker: () -> Unit, onExportMonth: () -> Unit, onClose: () -> Unit
) {
    val sk = LocalSk.current
    val all = filterGroup == GroupFilter.ALL
    val tableW = rememberTableWidth(dayInfo)


    Box(Modifier.fillMaxSize().background(sk.background)) {
        AuroraBackground(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glass(RoundedCornerShape(16.dp))
                    .padding(6.dp)
                    /* v2.2.9: کشیدن افقی روی «عنوان» برای تعویض ماه — v6.0.0:
                     * محتوای جدول هم اسکرول افقی خودش را دارد. */
                    .then(
                        Modifier.pointerInput(viewYear, viewMonth) {
                            var totalX = 0f
                            var totalY = 0f
                            val threshold = 48.dp.toPx()
                            detectDragGestures(
                                onDragStart = { totalX = 0f; totalY = 0f },
                                onDragEnd = {
                                    if (kotlin.math.abs(totalX) > threshold &&
                                        kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.4f
                                    ) {
                                        onGoMonth(if (totalX > 0) 1 else -1)
                                    }
                                },
                                onDragCancel = { totalX = 0f; totalY = 0f }
                            ) { change, dragAmount ->
                                change.consume()
                                totalX += dragAmount.x
                                totalY += dragAmount.y
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SkIconButton(SkIcons.ChevronRight, "ماه قبل", iconSize = 18) { onGoMonth(-1) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TitlePill("${JALALI_MONTHS[viewMonth - 1]} ${toPersianDigits(viewYear)}") { onOpenPicker() }
                }
                SkIconButton(SkIcons.ChevronLeft, "ماه بعد", iconSize = 18) { onGoMonth(1) }
                SkIconButton(SkIcons.Image, "خروجی تصویر ماه", iconSize = 17, tint = sk.primary) { onExportMonth() }
                SkIconButton(SkIcons.Collapse, "بازگشت به نمای تقویم", iconSize = 17, tint = sk.primary) { onClose() }
            }

            if (all) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(sk.day, "صبح‌کار")
                    if (ShiftEngine.activePattern.eveningCount > 0) {
                        Spacer(Modifier.width(16.dp))
                        LegendDot(sk.evening, "عصرکار")
                    }
                    Spacer(Modifier.width(16.dp))
                    LegendDot(sk.night, "شب‌کار")
                    Spacer(Modifier.width(16.dp))
                    LegendDot(sk.rest, "استراحت")
                }
            }

            Spacer(Modifier.height(8.dp))

            /* v6.0.0 (مالک: «make horizontally scrolling work on the full
             * screen table view»): سرستون + ردیف‌ها داخل اسکرول افقی؛ سطرها
             * تک‌خطی و متن بلند با کشیدن قابل خواندن. */
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .cardSoft(RoundedCornerShape(16.dp))
            ) {
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    Column {
                        Row(
                            Modifier
                                .width(tableW)
                                .background(sk.glassHi)
                                .padding(vertical = 10.dp)
                        ) {
                            Text("روز", Modifier.width(48.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                            Text("هفته", Modifier.width(64.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                            Text(if (all) "شیفت گروه‌ها" else "شیفت", Modifier.width(96.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                            Text("مناسبت و یادداشت", Modifier.weight(1f).padding(start = 12.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(dayInfo.size) { i ->
                                val info = dayInfo[i]
                                val holiday = info.holiday
                                val effShiftGroup = (if (all) myGroup else filterGroup).toShiftGroup()
                                val isMineSelected = effShiftGroup == myGroup.toShiftGroup()
                                val swappedKind = if (isMineSelected) info.swapKind else null
                                val kind = swappedKind ?: codeToKind(info.all.getValue(effShiftGroup))
                                val kc = kindColors(kind)
                                Row(
                                    Modifier
                                        .width(tableW)
                                        .then(when {
                                    holiday -> Modifier.background(sk.holidaySoft.copy(alpha = 0.6f))
                                    info.isFriday -> Modifier.background(sk.surfaceStrong.copy(alpha = 0.35f))
                                    else -> Modifier
                                })
                                        .then(if (info.isToday) Modifier.background(sk.primarySoft) else Modifier)
                                        .clickableNoRipple { onOpenDay(info.key) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        toPersianDigits(info.d.toString().padStart(2, '۰')),
                                        Modifier.width(48.dp), textAlign = TextAlign.Center,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = when {
                                            info.isToday -> sk.primary
                                            holiday -> sk.holiday
                                            else -> sk.text
                                        }
                                    )
                                    Text(
                                        WEEKDAYS[info.weekday],
                                        Modifier.width(64.dp), textAlign = TextAlign.Center,
                                        fontSize = 12.sp, fontFamily = IRANSansX,
                                        color = if (holiday) sk.holiday else sk.textMuted
                                    )
                                    Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                                        if (all) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                GROUPS.forEach { g ->
                                                    val gKind = if (g == myGroup.toShiftGroup() && info.swapKind != null) info.swapKind else codeToKind(info.all.getValue(g))
                                                    MiniBadge(g, gKind)
                                                }
                                            }
                                        } else {
                                            val badgeText = when (swappedKind) {
                                                ShiftKind.DAY -> "صبح‌کار*"
                                                ShiftKind.EVENING -> "عصرکار*"
                                                ShiftKind.NIGHT -> "شب‌کار*"
                                                ShiftKind.REST -> "استراحت*"
                                                null -> CODE_NUM_FA.getValue(info.all.getValue(effShiftGroup))
                                            }
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(kc.brush)
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    badgeText,
                                                    fontSize = 10.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = kc.ink
                                                )
                                            }
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                                        if (info.overtimeHours > 0f || info.swapKind != null) {
                                            val recLine = buildList {
                                                if (info.overtimeHours > 0f) add("اضافه‌کاری: ${toPersianDigits(info.overtimeHours.toInt())} ساعت")
                                                if (info.swapKind != null) add("جابه‌جایی: ${SHIFT_KIND_FA.getValue(info.swapKind)}")
                                            }.joinToString(" · ")
                                            Text(
                                                recLine,
                                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                                color = sk.primary,
                                                maxLines = 1, softWrap = false
                                            )
                                        }
                                        if (info.holidayNames.isNotEmpty()) {
                                            Text(
                                                info.holidayNames.joinToString("، "),
                                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                                color = sk.holiday,
                                                maxLines = 1, softWrap = false
                                            )
                                        }
                                        if (info.occasions.isNotEmpty()) {
                                            Text(
                                                info.occasions.joinToString("، "),
                                                fontSize = 11.sp, fontFamily = IRANSansX,
                                                color = sk.textMuted,
                                                maxLines = 1, softWrap = false
                                            )
                                        }
                                        if (info.note != null) {
                                            Text(
                                                info.note,
                                                fontSize = 11.5.sp, fontFamily = IRANSansX,
                                                color = sk.textMuted,
                                                maxLines = 1, softWrap = false
                                            )
                                        }
                                        if (info.overtimeHours <= 0f && info.swapKind == null && info.holidayNames.isEmpty() && info.occasions.isEmpty() && info.note == null) {
                                            Text("—", fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!isCurrentMonth) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(sk.primary)
                    .clickableNoRipple { onGoToday() },
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.Calendar, "برو به امروز", Modifier.size(19.dp), tint = Color.White)
            }
        }
    }
}
