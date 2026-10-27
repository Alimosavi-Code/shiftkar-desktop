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
import androidx.compose.foundation.clickable
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
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.tourTarget

private val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

data class DayInfo(
    val d: Int, val key: String, val holiday: Boolean, val holidayNames: List<String>,
    val occasions: List<String>, val isToday: Boolean, val isFriday: Boolean,
    val weekday: Int, val label: String, val note: String?,
    val all: Map<ShiftGroup, ShiftCode>
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
            // «همه» is a VIEW filter only — the personal group in Settings stays untouched
            vm.set { it.copy(filterGroup = GroupFilter.ALL) }
        } else {
            vm.set { it.copy(filterGroup = g, myGroup = g) }
        }
    }

    /* ---- month data (shared by grid + table) ---- */
    val monthLen = jalaaliMonthLength(s.viewYear, s.viewMonth)
    val firstWeekday = jalaaliWeekday(s.viewYear, s.viewMonth, 1)
    val dayInfo = remember(s.viewYear, s.viewMonth, notes, s.showEvents) {
        (1..monthLen).map { d ->
            val key = makeDateKey(s.viewYear, s.viewMonth, d)
            val events = if (s.showEvents) Holidays.getDayEvents(s.viewYear, s.viewMonth, d) else emptyList()
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
                all = ShiftEngine.calculateAllShifts(key).associate { it.group to it.code }
            )
        }
    }

    /* v2.2.9: آستانهٔ ژست افقی — هم برای عنوان ماه، هم برای محتوای نمای تقویم */
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThreshold = with(density) { 48.dp.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        /* v2.2.4: bottom clearance owned by the screen again — the floating
         * glass bar is back (owner reverted the v2.2.3 reserved-slot look) */
    ) {
        /* v6.0.1 (مالک: «Bring back the banner for the 4 shift view but in
         * a correct view and elegant beauty»): بنر «شیفت امروز» به نمای «همه»
         * برگشت — این بار یک کارت شیشه‌ای آراسته با چهار کاشیِ هم‌رنگِ راهنما
         * (کهربایی/نیلی/زمردی) برای گروه‌های A/B/C/D؛ بنر تک‌گروهی هم سر جایش. */
        if (s.filterGroup != GroupFilter.ALL) {
            TodayBanner(vm, todayKey, onOpenDay)
        } else {
            AllTodayBanner(todayKey, onOpenDay)
        }
        Spacer(Modifier.height(10.dp))

        /* ---- month navigation row ----
         * v2.2.1: چیدمان سامان‌یافته — هر چهار دکمه 40dp، عنوان ماه همیشه با
         * عرض ثابت (weight) و متن وسط‌چین؛ قبلاً با نام‌های کوتاه (دی) فاصله‌ها
         * بی‌نظم می‌شد و آیکون یادداشت/نمای جدولی به عنوان چسبیده به نظر می‌رسید.
         * v2.2.9: ژستِ کشیدن افقی برای عوض کردن ماه روی همین «عنوان» هم نشست —
         * در نمای جدولی محتوا دیگر کشیده نمی‌شود (جدول اسکرول عمودی دارد). */
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
                                    // RTL convention: dx > 0 → next month (same as the PWA)
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

        Spacer(Modifier.height(10.dp))

        /* ---- filter chips + fullscreen + today chip ---- */
        Row(
            Modifier
                .fillMaxWidth()
                .tourTarget("group-filter"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GROUP_FILTERS.forEach { g ->
                val active = s.filterGroup == g
                val label = if (g == GroupFilter.ALL) "همه" else g.toShiftGroup().faLetter() // v2.2.4: حرف فارسی
                val skChip = LocalSk.current
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .then(
                            if (active) Modifier
                                .background(skChip.surfaceStrong)
                                .border(1.5.dp, skChip.primary, RoundedCornerShape(100))
                            else Modifier.glass(RoundedCornerShape(100))
                        )
                        .clickableNoRipple { setFilter(g) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = if (active) skChip.primary else skChip.textMuted
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (s.viewType == ViewType.TABLE) {
                Box(
                    Modifier
                        .size(36.dp)
                        .glass(CircleShape)
                        .clickableNoRipple { tableFs = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Expand, "جدول تمام‌صفحه", Modifier.size(16.dp), tint = LocalSk.current.primary)
                }
            }
            if (!isCurrentMonth) {
                /* v2.2.3: چیپ «امروز» بعد از ~۲.۴ ثانیه (PWA hold) خودش کوچک
                 * می‌شود — فقط آیکون در قرص گرد، با انیمیشن افقی نرم؛
                 * دوباره با عوض کردن ماه برمی‌گردد. */
                Box(
                    Modifier
                        .height(36.dp)
                        .glass(CircleShape)
                        .clickableNoRipple { goToday() }
                        .padding(horizontal = 10.5.dp),
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
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = IRANSansX,
                                color = LocalSk.current.primary
                            )
                        }
                    }
                }
            }
        }

        /* ---- legend (grid + ALL) ---- */
        if (s.viewType == ViewType.GRID && s.filterGroup == GroupFilter.ALL) {
            Row(
                Modifier.padding(top = 8.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(LocalSk.current.day, "صبح‌کار")
                LegendDot(LocalSk.current.night, "شب‌کار")
                LegendDot(LocalSk.current.rest, "استراحت")
            }
        }

        Spacer(Modifier.height(8.dp))

        /* ---- swipeable body (RTL: swipe RIGHT → next month, LEFT → prev) ----
         * v2.2.9: ژست افقی فقط برای «نمای تقویم» روی محتوا می‌ماند؛ نمای جدولی
         * اسکرول عمودیِ آزاد دارد و تعویض ماه از عنوان انجام می‌شود. */
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
                                        // RTL convention: dx > 0 → next month (same as the PWA)
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
                when (vt) {
                    ViewType.GRID -> GridView(dayInfo, firstWeekday, s.filterGroup, todayKey, selectedKey) { k -> selectedKey = k; onOpenDay(k) }
                    ViewType.TABLE -> TableView(dayInfo, s.filterGroup, onOpenDay)
                }
            }
        }
    }

    if (tableFs) {
        TableFullscreen(
            viewYear = s.viewYear, viewMonth = s.viewMonth, dayInfo = dayInfo,
            filterGroup = s.filterGroup, todayKey = todayKey, isCurrentMonth = isCurrentMonth,
            onGoMonth = ::goMonth, onGoToday = ::goToday, onOpenDay = onOpenDay,
            onOpenPicker = onOpenMonthPicker, onClose = { tableFs = false }
        )
    }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("شیفت امروز: ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IRANSansX, color = sk.text)
                GradientText(CODE_FA.getValue(ShiftEngine.getShiftCode(todayKey, effGroup)), fontSize = 16)
            }
            val kind = codeToKind(ShiftEngine.getShiftCode(todayKey, effGroup))
            val kc = kindColors(kind)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(kc.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(kind), CODE_FA.getValue(ShiftEngine.getShiftCode(todayKey, effGroup)), Modifier.size(20.dp), tint = kc.ink)
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
                        .background(kc.bg)
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
    todayKey: String,
    selectedKey: String?,
    onOpenDay: (String) -> Unit
) {
    val sk = LocalSk.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight.value
        val cellH = ((screenH * 0.084f).coerceIn(52f, 66f)).dp

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEachIndexed { i, w ->
                Text(
                    w,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = if (i == 6) sk.holiday else sk.textFaint
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val cells: List<DayInfo?> = List(firstWeekday) { null } + dayInfo
        val rows = (cells.size + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().height(cellH), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val info = cells.getOrNull(r * 7 + c)
                        if (info == null) {
                            Box(Modifier.weight(1f).fillMaxHeight())
                        } else {
                            DayCell(
                                info = info, filterGroup = filterGroup,
                                isSelected = selectedKey == info.key,
                                onOpenDay = onOpenDay,
                                /* v2.2.3: تأخیر پلکانی ورود — دقیقاً فرمول PWA */
                                introDelayMs = kotlin.math.min((info.d + 3) * 6, 200),
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
        /* v6.0.3 (مالک: «fixing the bottom bar interfere with other element»):
         * فاصلهٔ انتهایی اسکرول از ۸ به ۸۸dp — نوار شیشه‌ای شناور ~۸۸dp بالای
         * لبهٔ امن می‌نشیند؛ با ۸dp آخرین ردیف تقویم همیشه پشت نوار گیر می‌کرد. */
        Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun DayCell(
    info: DayInfo,
    filterGroup: GroupFilter,
    isSelected: Boolean,
    onOpenDay: (String) -> Unit,
    introDelayMs: Int = 0,
    modifier: Modifier = Modifier
) {
    val sk = LocalSk.current
    val holiday = info.holiday || info.isFriday
    val effGroup = if (filterGroup == GroupFilter.ALL) GroupFilter.A else filterGroup
    val kind = codeToKind(info.all.getValue(effGroup.toShiftGroup()))

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
                if (info.isToday) Modifier.border(2.dp, sk.primary, shape)
                else if (isSelected) Modifier.border(2.dp, sk.primary.copy(alpha = 0.4f), shape)
                else Modifier
            )
            .clickableNoRipple { onOpenDay(info.key) }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                toPersianDigits(info.d),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = if (holiday) sk.holiday else sk.text
            )
            if (info.note != null) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(sk.noteInk))
            }
        }
        Spacer(Modifier.height(4.dp))
        if (filterGroup == GroupFilter.ALL) {
            /* شبکهٔ ۲×۲ نشان‌های عددی — هر چهار گروه (A,B / C,D در ترتیب راست‌به‌چپ)
             * داخل عرض سلول جا می‌شوند؛ قبلاً ۴ حرف در یک ردیف جا نمی‌شد و گروه
             * D از عرض بیرون می‌زد (گزارش «اعداد در سلول‌ها ناقص‌اند»). */
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GROUPS.chunked(2).forEach { rowGroups ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowGroups.forEach { g ->
                            GroupNumBadge(g, info.all.getValue(g))
                        }
                    }
                }
            }
        } else {
            val kc = kindColors(kind)
            Box(
                Modifier
                    .size(19.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(kc.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(kind, 13), null, Modifier.size(13.dp), tint = kc.ink)
            }
        }
    }
}

/* ================= table (Desktop Full-Width Vertical Scroll) ================= */

@Composable
fun TableView(
    dayInfo: List<DayInfo>,
    filterGroup: GroupFilter,
    onOpenDay: (String) -> Unit
) {
    val sk = LocalSk.current
    val all = filterGroup == GroupFilter.ALL

    Column(
        Modifier
            .fillMaxSize()
            .cardSoft(RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (all) {
                LegendDot(sk.day, "صبح‌کار")
                LegendDot(sk.night, "شب‌کار")
                LegendDot(sk.rest, "استراحت")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(SkIcons.Info, null, Modifier.size(13.dp), tint = sk.textFaint)
                    Text("برای مشاهدهٔ جزئیات و ثبت یادداشت، روی هر روز کلیک کنید", fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
                }
            }
        }

        // Full-width sticky header
        Row(
            Modifier
                .fillMaxWidth()
                .background(sk.glassHi)
                .padding(vertical = 9.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("روز", Modifier.width(64.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
            Text("روز هفته", Modifier.width(96.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
            Text(if (all) "شیفت گروه‌ها" else "وضعیت شیفت", Modifier.width(148.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
            Text("مناسبت‌ها و یادداشت روزانه", Modifier.weight(1f).padding(start = 16.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
        }

        // Pure vertical LazyColumn — smooth mouse wheel scrolling without horizontal scroll interference
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(dayInfo.size) { i ->
                val info = dayInfo[i]
                val holiday = info.holiday || info.isFriday
                val effGroup = if (all) GroupFilter.A else filterGroup
                val code = info.all.getValue(effGroup.toShiftGroup())
                val kind = codeToKind(code)
                val kc = kindColors(kind)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (holiday) Modifier.background(sk.holidaySoft.copy(alpha = 0.6f)) else Modifier)
                        .then(if (info.isToday) Modifier.background(sk.primarySoft) else Modifier)
                        .clickableNoRipple { onOpenDay(info.key) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        toPersianDigits(info.d.toString().padStart(2, '۰')),
                        Modifier.width(64.dp), textAlign = TextAlign.Center,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                        color = when {
                            info.isToday -> sk.primary
                            holiday -> sk.holiday
                            else -> sk.text
                        }
                    )
                    Text(
                        WEEKDAYS[info.weekday],
                        Modifier.width(96.dp), textAlign = TextAlign.Center,
                        fontSize = 12.5.sp, fontFamily = IRANSansX,
                        color = if (holiday) sk.holiday else sk.textMuted
                    )
                    Box(Modifier.width(148.dp), contentAlignment = Alignment.Center) {
                        if (all) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                GROUPS.forEach { g -> MiniBadge(g, codeToKind(info.all.getValue(g))) }
                            }
                        } else {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(kc.bg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${CODE_FA.getValue(code)} (${code.name})",
                                    fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = kc.ink
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
                        if (info.holidayNames.isNotEmpty()) {
                            Text(
                                info.holidayNames.joinToString("، "),
                                fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                color = sk.holiday,
                                maxLines = 2, softWrap = true
                            )
                        }
                        if (info.note != null) {
                            Text(
                                info.note,
                                fontSize = 12.sp, lineHeight = 20.sp, fontFamily = IRANSansX,
                                color = sk.textMuted,
                                maxLines = 2, softWrap = true
                            )
                        }
                        if (info.holidayNames.isEmpty() && info.note == null) {
                            Text("—", fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
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
    todayKey: String, isCurrentMonth: Boolean,
    onGoMonth: (Int) -> Unit, onGoToday: () -> Unit, onOpenDay: (String) -> Unit,
    onOpenPicker: () -> Unit, onClose: () -> Unit
) {
    val sk = LocalSk.current
    val all = filterGroup == GroupFilter.ALL

    Box(Modifier.fillMaxSize().background(sk.background)) {
        AuroraBackground(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glass(RoundedCornerShape(16.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SkIconButton(SkIcons.ChevronRight, "ماه قبل", iconSize = 18) { onGoMonth(-1) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TitlePill("${JALALI_MONTHS[viewMonth - 1]} ${toPersianDigits(viewYear)}") { onOpenPicker() }
                }
                SkIconButton(SkIcons.ChevronLeft, "ماه بعد", iconSize = 18) { onGoMonth(1) }
                SkIconButton(SkIcons.Collapse, "بازگشت به نمای تقویم", iconSize = 17, tint = sk.primary) { onClose() }
            }

            if (all) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(sk.day, "صبح‌کار")
                    Spacer(Modifier.width(16.dp))
                    LegendDot(sk.night, "شب‌کار")
                    Spacer(Modifier.width(16.dp))
                    LegendDot(sk.rest, "استراحت")
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .cardSoft(RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(sk.glassHi)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("روز", Modifier.width(64.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                    Text("روز هفته", Modifier.width(96.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                    Text(if (all) "شیفت گروه‌ها" else "وضعیت شیفت", Modifier.width(148.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                    Text("مناسبت‌ها و یادداشت روزانه", Modifier.weight(1f).padding(start = 16.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(dayInfo.size) { i ->
                        val info = dayInfo[i]
                        val holiday = info.holiday || info.isFriday
                        val effGroup = if (all) GroupFilter.A else filterGroup
                        val code = info.all.getValue(effGroup.toShiftGroup())
                        val kind = codeToKind(code)
                        val kc = kindColors(kind)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (holiday) Modifier.background(sk.holidaySoft.copy(alpha = 0.6f)) else Modifier)
                                .then(if (info.isToday) Modifier.background(sk.primarySoft) else Modifier)
                                .clickableNoRipple { onOpenDay(info.key) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                toPersianDigits(info.d.toString().padStart(2, '۰')),
                                Modifier.width(64.dp), textAlign = TextAlign.Center,
                                fontSize = 13.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                color = when {
                                    info.isToday -> sk.primary
                                    holiday -> sk.holiday
                                    else -> sk.text
                                }
                            )
                            Text(
                                WEEKDAYS[info.weekday],
                                Modifier.width(96.dp), textAlign = TextAlign.Center,
                                fontSize = 12.5.sp, fontFamily = IRANSansX,
                                color = if (holiday) sk.holiday else sk.textMuted
                            )
                            Box(Modifier.width(148.dp), contentAlignment = Alignment.Center) {
                                if (all) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        GROUPS.forEach { g -> MiniBadge(g, codeToKind(info.all.getValue(g))) }
                                    }
                                } else {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(kc.bg)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "${CODE_FA.getValue(code)} (${code.name})",
                                            fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = kc.ink
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
                                if (info.holidayNames.isNotEmpty()) {
                                    Text(
                                        info.holidayNames.joinToString("، "),
                                        fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.holiday,
                                        maxLines = 3, softWrap = true
                                    )
                                }
                                if (info.note != null) {
                                    Text(
                                        info.note,
                                        fontSize = 12.sp, lineHeight = 20.sp, fontFamily = IRANSansX,
                                        color = sk.textMuted,
                                        maxLines = 3, softWrap = true
                                    )
                                }
                                if (info.holidayNames.isEmpty() && info.note == null) {
                                    Text("—", fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
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

@Composable
private fun MiniBadge(group: ShiftGroup, kind: ShiftKind) {
    val kc = kindColors(kind)
    Box(
        Modifier
            .size(width = 24.dp, height = 22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(kc.bg),
        contentAlignment = Alignment.Center
    ) {
        Text(group.faLetter(), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = kc.ink)
    }
}
