package com.seyedhosseinmousavisaeedi.shiftkar.ui.manager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seyedhosseinmousavisaeedi.shiftkar.data.*
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.ImageExporter
import com.seyedhosseinmousavisaeedi.shiftkar.util.WorkReportLogEntry
import com.seyedhosseinmousavisaeedi.shiftkar.util.WorkReportSummaryData
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import java.util.Calendar

/**
 * ShiftManagerScreen (v6.5.1) — Dedicated 5th Tab («مدیریت شیفت»):
 *  - Supports Morning (صبح‌کار), Evening (عصرکار), Night (شب‌کار), and Rest (استراحت)
 *  - Revamped 11 industrial/3-shift/hospital/guard/satellite/admin presets + Custom Builder
 *  - Polished Employee Cards (no text/button overlap)
 *  - Dedicated Popup Modals for Adding/Editing an Employee and Confirming Deletion
 */

private enum class ManagerSubTab { PATTERNS, EMPLOYEES, REPORT }

private fun addDaysJalali(j: JalaliDate, delta: Int): JalaliDate {
    val g = toGregorian(j.jy, j.jm, j.jd)
    val cal = Calendar.getInstance().apply {
        set(g.gy, g.gm - 1, g.gd, 12, 0, 0)
        add(Calendar.DAY_OF_YEAR, delta)
    }
    return toJalaali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

@Composable
fun ShiftManagerScreen(vm: AppViewModel) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)

    var subTab by remember { mutableStateOf(ManagerSubTab.PATTERNS) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            "مدیریت شیفت و پرسنل",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = IRANSansX,
            color = sk.text
        )
        Text(
            "طراحی و انتخاب الگوی نوبت‌کاری و مدیریت برنامهٔ همکاران",
            Modifier.fillMaxWidth().padding(top = 3.dp),
            textAlign = TextAlign.Center,
            fontSize = 11.5.sp,
            fontFamily = IRANSansX,
            color = sk.textMuted
        )

        Spacer(Modifier.height(12.dp))

        SegmentedControl(
            options = listOf(
                "الگو و ساخت شیفت",
                "پرسنل (${toPersianDigits(s.employees.size)})",
                "گزارش کارکرد"
            ),
            selectedIndex = when (subTab) {
                ManagerSubTab.PATTERNS -> 0
                ManagerSubTab.EMPLOYEES -> 1
                ManagerSubTab.REPORT -> 2
            },
            modifier = Modifier.fillMaxWidth()
        ) { idx ->
            haptic.tick()
            subTab = when (idx) {
                0 -> ManagerSubTab.PATTERNS
                1 -> ManagerSubTab.EMPLOYEES
                else -> ManagerSubTab.REPORT
            }
        }

        Spacer(Modifier.height(12.dp))

        when (subTab) {
            ManagerSubTab.PATTERNS -> ShiftPatternsSection(vm, s)
            ManagerSubTab.EMPLOYEES -> EmployeesManagerSection(vm, s)
            ManagerSubTab.REPORT -> WorkReportSection(vm, s)
        }

        Spacer(Modifier.height(112.dp))
    }
}

/* ============================================================================
 * SUB-TAB 1: PRESET & CUSTOM SHIFT SYSTEM BUILDER (WITH «عصرکار»)
 * ============================================================================ */

@Composable
private fun ShiftPatternsSection(vm: AppViewModel, s: AppSettings) {
    val sk = LocalSk.current
    val haptic = rememberHaptics(s.hapticOn)
    val today = remember { todayJalaali() }
    val todayKey = remember(today) { makeDateKey(today.jy, today.jm, today.jd) }
    val myGroup = s.myGroup.toShiftGroup()

    val activePattern = remember(s.activePatternId, s.customPatterns) {
        ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
    }
    val todayCode = remember(todayKey, myGroup, activePattern) {
        ShiftEngine.getShiftCodeForPattern(todayKey, myGroup, activePattern)
    }
    val todayKind = codeToKind(todayCode)

    var patternPendingDelete by remember { mutableStateOf<ShiftPatternSpec?>(null) }
    var presetPendingRestore by remember { mutableStateOf<ShiftPatternSpec?>(null) }
    var restoreAllConfirmOpen by remember { mutableStateOf(false) }

    /* Builder & Preset-Editor states */
    var builderOpen by remember { mutableStateOf(false) }
    var editingPatternId by remember { mutableStateOf<String?>(null) }
    var customName by remember { mutableStateOf("شیفت سفارشی من") }
    var dayCount by remember { mutableIntStateOf(2) }
    var eveningCount by remember { mutableIntStateOf(2) }
    var nightCount by remember { mutableIntStateOf(2) }
    var restCount by remember { mutableIntStateOf(2) }
    var todayCycleStep by remember { mutableIntStateOf(0) }
    var noGroupMode by remember { mutableStateOf(false) }
    var groupCells by remember { mutableStateOf(listOf("A", "B", "C", "D")) }

    fun loadPatternIntoBuilder(pat: ShiftPatternSpec) {
        editingPatternId = pat.id
        customName = pat.name
        dayCount = pat.dayCount.coerceIn(0, 24)
        eveningCount = pat.eveningCount.coerceIn(0, 24)
        nightCount = pat.nightCount.coerceIn(0, 24)
        restCount = pat.restCount.coerceIn(1, 24)
        noGroupMode = !pat.hasGroups
        groupCells = if (!pat.hasGroups) {
            listOf("A", "B", "C", "D")
        } else {
            ALL_SHIFT_GROUPS.take(pat.groupCount.coerceIn(1, 8)).map { g ->
                pat.groupLabels[g]?.takeIf { it.isNotBlank() } ?: g.name
            }
        }
        val cyc = pat.buildCycleList()
        val curCode = ShiftEngine.getShiftCodeForPattern(todayKey, myGroup, pat)
        todayCycleStep = cyc.indexOf(curCode).takeIf { it >= 0 } ?: 0
        builderOpen = true
    }

    val isActivePresetModified = remember(activePattern.id, s.customPatterns) {
        ShiftEngine.isPresetModified(activePattern.id, s.customPatterns)
    }

    /* 1. Active Pattern Status Banner (polished layout + Edit Active Preset & Restore to Default) */
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), tint = sk.primarySoft)
            .border(1.5.dp, sk.primary, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(sk.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.Calendar, null, Modifier.size(18.dp), tint = sk.sheetSolid)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "سیستم نوبت‌کاری فعال در کل برنامه",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActivePresetModified) {
                        Text(
                            "ویرایش‌شده",
                            Modifier
                                .clip(RoundedCornerShape(100))
                                .background(sk.surfaceStrong)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                    }
                }
                Text(
                    activePattern.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(100))
                    .background(sk.surfaceStrong)
                    .border(1.dp, sk.border, RoundedCornerShape(100))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    GROUP_FA.getValue(myGroup),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }
        }
        Text(
            activePattern.subtitle,
            fontSize = 11.sp,
            fontFamily = IRANSansX,
            color = sk.textMuted
        )
        val (tBg, tInk) = when (todayKind) {
            ShiftKind.DAY -> sk.daySoft to sk.dayInk
            ShiftKind.EVENING -> sk.eveningSoft to sk.eveningInk
            ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
            ShiftKind.REST -> sk.restSoft to sk.restInk
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "وضعیت امروز شما با این الگو:",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.text
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(kindIcon(todayKind), null, Modifier.size(15.dp), tint = tInk)
                Text(
                    CODE_FA.getValue(todayCode),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = tInk
                )
            }
        }

        /* Quick Action Row: Edit Active Preset + Restore to Default (if modified) */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(sk.surfaceStrong)
                    .border(1.dp, sk.primary.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .clickableNoRipple {
                        haptic.tick()
                        loadPatternIntoBuilder(activePattern)
                    }
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(SkIcons.Edit, null, Modifier.size(14.dp), tint = sk.primary)
                Text(
                    "ویرایش الگوی فعال",
                    Modifier.padding(start = 6.dp),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }
            if (isActivePresetModified) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.holidaySoft)
                        .border(1.dp, sk.holiday.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            presetPendingRestore = activePattern
                        }
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(SkIcons.Refresh, null, Modifier.size(14.dp), tint = sk.holiday)
                    Text(
                        "بازگردانی به پیش‌فرض",
                        Modifier.padding(start = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IRANSansX,
                        color = sk.holiday
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    val previewCycle = remember(dayCount, eveningCount, nightCount, restCount) {
        val list = mutableListOf<ShiftCode>()
        for (i in 1..dayCount) list.add(ShiftCode.forKindAndOrdinal(ShiftKind.DAY, i))
        for (i in 1..eveningCount) list.add(ShiftCode.forKindAndOrdinal(ShiftKind.EVENING, i))
        for (i in 1..nightCount) list.add(ShiftCode.forKindAndOrdinal(ShiftKind.NIGHT, i))
        for (i in 1..restCount) list.add(ShiftCode.forKindAndOrdinal(ShiftKind.REST, i))
        if (list.isEmpty()) list.add(ShiftCode.R1)
        list
    }
    val safeStep = todayCycleStep.coerceIn(0, (previewCycle.size - 1).coerceAtLeast(0))

    /* Action Card to open Custom Shift Builder Popup Sheet */
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .clickableNoRipple {
                haptic.tick()
                editingPatternId = null
                customName = "شیفت سفارشی من"
                dayCount = 2
                eveningCount = 0
                nightCount = 2
                restCount = 4
                noGroupMode = false
                groupCells = listOf("A", "B", "C", "D")
                todayCycleStep = 0
                builderOpen = true
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(sk.primarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(SkIcons.Palette, null, Modifier.size(18.dp), tint = sk.primary)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                "+ ساخت سیستم شیفت سفارشی جدید",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IRANSansX,
                color = sk.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "طراحی چرخه با صبح‌کار، عصرکار، شب‌کار، استراحت و گروه‌های دلخواه",
                fontSize = 11.sp,
                fontFamily = IRANSansX,
                color = sk.textMuted
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(sk.primarySoft)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                "طراحی الگو",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = sk.primary
            )
        }
    }

    /* Popup Modal SkSheet for Editing / Creating a Shift Pattern */
    SkSheet(
        visible = builderOpen,
        onDismiss = { builderOpen = false },
        title = if (editingPatternId != null) "ویرایش الگوی «$customName»" else "ساخت سیستم شیفت سفارشی"
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

                if (editingPatternId != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(sk.primarySoft)
                            .border(1.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (ShiftEngine.isBuiltInPreset(editingPatternId!!)) "در حال ویرایش الگوی آماده"
                            else "در حال ویرایش الگوی سفارشی",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (ShiftEngine.isPresetModified(editingPatternId!!, s.customPatterns)) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(sk.holidaySoft)
                                        .clickableNoRipple {
                                            haptic.tick()
                                            val target = ShiftEngine.resolvePattern(editingPatternId!!, s.customPatterns)
                                            presetPendingRestore = target
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "بازگردانی پیش‌فرض",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = IRANSansX,
                                        color = sk.holiday
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(sk.surfaceStrong)
                                    .clickableNoRipple {
                                        haptic.tick()
                                        editingPatternId = null
                                        customName = "شیفت سفارشی من"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "ساخت الگوی جدید",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = sk.textMuted
                                )
                            }
                        }
                    }
                }

                Text(
                    if (editingPatternId != null) "نام الگو:" else "نام الگوی سفارشی:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = customName,
                        onValueChange = { if (it.length <= 40) customName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.text
                        ),
                        cursorBrush = SolidColor(sk.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                /* مدیریت پویا و تعاملی سلول‌های گروه شیفت (افزودن E/F/G یا حذف C/D یا فردی) */
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "گروه‌های شیفت (حذف، ویرایش یا افزودن):",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                    Text(
                        if (noGroupMode) "شیفت فردی (بدون گروه)" else "${toPersianDigits(groupCells.size)} گروه فعال",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.primary
                    )
                }

                /* ردیف پیش‌فرض‌های سریع */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickPresets: List<Triple<String, Boolean, List<String>>> = listOf(
                        Triple("چهار گروه (A..D)", false, listOf("A", "B", "C", "D")),
                        Triple("سه گروه (A..C)", false, listOf("A", "B", "C")),
                        Triple("دو گروه (A, B)", false, listOf("A", "B")),
                        Triple("گروه‌های E..H", false, listOf("E", "F", "G", "H")),
                        Triple("الف تا د", false, listOf("الف", "ب", "ج", "د")),
                        Triple("بدون گروه (فردی)", true, emptyList())
                    )
                    quickPresets.forEach { (lbl, isNoGrp, presetList) ->
                        val active = (noGroupMode && isNoGrp) || (!noGroupMode && !isNoGrp && groupCells == presetList)
                        Box(
                            Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) sk.primarySoft else sk.surface)
                                .border(
                                    width = if (active) 1.4.dp else 1.dp,
                                    color = if (active) sk.primary else sk.borderSoft,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickableNoRipple {
                                    haptic.tick()
                                    if (isNoGrp) {
                                        noGroupMode = true
                                    } else {
                                        noGroupMode = false
                                        groupCells = presetList
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lbl,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                color = if (active) sk.primary else sk.textMuted
                            )
                        }
                    }
                }

                /* سلول‌های گروه با قابلیت حذف هر سلول (×) و افزودن گروه جدید (+) */
                AnimatedVisibility(visible = !noGroupMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(sk.surface)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val allItemsCount = groupCells.size + (if (groupCells.size < 8) 1 else 0)
                        val rows = (0 until allItemsCount).chunked(4)
                        rows.forEach { rowIndices ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowIndices.forEach { cellIdx ->
                                    if (cellIdx < groupCells.size) {
                                        val curVal = groupCells[cellIdx]
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(sk.surfaceStrong)
                                                .border(1.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                                .padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "گروه ${toPersianDigits(cellIdx + 1)}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = IRANSansX,
                                                    color = sk.textMuted
                                                )
                                                Box(
                                                    Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(sk.holidaySoft)
                                                        .clickableNoRipple {
                                                            haptic.tick()
                                                            if (groupCells.size <= 1) {
                                                                noGroupMode = true
                                                            } else {
                                                                groupCells = groupCells.toMutableList().also { it.removeAt(cellIdx) }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(SkIcons.Close, "حذف گروه", Modifier.size(10.dp), tint = sk.holiday)
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            BasicTextField(
                                                value = curVal,
                                                onValueChange = { newTxt ->
                                                    if (newTxt.length <= 6) {
                                                        groupCells = groupCells.toMutableList().also { it[cellIdx] = newTxt }
                                                    }
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = IRANSansX,
                                                    color = sk.primary,
                                                    textAlign = TextAlign.Center
                                                ),
                                                cursorBrush = SolidColor(sk.primary),
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        /* دکمهٔ + افزودن گروه جدید (مثلاً E، F، G، H) */
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(sk.primarySoft)
                                                .border(1.2.dp, sk.primary, RoundedCornerShape(12.dp))
                                                .clickableNoRipple {
                                                    haptic.tick()
                                                    val candidatePool = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                                                    val nextLetter = candidatePool.firstOrNull { it !in groupCells }
                                                        ?: "${groupCells.size + 1}"
                                                    if (groupCells.size < 8) {
                                                        groupCells = groupCells + nextLetter
                                                    }
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                "+",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = IRANSansX,
                                                color = sk.primary
                                            )
                                            Text(
                                                "افزودن گروه",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = IRANSansX,
                                                color = sk.primary
                                            )
                                        }
                                    }
                                }
                                repeat(4 - rowIndices.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                /* Day / Evening / Night / Rest count steppers */
                CycleCountRow(
                    icon = SkIcons.Sun,
                    label = "تعداد روز صبح‌کار",
                    count = dayCount,
                    minVal = 0,
                    maxVal = 24,
                    tintBg = sk.daySoft,
                    tintInk = sk.dayInk,
                    onChange = { haptic.tick(); dayCount = it }
                )
                CycleCountRow(
                    icon = SkIcons.Sunset,
                    label = "تعداد روز عصرکار",
                    count = eveningCount,
                    minVal = 0,
                    maxVal = 24,
                    tintBg = sk.eveningSoft,
                    tintInk = sk.eveningInk,
                    onChange = { haptic.tick(); eveningCount = it }
                )
                CycleCountRow(
                    icon = SkIcons.Moon,
                    label = "تعداد روز شب‌کار",
                    count = nightCount,
                    minVal = 0,
                    maxVal = 24,
                    tintBg = sk.nightSoft,
                    tintInk = sk.nightInk,
                    onChange = { haptic.tick(); nightCount = it }
                )
                CycleCountRow(
                    icon = SkIcons.Rest,
                    label = "تعداد روز استراحت",
                    count = restCount,
                    minVal = 1,
                    maxVal = 24,
                    tintBg = sk.restSoft,
                    tintInk = sk.restInk,
                    onChange = { haptic.tick(); restCount = it }
                )

                Text(
                    "پیش‌نمایش چرخه (${toPersianDigits(previewCycle.size)} روزه — برای انتخاب روزِ امروز لمس کنید):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    previewCycle.forEachIndexed { idx, c ->
                        val k = codeToKind(c)
                        val (bg, ink) = when (k) {
                            ShiftKind.DAY -> sk.daySoft to sk.dayInk
                            ShiftKind.EVENING -> sk.eveningSoft to sk.eveningInk
                            ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
                            ShiftKind.REST -> sk.restSoft to sk.restInk
                        }
                        val isPickedToday = idx == safeStep
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(
                                    width = if (isPickedToday) 1.8.dp else 1.dp,
                                    color = if (isPickedToday) sk.primary else ink.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickableNoRipple {
                                    haptic.tick()
                                    todayCycleStep = idx
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                CODE_NUM_FA.getValue(c),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = ink
                            )
                        }
                    }
                }

                val stepCode = previewCycle[safeStep]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "تنظیم با تاریخ امروز (${formatJalali(today.jy, today.jm, today.jd)} — ${GROUP_FA.getValue(myGroup)}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(sk.surfaceStrong)
                                .clickableNoRipple {
                                    haptic.tick()
                                    todayCycleStep = (safeStep - 1 + previewCycle.size) % previewCycle.size
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SkIcons.ChevronRight, null, Modifier.size(15.dp), tint = sk.text)
                        }
                        Text(
                            "امروز: ${CODE_FA.getValue(stepCode)} (روز ${toPersianDigits(safeStep + 1)} از ${toPersianDigits(previewCycle.size)})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(sk.surfaceStrong)
                                .clickableNoRipple {
                                    haptic.tick()
                                    todayCycleStep = (safeStep + 1) % previewCycle.size
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SkIcons.ChevronLeft, null, Modifier.size(15.dp), tint = sk.text)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.primary)
                        .clickableNoRipple {
                            haptic.tick(18)
                            val total = previewCycle.size.coerceAtLeast(1)
                            val cleanedCells = groupCells.map { it.trim() }.filter { it.isNotEmpty() }
                            val hasGroupsFlag = !noGroupMode && cleanedCells.isNotEmpty()
                            val grpCount = if (!hasGroupsFlag) 1 else cleanedCells.size.coerceIn(1, ALL_SHIFT_GROUPS.size)
                            val stepGap = (total / grpCount).coerceAtLeast(1)
                            val offsets = ALL_SHIFT_GROUPS.mapIndexed { idx, g ->
                                g to (if (!hasGroupsFlag) 0 else (stepGap * idx) % total)
                            }.toMap()
                            val resolvedLabels = ALL_SHIFT_GROUPS.mapIndexed { idx, g ->
                                g to (cleanedCells.getOrNull(idx) ?: g.name)
                            }.toMap()
                            val myOff = offsets[myGroup] ?: 0
                            val todayJdn = jalaliToJdn(today.jy, today.jm, today.jd)
                            val anchorJdn = todayJdn + myOff - safeStep
                            val wasEditing = editingPatternId != null
                            val id = editingPatternId ?: "custom_${System.currentTimeMillis()}"
                            val title = customName.trim().ifEmpty { "شیفت سفارشی" }
                            val activeNames = cleanedCells.take(grpCount).joinToString("/")
                            val sub = buildString {
                                if (dayCount > 0) append("${toPersianDigits(dayCount)} روز صبح‌کار · ")
                                if (eveningCount > 0) append("${toPersianDigits(eveningCount)} روز عصرکار · ")
                                if (nightCount > 0) append("${toPersianDigits(nightCount)} روز شب‌کار · ")
                                append("${toPersianDigits(restCount)} روز استراحت")
                                if (!hasGroupsFlag) {
                                    append(" (بدون گروه / فردی)")
                                } else {
                                    append(" (${toPersianDigits(grpCount)} گروه: $activeNames)")
                                }
                            }
                            val spec = ShiftPatternSpec(
                                id = id,
                                name = title,
                                subtitle = sub,
                                dayCount = dayCount,
                                eveningCount = eveningCount,
                                nightCount = nightCount,
                                restCount = restCount,
                                anchorJdn = anchorJdn,
                                groupOffsets = offsets,
                                isWeeklyAdmin = false,
                                isCustom = true,
                                hasGroups = hasGroupsFlag,
                                groupCount = grpCount,
                                groupLabels = resolvedLabels
                            )
                            vm.saveCustomPattern(spec, activateImmediately = true)
                            editingPatternId = null
                            builderOpen = false
                            ToastCenter.show(
                                if (wasEditing) "تغییرات الگوی «$title» ذخیره و در برنامه اعمال شد"
                                else "الگوی «$title» ساخته و در کل برنامه فعال شد"
                            )
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(SkIcons.Check, null, Modifier.size(16.dp), tint = sk.sheetSolid)
                    Text(
                        if (editingPatternId != null) "ذخیرهٔ تغییرات الگو و اعمال در برنامه" else "ذخیره و اعمال در کل برنامه",
                        Modifier.padding(start = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.sheetSolid
                    )
                }
        }
    }

    /* 3. User's Saved Custom Patterns (excluding modified built-in presets) */
    val pureCustomPatterns = remember(s.customPatterns) {
        s.customPatterns.filterNot { ShiftEngine.isBuiltInPreset(it.id) }
    }
    val anyBuiltInModified = remember(s.customPatterns) {
        s.customPatterns.any { ShiftEngine.isBuiltInPreset(it.id) }
    }

    if (pureCustomPatterns.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(
            "الگوهای سفارشی ساخته‌شده توسط شما",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = IRANSansX,
            color = sk.primary
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pureCustomPatterns.forEach { pat ->
                PatternCard(
                    pat = pat,
                    isActive = s.activePatternId == pat.id,
                    isModifiedBuiltIn = false,
                    onActivate = {
                        haptic.tick()
                        vm.setActivePattern(pat.id)
                        ToastCenter.show("الگوی «${pat.name}» فعال شد")
                    },
                    onEdit = {
                        haptic.tick()
                        loadPatternIntoBuilder(pat)
                    },
                    onRestoreDefault = null,
                    onDelete = {
                        haptic.tick()
                        patternPendingDelete = pat
                    }
                )
            }
        }
    }

    /* 4. Revamped Built-in Presets (with Edit & Restore-to-Default support) */
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "فهرست کامل الگوهای آماده (${toPersianDigits(ShiftEngine.PRESET_PATTERNS.size)} الگوی استاندارد)",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = IRANSansX,
            color = sk.primary
        )
        if (anyBuiltInModified) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(100))
                    .background(sk.holidaySoft)
                    .border(1.dp, sk.holiday.copy(alpha = 0.45f), RoundedCornerShape(100))
                    .clickableNoRipple {
                        haptic.tick()
                        restoreAllConfirmOpen = true
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(SkIcons.Refresh, null, Modifier.size(12.dp), tint = sk.holiday)
                Text(
                    "بازگردانی همه به پیش‌فرض",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.holiday
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShiftEngine.PRESET_PATTERNS.forEach { defaultPat ->
            val resolvedPat = ShiftEngine.resolvePattern(defaultPat.id, s.customPatterns)
            val isMod = ShiftEngine.isPresetModified(defaultPat.id, s.customPatterns)
            PatternCard(
                pat = resolvedPat,
                isActive = s.activePatternId == defaultPat.id,
                isModifiedBuiltIn = isMod,
                onActivate = {
                    haptic.tick()
                    vm.setActivePattern(defaultPat.id)
                    ToastCenter.show("الگوی «${resolvedPat.name}» در برنامه فعال شد")
                },
                onEdit = {
                    haptic.tick()
                    loadPatternIntoBuilder(resolvedPat)
                },
                onRestoreDefault = if (isMod) {
                    {
                        haptic.tick()
                        presetPendingRestore = resolvedPat
                    }
                } else null,
                onDelete = null
            )
        }
    }

    /* Confirmation Popup for Deleting a Custom Pattern */
    patternPendingDelete?.let { targetPat ->
        ConfirmDeleteDialog(
            title = "حذف الگوی سفارشی",
            message = "آیا از حذف الگوی «${targetPat.name}» اطمینان دارید؟",
            confirmLabel = "حذف الگو",
            onConfirm = {
                haptic.tick(16)
                if (editingPatternId == targetPat.id) editingPatternId = null
                vm.deleteCustomPattern(targetPat.id)
                patternPendingDelete = null
                ToastCenter.show("الگوی «${targetPat.name}» حذف شد")
            },
            onDismiss = { patternPendingDelete = null }
        )
    }

    /* Confirmation Popup for Restoring a Modified Built-in Preset to Default */
    presetPendingRestore?.let { targetPat ->
        val defPat = ShiftEngine.getDefaultPreset(targetPat.id)
        ConfirmDeleteDialog(
            title = "بازگردانی به حالت پیش‌فرض",
            message = "آیا می‌خواهید الگوی «${defPat?.name ?: targetPat.name}» به تنظیمات اولیه و پیش‌فرض کارخانه برگردد؟",
            confirmLabel = "بازگردانی پیش‌فرض",
            onConfirm = {
                haptic.tick(16)
                if (editingPatternId == targetPat.id) {
                    editingPatternId = null
                    builderOpen = false
                }
                vm.restorePresetToDefault(targetPat.id)
                presetPendingRestore = null
                ToastCenter.show("الگوی «${defPat?.name ?: targetPat.name}» به حالت پیش‌فرض بازگردانی شد")
            },
            onDismiss = { presetPendingRestore = null }
        )
    }

    /* Confirmation Popup for Restoring ALL Modified Built-in Presets */
    if (restoreAllConfirmOpen) {
        ConfirmDeleteDialog(
            title = "بازگردانی همهٔ الگوهای آماده",
            message = "آیا می‌خواهید تمام تغییرات اعمال‌شده روی الگوهای آماده به حالت پیش‌فرض اولیه برگردد؟ (الگوهای سفارشیِ جدید شما حذف نمی‌شوند)",
            confirmLabel = "بازگردانی همه",
            onConfirm = {
                haptic.tick(16)
                if (editingPatternId != null && ShiftEngine.isBuiltInPreset(editingPatternId!!)) {
                    editingPatternId = null
                    builderOpen = false
                }
                vm.restoreAllPresetsToDefault()
                restoreAllConfirmOpen = false
                ToastCenter.show("همهٔ الگوهای آماده به حالت پیش‌فرض بازگردانی شدند")
            },
            onDismiss = { restoreAllConfirmOpen = false }
        )
    }
}

@Composable
private fun CycleCountRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    minVal: Int,
    maxVal: Int,
    tintBg: Color,
    tintInk: Color,
    onChange: (Int) -> Unit
) {
    val sk = LocalSk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(sk.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tintBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(15.dp), tint = tintInk)
            }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(sk.surfaceStrong)
                    .clickableNoRipple(enabled = count > minVal) { onChange((count - 1).coerceAtLeast(minVal)) },
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
            }
            Text(
                "${toPersianDigits(count)} روز",
                Modifier.widthIn(min = 48.dp),
                textAlign = TextAlign.Center,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = tintInk
            )
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(sk.surfaceStrong)
                    .clickableNoRipple(enabled = count < maxVal) { onChange((count + 1).coerceAtMost(maxVal)) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary)
            }
        }
    }
}

@Composable
private fun PatternCard(
    pat: ShiftPatternSpec,
    isActive: Boolean,
    isModifiedBuiltIn: Boolean = false,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onRestoreDefault: (() -> Unit)? = null,
    onDelete: (() -> Unit)?
) {
    val sk = LocalSk.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), tint = if (isActive) sk.primary.copy(alpha = 0.12f) else null)
            .border(
                width = if (isActive) 1.8.dp else 1.dp,
                color = if (isActive) sk.primary else sk.borderSoft,
                shape = RoundedCornerShape(18.dp)
            )
            .clickableNoRipple { onActivate() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        pat.name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IRANSansX,
                        color = if (isActive) sk.primary else sk.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isModifiedBuiltIn) {
                        Text(
                            "ویرایش‌شده",
                            Modifier
                                .clip(RoundedCornerShape(100))
                                .background(sk.primarySoft)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                    }
                }
                Text(
                    pat.subtitle,
                    fontSize = 11.sp,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sk.primarySoft)
                        .border(1.dp, sk.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickableNoRipple { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Edit, "ویرایش الگو", Modifier.size(14.dp), tint = sk.primary)
                }
                if (onRestoreDefault != null) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(sk.holidaySoft)
                            .border(1.dp, sk.holiday.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickableNoRipple { onRestoreDefault() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Refresh, "بازگردانی به پیش‌فرض", Modifier.size(14.dp), tint = sk.holiday)
                    }
                }
                if (onDelete != null) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(sk.holidaySoft)
                            .clickableNoRipple { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Trash, "حذف الگو", Modifier.size(15.dp), tint = sk.holiday)
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .background(if (isActive) sk.primary else sk.surfaceStrong)
                        .border(1.dp, if (isActive) sk.primary else sk.borderSoft, RoundedCornerShape(100))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (isActive) "فعال" else "اعمال الگو",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = if (isActive) sk.sheetSolid else sk.primary
                    )
                }
            }
        }

        /* Mini visual color bar of the cycle phases (Morning / Evening / Night / Rest) */
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(100)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            pat.effectivePhases.forEach { ph ->
                val phColor = when (ph.kind) {
                    ShiftKind.DAY -> sk.day
                    ShiftKind.EVENING -> sk.evening
                    ShiftKind.NIGHT -> sk.night
                    ShiftKind.REST -> sk.rest
                }
                Box(Modifier.weight(ph.count.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(phColor))
            }
        }
    }
}

/* ============================================================================
 * SUB-TAB 2: EMPLOYEE PROFILES & SCHEDULE COMPARISON (WITH POPUP DIALOGS)
 * ============================================================================ */

@Composable
private fun EmployeesManagerSection(vm: AppViewModel, s: AppSettings) {
    val sk = LocalSk.current
    val haptic = rememberHaptics(s.hapticOn)
    val today = remember { todayJalaali() }

    var dayOffset by remember { mutableIntStateOf(0) }
    val targetDate = remember(today, dayOffset) { addDaysJalali(today, dayOffset) }
    val targetKey = remember(targetDate) { makeDateKey(targetDate.jy, targetDate.jm, targetDate.jd) }
    val targetWeekday = remember(targetDate) { formatWeekday(targetDate.jy, targetDate.jm, targetDate.jd) }

    val myGroup = s.myGroup.toShiftGroup()
    val myScheduledCode = ShiftEngine.getShiftCode(targetKey, myGroup)
    val myEffectiveKind = s.dayRecords[targetKey]?.swapKind ?: codeToKind(myScheduledCode)

    /* Popup modal states for Add/Edit Employee and Delete Confirmation */
    var employeeModalOpen by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeProfile?>(null) }
    var employeePendingDelete by remember { mutableStateOf<EmployeeProfile?>(null) }

    /* 1. Date Inspector Stepper (< پنج‌شنبه ۱۴ آبان ۱۴۰۵ >) */
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SkIconButton(SkIcons.ChevronRight, "روز قبل", iconSize = 16) {
            haptic.tick()
            dayOffset -= 1
        }
        Column(
            Modifier
                .weight(1f)
                .clickableNoRipple {
                    if (dayOffset != 0) {
                        haptic.tick()
                        dayOffset = 0
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$targetWeekday ${formatJalali(targetDate.jy, targetDate.jm, targetDate.jd)}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.text
                )
                if (dayOffset == 0) {
                    Text(
                        "امروز",
                        Modifier
                            .clip(RoundedCornerShape(100))
                            .background(sk.primarySoft)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.primary
                    )
                }
            }
            Text(
                "شیفت شما (${GROUP_FA.getValue(myGroup)}): ${SHIFT_KIND_FA.getValue(myEffectiveKind)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.primary
            )
        }
        SkIconButton(SkIcons.ChevronLeft, "روز بعد", iconSize = 16) {
            haptic.tick()
            dayOffset += 1
        }
    }

    Spacer(Modifier.height(10.dp))

    /* 2. Unified Team Overview & Conflict/Coverage Analyzer */
    if (s.employees.isNotEmpty()) {
        data class EvaluatedEmployee(
            val emp: EmployeeProfile,
            val scheduledCode: ShiftCode,
            val scheduledKind: ShiftKind,
            val effectiveKind: ShiftKind?,
            val log: EmployeeDayLog
        )

        val evaluated = remember(s.employees, targetKey, s.activePatternId, s.customPatterns) {
            s.employees.map { emp ->
                val pat = if (emp.patternId == "ACTIVE") {
                    ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
                } else {
                    ShiftEngine.resolvePattern(emp.patternId, s.customPatterns)
                }
                val code = ShiftEngine.getShiftCodeForPattern(targetKey, emp.group, pat)
                val schedKind = codeToKind(code)
                val log = emp.dayLogs[targetKey] ?: EmployeeDayLog()
                val effKind: ShiftKind? = when (log.status) {
                    EmployeeDayStatus.SCHEDULED -> schedKind
                    EmployeeDayStatus.LEAVE -> null
                    EmployeeDayStatus.SWAP_DAY -> ShiftKind.DAY
                    EmployeeDayStatus.SWAP_EVENING -> ShiftKind.EVENING
                    EmployeeDayStatus.SWAP_NIGHT -> ShiftKind.NIGHT
                    EmployeeDayStatus.SWAP_REST -> ShiftKind.REST
                }
                EvaluatedEmployee(emp, code, schedKind, effKind, log)
            }
        }

        val onDayShift = evaluated.filter { it.effectiveKind == ShiftKind.DAY }
        val onEveningShift = evaluated.filter { it.effectiveKind == ShiftKind.EVENING }
        val onNightShift = evaluated.filter { it.effectiveKind == ShiftKind.NIGHT }
        val onRestAvailable = evaluated.filter { it.effectiveKind == ShiftKind.REST }
        val onLeave = evaluated.filter { it.log.status == EmployeeDayStatus.LEAVE }
        val uncoveredLeaves = onLeave.filter { it.scheduledKind != ShiftKind.REST }

        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "خلاصه وضعیت پرسنل در این روز",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IRANSansX,
                color = sk.primary
            )

            TeamBucketRow(
                icon = SkIcons.Sun,
                title = "صبح‌کار (${toPersianDigits(onDayShift.size)} نفر)",
                names = onDayShift.joinToString("، ") { "${it.emp.name} (${it.emp.group.faLetter()})" }.ifEmpty { "—" },
                bg = sk.daySoft,
                ink = sk.dayInk
            )
            if (onEveningShift.isNotEmpty() || evaluated.any { it.scheduledKind == ShiftKind.EVENING }) {
                TeamBucketRow(
                    icon = SkIcons.Sunset,
                    title = "عصرکار (${toPersianDigits(onEveningShift.size)} نفر)",
                    names = onEveningShift.joinToString("، ") { "${it.emp.name} (${it.emp.group.faLetter()})" }.ifEmpty { "—" },
                    bg = sk.eveningSoft,
                    ink = sk.eveningInk
                )
            }
            TeamBucketRow(
                icon = SkIcons.Moon,
                title = "شب‌کار (${toPersianDigits(onNightShift.size)} نفر)",
                names = onNightShift.joinToString("، ") { "${it.emp.name} (${it.emp.group.faLetter()})" }.ifEmpty { "—" },
                bg = sk.nightSoft,
                ink = sk.nightInk
            )
            TeamBucketRow(
                icon = SkIcons.Rest,
                title = "استراحت / آماده جایگزینی (${toPersianDigits(onRestAvailable.size)} نفر)",
                names = onRestAvailable.joinToString("، ") { "${it.emp.name} (${it.emp.group.faLetter()})" }.ifEmpty { "—" },
                bg = sk.restSoft,
                ink = sk.restInk
            )
            if (onLeave.isNotEmpty()) {
                TeamBucketRow(
                    icon = SkIcons.Holiday,
                    title = "مرخصی / غایب (${toPersianDigits(onLeave.size)} نفر)",
                    names = onLeave.joinToString("، ") { "${it.emp.name} (${SHIFT_KIND_FA.getValue(it.scheduledKind)})" },
                    bg = sk.holidaySoft,
                    ink = sk.holiday
                )
            }

            if (uncoveredLeaves.isNotEmpty()) {
                val replacements = onRestAvailable.joinToString("، ") { "${it.emp.name} (شیفت ${it.emp.group.faLetter()})" }
                    .ifEmpty { "هیچ‌کدام از پرسنل ثبت‌شده در استراحت نیستند" }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.holidaySoft)
                        .border(1.dp, sk.holiday.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "هشدار تداخل / نیاز به جایگزین:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.holiday
                    )
                    Text(
                        "${uncoveredLeaves.joinToString(" و ") { "${it.emp.name} (نوبت ${SHIFT_KIND_FA.getValue(it.scheduledKind)})" }} در مرخصی است. نفرات در استراحت جهت جایگزینی: $replacements",
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }

    /* 3. Button to Open Add Employee Popup Modal */
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), tint = sk.primary.copy(alpha = 0.14f))
            .border(1.2.dp, sk.primary, RoundedCornerShape(18.dp))
            .clickableNoRipple {
                haptic.tick()
                editingEmployee = null
                employeeModalOpen = true
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(SkIcons.Group, null, Modifier.size(17.dp), tint = sk.primary)
        Text(
            "+ افزودن همکار / پرسنل جدید",
            Modifier.padding(start = 8.dp),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = IRANSansX,
            color = sk.primary
        )
    }

    Spacer(Modifier.height(12.dp))

    /* 4. Individual Employee Cards */
    if (s.employees.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(SkIcons.Group, null, Modifier.size(28.dp), tint = sk.primary)
            Text(
                "هنوز همکاری اضافه نشده است",
                Modifier.padding(top = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX,
                color = sk.text
            )
            Text(
                "روی دکمهٔ «+ افزودن همکار / پرسنل جدید» بزنید تا همکاران خود را اضافه کنید و شیفت، مرخصی و اضافه‌کاری آن‌ها را با شیفت خودتان مقایسه نمایید.",
                Modifier.padding(top = 4.dp),
                fontSize = 11.5.sp,
                lineHeight = 20.sp,
                fontFamily = IRANSansX,
                color = sk.textMuted,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.employees.forEach { emp ->
                EmployeeCard(
                    emp = emp,
                    targetDate = targetDate,
                    targetKey = targetKey,
                    myGroup = myGroup,
                    myEffectiveKind = myEffectiveKind,
                    s = s,
                    onEdit = {
                        haptic.tick()
                        editingEmployee = emp
                        employeeModalOpen = true
                    },
                    onDelete = {
                        haptic.tick()
                        employeePendingDelete = emp
                    },
                    onUpdateLog = { st, ot ->
                        haptic.tick()
                        vm.saveEmployeeDayLog(emp.id, targetKey, st, ot)
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        /* 5. Toggle: «نمایش شیفت افراد روی تقویم» (active on default) + Shared Rest Days in Month */
        val sharedRestDaysInMonth = remember(
            targetDate.jy, targetDate.jm, myGroup, s.employees,
            s.dayRecords, s.activePatternId, s.customPatterns
        ) {
            val mLen = jalaaliMonthLength(targetDate.jy, targetDate.jm)
            var count = 0
            for (d in 1..mLen) {
                val dKey = makeDateKey(targetDate.jy, targetDate.jm, d)
                val myK = s.dayRecords[dKey]?.swapKind ?: codeToKind(ShiftEngine.getShiftCode(dKey, myGroup))
                if (myK == ShiftKind.REST) {
                    val anyColleagueRest = s.employees.any { emp ->
                        val pat = if (emp.patternId == "ACTIVE") ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
                        else ShiftEngine.resolvePattern(emp.patternId, s.customPatterns)
                        val eSched = codeToKind(ShiftEngine.getShiftCodeForPattern(dKey, emp.group, pat))
                        val eLog = emp.dayLogs[dKey]
                        val eEff: ShiftKind? = when (eLog?.status ?: EmployeeDayStatus.SCHEDULED) {
                            EmployeeDayStatus.SCHEDULED -> eSched
                            EmployeeDayStatus.LEAVE -> null
                            EmployeeDayStatus.SWAP_DAY -> ShiftKind.DAY
                            EmployeeDayStatus.SWAP_EVENING -> ShiftKind.EVENING
                            EmployeeDayStatus.SWAP_NIGHT -> ShiftKind.NIGHT
                            EmployeeDayStatus.SWAP_REST -> ShiftKind.REST
                        }
                        eEff == ShiftKind.REST
                    }
                    if (anyColleagueRest) count++
                }
            }
            count
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp))
                    .border(
                        1.dp,
                        if (s.showEmployeesOnCalendar) sk.primary.copy(alpha = 0.5f) else sk.borderSoft,
                        RoundedCornerShape(18.dp)
                    )
                    .clickableNoRipple {
                        haptic.tick()
                        val next = !s.showEmployeesOnCalendar
                        vm.setShowEmployeesOnCalendar(next)
                        ToastCenter.show(
                            if (next) "نمایش شیفت افراد روی تقویم فعال شد"
                            else "نمایش شیفت افراد روی تقویم غیرفعال شد"
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "نمایش شیفت افراد روی تقویم",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = IRANSansX,
                    color = sk.text
                )
                Box(
                    Modifier
                        .width(48.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(100))
                        .background(if (s.showEmployeesOnCalendar) sk.restInk else sk.surfaceStrong)
                        .border(1.dp, if (s.showEmployeesOnCalendar) sk.restInk else sk.borderSoft, RoundedCornerShape(100))
                        .padding(3.dp),
                    contentAlignment = if (s.showEmployeesOnCalendar) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(sk.restSoft)
                    .border(1.2.dp, sk.restInk.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(SkIcons.Rest, null, Modifier.size(16.dp), tint = sk.restInk)
                Text(
                    "${toPersianDigits(sharedRestDaysInMonth)} روز آزاد مشترک در ${JALALI_MONTHS[targetDate.jm - 1]}",
                    Modifier.padding(start = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.restInk
                )
            }
        }
    }

    /* Popup Modal for Add / Edit Employee */
    if (employeeModalOpen) {
        EmployeeEditDialog(
            initial = editingEmployee,
            s = s,
            onSave = { profile ->
                haptic.tick(16)
                vm.saveEmployee(profile)
                employeeModalOpen = false
                editingEmployee = null
                ToastCenter.show("مشخصات «${profile.name}» ذخیره شد")
            },
            onDismiss = {
                employeeModalOpen = false
                editingEmployee = null
            }
        )
    }

    /* Popup Modal for Confirming Employee Deletion */
    employeePendingDelete?.let { targetEmp ->
        ConfirmDeleteDialog(
            title = "حذف همکار از فهرست پرسنل",
            message = "آیا از حذف «${targetEmp.name}» و سوابق مرخصی/اضافه‌کاری او اطمینان دارید؟",
            confirmLabel = "حذف همکار",
            onConfirm = {
                haptic.tick(16)
                vm.deleteEmployee(targetEmp.id)
                employeePendingDelete = null
                ToastCenter.show("«${targetEmp.name}» از فهرست حذف شد")
            },
            onDismiss = { employeePendingDelete = null }
        )
    }
}

@Composable
private fun EmployeeEditDialog(
    initial: EmployeeProfile?,
    s: AppSettings,
    onSave: (EmployeeProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val sk = LocalSk.current
    val haptic = rememberHaptics(s.hapticOn)

    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var role by remember(initial) { mutableStateOf(initial?.role ?: "") }
    var group by remember(initial) { mutableStateOf(initial?.group ?: ShiftGroup.B) }
    var patternId by remember(initial) { mutableStateOf(initial?.patternId ?: "ACTIVE") }

    val allPatterns = remember(s.customPatterns) {
        ShiftEngine.PRESET_PATTERNS + s.customPatterns
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(sk.sheetSolid)
                .border(1.5.dp, sk.border, RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(sk.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (initial != null) SkIcons.Edit else SkIcons.Group, null, Modifier.size(17.dp), tint = sk.primary)
                    }
                    Text(
                        if (initial != null) "ویرایش مشخصات همکار" else "افزودن همکار جدید",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(sk.surfaceStrong)
                        .clickableNoRipple { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Close, "بستن", Modifier.size(15.dp), tint = sk.textMuted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("نام و نام خانوادگی:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= 35) name = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text),
                        cursorBrush = SolidColor(sk.primary),
                        decorationBox = { inner ->
                            if (name.isEmpty()) Text("مثلاً علی رضایی", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("سمت یا واحد (اختیاری):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = role,
                        onValueChange = { if (it.length <= 25) role = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = IRANSansX, color = sk.text),
                        cursorBrush = SolidColor(sk.primary),
                        decorationBox = { inner ->
                            if (role.isEmpty()) Text("مثلاً اپراتور برد", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textFaint)
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("گروه شیفت همکار:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
                SegmentedControl(
                    options = GROUPS.map { GROUP_FA.getValue(it) },
                    selectedIndex = GROUPS.indexOf(group)
                ) { idx ->
                    haptic.tick()
                    group = GROUPS[idx]
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("الگوی نوبت‌کاری همکار:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val patChoices = listOf("ACTIVE" to "هماهنگ با الگوی برنامه") + allPatterns.map { it.id to it.name }
                    patChoices.forEach { (pid, pLabel) ->
                        val active = patternId == pid
                        Box(
                            Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) sk.primarySoft else sk.surface)
                                .border(1.dp, if (active) sk.primary else sk.borderSoft, RoundedCornerShape(10.dp))
                                .clickableNoRipple {
                                    haptic.tick()
                                    patternId = pid
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                pLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                color = if (active) sk.primary else sk.textMuted
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                        .clickableNoRipple { onDismiss() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("انصراف", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textMuted)
                }
                Box(
                    Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.primary)
                        .clickableNoRipple {
                            val cleanName = name.trim()
                            if (cleanName.isEmpty()) {
                                ToastCenter.show("لطفاً نام همکار را وارد کنید")
                                return@clickableNoRipple
                            }
                            onSave(
                                EmployeeProfile(
                                    id = initial?.id ?: "emp_${System.currentTimeMillis()}",
                                    name = cleanName,
                                    role = role.trim(),
                                    group = group,
                                    patternId = patternId,
                                    dayLogs = initial?.dayLogs ?: emptyMap()
                                )
                            )
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (initial != null) "ذخیرهٔ تغییرات" else "افزودن همکار",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.sheetSolid
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sk = LocalSk.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(sk.sheetSolid)
                .border(1.5.dp, sk.holiday.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(sk.holidaySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Trash, null, Modifier.size(18.dp), tint = sk.holiday)
                }
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.text
                )
            }
            Text(
                message,
                fontSize = 12.5.sp,
                lineHeight = 22.sp,
                fontFamily = IRANSansX,
                color = sk.textMuted
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                        .clickableNoRipple { onDismiss() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("انصراف", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.holiday)
                        .clickableNoRipple { onConfirm() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(confirmLabel, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TeamBucketRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    names: String,
    bg: Color,
    ink: Color
) {
    val sk = LocalSk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = ink)
        Text(
            "$title:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = IRANSansX,
            color = ink
        )
        Text(
            names,
            Modifier.weight(1f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IRANSansX,
            color = sk.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmployeeCard(
    emp: EmployeeProfile,
    targetDate: JalaliDate,
    targetKey: String,
    myGroup: ShiftGroup,
    myEffectiveKind: ShiftKind,
    s: AppSettings,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateLog: (EmployeeDayStatus, Float) -> Unit
) {
    val sk = LocalSk.current
    val pat = remember(emp.patternId, s.activePatternId, s.customPatterns) {
        if (emp.patternId == "ACTIVE") ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
        else ShiftEngine.resolvePattern(emp.patternId, s.customPatterns)
    }
    val schedCode = ShiftEngine.getShiftCodeForPattern(targetKey, emp.group, pat)
    val schedKind = codeToKind(schedCode)
    val log = emp.dayLogs[targetKey] ?: EmployeeDayLog()

    val effectiveKind: ShiftKind? = when (log.status) {
        EmployeeDayStatus.SCHEDULED -> schedKind
        EmployeeDayStatus.LEAVE -> null
        EmployeeDayStatus.SWAP_DAY -> ShiftKind.DAY
        EmployeeDayStatus.SWAP_EVENING -> ShiftKind.EVENING
        EmployeeDayStatus.SWAP_NIGHT -> ShiftKind.NIGHT
        EmployeeDayStatus.SWAP_REST -> ShiftKind.REST
    }

    val (badgeBg, badgeInk, statusLabel) = when (effectiveKind) {
        ShiftKind.DAY -> Triple(sk.daySoft, sk.dayInk, if (log.status == EmployeeDayStatus.SCHEDULED) CODE_FA.getValue(schedCode) else "جابه‌جایی: صبح‌کار")
        ShiftKind.EVENING -> Triple(sk.eveningSoft, sk.eveningInk, if (log.status == EmployeeDayStatus.SCHEDULED) CODE_FA.getValue(schedCode) else "جابه‌جایی: عصرکار")
        ShiftKind.NIGHT -> Triple(sk.nightSoft, sk.nightInk, if (log.status == EmployeeDayStatus.SCHEDULED) CODE_FA.getValue(schedCode) else "جابه‌جایی: شب‌کار")
        ShiftKind.REST -> Triple(sk.restSoft, sk.restInk, if (log.status == EmployeeDayStatus.SCHEDULED) CODE_FA.getValue(schedCode) else "جابه‌جایی: استراحت")
        null -> Triple(sk.holidaySoft, sk.holiday, "مرخصی (برنامه: ${SHIFT_KIND_FA.getValue(schedKind)})")
    }

    val relationText = when {
        effectiveKind == null -> "در مرخصی"
        effectiveKind == myEffectiveKind && effectiveKind == ShiftKind.REST -> "استراحت مشترک با شما"
        effectiveKind == myEffectiveKind -> "هم‌شیفت با شما (${SHIFT_KIND_FA.getValue(myEffectiveKind)})"
        else -> "شیفت شما: ${SHIFT_KIND_FA.getValue(myEffectiveKind)}"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        /* Polished Header: weighted right section so Edit/Delete buttons NEVER clip or overlap */
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(sk.primarySoft)
                    .border(1.5.dp, sk.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    emp.group.faLetter(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        emp.name,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (emp.role.isNotEmpty()) {
                        Text(
                            "· ${emp.role}",
                            fontSize = 11.5.sp,
                            fontFamily = IRANSansX,
                            color = sk.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "${GROUP_FA.getValue(emp.group)} · $relationText",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = if (effectiveKind == myEffectiveKind) sk.primary else sk.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sk.primarySoft)
                        .border(1.dp, sk.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickableNoRipple { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Edit, "ویرایش همکار", Modifier.size(15.dp), tint = sk.primary)
                }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(sk.holidaySoft)
                        .border(1.dp, sk.holiday.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickableNoRipple { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Trash, "حذف همکار", Modifier.size(15.dp), tint = sk.holiday)
                }
            }
        }

        /* Selected Date Status Pill + Overtime Stepper */
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    when (effectiveKind) {
                        ShiftKind.DAY -> SkIcons.Sun
                        ShiftKind.EVENING -> SkIcons.Sunset
                        ShiftKind.NIGHT -> SkIcons.Moon
                        ShiftKind.REST -> SkIcons.Rest
                        null -> SkIcons.Holiday
                    },
                    null,
                    Modifier.size(14.dp),
                    tint = badgeInk
                )
                Text(
                    statusLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = badgeInk
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("اضافه‌کاری:", fontSize = 10.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(sk.surfaceStrong)
                        .clickableNoRipple(enabled = log.overtimeHours > 0f) {
                            onUpdateLog(log.status, (log.overtimeHours - 1f).coerceAtLeast(0f))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
                }
                Text(
                    "${toPersianDigits(log.overtimeHours.toInt())}س",
                    Modifier.widthIn(min = 28.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = if (log.overtimeHours > 0f) sk.primary else sk.textMuted
                )
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(sk.surfaceStrong)
                        .clickableNoRipple(enabled = log.overtimeHours < 24f) {
                            onUpdateLog(log.status, (log.overtimeHours + 1f).coerceAtMost(24f))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary)
                }
            }
        }

        /* Quick Daily Exception Chips (طبق برنامه | مرخصی | صبح | عصر | شب | استراحت) */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val statuses = listOf(
                EmployeeDayStatus.SCHEDULED to "برنامه",
                EmployeeDayStatus.LEAVE to "مرخصی",
                EmployeeDayStatus.SWAP_DAY to "صبح‌کار",
                EmployeeDayStatus.SWAP_EVENING to "عصرکار",
                EmployeeDayStatus.SWAP_NIGHT to "شب‌کار",
                EmployeeDayStatus.SWAP_REST to "استراحت"
            )
            statuses.forEach { (st, lbl) ->
                val active = log.status == st
                Box(
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) sk.primarySoft else sk.surface)
                        .border(1.dp, if (active) sk.primary else sk.borderSoft, RoundedCornerShape(8.dp))
                        .clickableNoRipple { onUpdateLog(st, log.overtimeHours) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        lbl,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = if (active) sk.primary else sk.textMuted,
                        maxLines = 1
                    )
                }
            }
        }

        /* 7-Day Comparison Strip (Selected Date .. +6 Days) */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until 7) {
                val dJ = addDaysJalali(targetDate, i)
                val dK = makeDateKey(dJ.jy, dJ.jm, dJ.jd)
                val dLog = emp.dayLogs[dK]
                val dSched = ShiftEngine.getShiftCodeForPattern(dK, emp.group, pat)
                val dKind: ShiftKind? = when (dLog?.status ?: EmployeeDayStatus.SCHEDULED) {
                    EmployeeDayStatus.SCHEDULED -> codeToKind(dSched)
                    EmployeeDayStatus.LEAVE -> null
                    EmployeeDayStatus.SWAP_DAY -> ShiftKind.DAY
                    EmployeeDayStatus.SWAP_EVENING -> ShiftKind.EVENING
                    EmployeeDayStatus.SWAP_NIGHT -> ShiftKind.NIGHT
                    EmployeeDayStatus.SWAP_REST -> ShiftKind.REST
                }
                val myK = s.dayRecords[dK]?.swapKind ?: codeToKind(ShiftEngine.getShiftCode(dK, myGroup))
                val sameAsMe = dKind != null && dKind == myK

                val (cBg, cInk, cShort) = when (dKind) {
                    ShiftKind.DAY -> Triple(sk.daySoft, sk.dayInk, "ص")
                    ShiftKind.EVENING -> Triple(sk.eveningSoft, sk.eveningInk, "ع")
                    ShiftKind.NIGHT -> Triple(sk.nightSoft, sk.nightInk, "ش")
                    ShiftKind.REST -> Triple(sk.restSoft, sk.restInk, "ا")
                    null -> Triple(sk.holidaySoft, sk.holiday, "م")
                }

                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cBg)
                        .border(
                            width = if (sameAsMe) 1.4.dp else 0.8.dp,
                            color = if (sameAsMe) sk.primary else cInk.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        toPersianDigits(dJ.jd),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    Text(
                        cShort,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = cInk
                    )
                }
            }
        }
    }
}


/* ============================================================================
 * SUB-TAB 3: WORK, OVERTIME & PAYSLIP REPORT («گزارش کارکرد و اضافه‌کاری»)
 * ============================================================================ */

@Composable
private fun WorkReportSection(vm: AppViewModel, s: AppSettings) {
    val sk = LocalSk.current
    val haptic = rememberHaptics(s.hapticOn)
    val context: Any? = null
    val notes = vm.notesFlow.collectAsState().value
    val today = remember { todayJalaali() }

    var yearlyScope by remember { mutableStateOf(false) }
    var reportYear by remember(s.viewYear) { mutableIntStateOf(s.viewYear) }
    var reportMonth by remember(s.viewMonth) { mutableIntStateOf(s.viewMonth) }
    var exportReportOpen by remember { mutableStateOf(false) }

    val myGroup = s.myGroup.toShiftGroup()
    val activePattern = remember(s.activePatternId, s.customPatterns) {
        ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
    }

    val calSyncRev = CalendarSync.revision.collectAsState().value
    val reportData = remember(
        yearlyScope, reportYear, reportMonth, myGroup,
        s.dayRecords, notes, s.showEvents, activePattern, calSyncRev
    ) {
        var totalDays = 0
        var dayCnt = 0
        var eveCnt = 0
        var nightCnt = 0
        var restCnt = 0
        var totalOt = 0f
        var otDays = 0
        var swapCnt = 0
        var holWorkDays = 0
        var friWorkDays = 0
        val logs = mutableListOf<WorkReportLogEntry>()

        val monthsRange = if (yearlyScope) (1..12) else (reportMonth..reportMonth)
        for (m in monthsRange) {
            val len = jalaaliMonthLength(reportYear, m)
            for (d in 1..len) {
                totalDays++
                val key = makeDateKey(reportYear, m, d)
                val schedCode = ShiftEngine.getShiftCodeForPattern(key, myGroup, activePattern)
                val schedKind = codeToKind(schedCode)
                val rec = s.dayRecords[key]
                val effKind = rec?.swapKind ?: schedKind

                when (effKind) {
                    ShiftKind.DAY -> dayCnt++
                    ShiftKind.EVENING -> eveCnt++
                    ShiftKind.NIGHT -> nightCnt++
                    ShiftKind.REST -> restCnt++
                }

                val isHol = s.showEvents && Holidays.isHoliday(reportYear, m, d)
                val isFri = jalaaliWeekday(reportYear, m, d) == 6
                if (effKind != ShiftKind.REST) {
                    if (isHol) holWorkDays++
                    if (isFri) friWorkDays++
                }

                val ot = rec?.overtimeHours ?: 0f
                val sw = rec?.swapKind
                if (ot > 0.01f) {
                    totalOt += ot
                    otDays++
                }
                if (sw != null) {
                    swapCnt++
                }

                val noteTxt = notes[key]?.trim().orEmpty()
                if (ot > 0.01f || sw != null) {
                    val wd = formatWeekday(reportYear, m, d)
                    val dateLbl = "$wd ${toPersianDigits(d)} ${JALALI_MONTHS[m - 1]}"
                    val detailParts = buildList {
                        if (sw != null) {
                            add("برنامه: ${SHIFT_KIND_FA.getValue(schedKind)} ← ${SHIFT_KIND_FA.getValue(sw)}")
                        } else {
                            add("نوبت: ${CODE_FA.getValue(schedCode)}")
                        }
                        if (noteTxt.isNotEmpty()) {
                            add(if (noteTxt.length > 24) noteTxt.take(24) + "…" else noteTxt)
                        }
                    }.joinToString(" · ")

                    val badgeTxt = buildList {
                        if (ot > 0.01f) {
                            val otFmt = if (ot % 1f == 0f) toPersianDigits(ot.toInt()) else toPersianDigits(ot.toString())
                            add("+${otFmt} ساعت")
                        }
                        if (sw != null) add(SHIFT_KIND_FA.getValue(sw))
                    }.joinToString(" | ")

                    logs.add(
                        WorkReportLogEntry(
                            dateLabel = dateLbl,
                            detailText = detailParts,
                            badgeText = badgeTxt,
                            kind = effKind
                        )
                    )
                }
            }
        }

        val periodTitle = if (yearlyScope) {
            "کل سال ${toPersianDigits(reportYear)}"
        } else {
            "${JALALI_MONTHS[reportMonth - 1]} ${toPersianDigits(reportYear)}"
        }

        WorkReportSummaryData(
            periodTitle = periodTitle,
            groupTitle = GROUP_FA.getValue(myGroup),
            patternName = activePattern.name,
            totalDays = totalDays,
            dayShiftDays = dayCnt,
            eveningShiftDays = eveCnt,
            nightShiftDays = nightCnt,
            restDays = restCnt,
            totalOvertimeHours = totalOt,
            overtimeDaysCount = otDays,
            swapCount = swapCnt,
            holidayWorkDays = holWorkDays,
            fridayWorkDays = friWorkDays,
            logEntries = logs
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        /* 1. Scope & Period Header Card */
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "گزارش کارکرد، اضافه‌کاری و شیفت‌ها",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    Text(
                        "محاسبهٔ دقیق ساعت اضافه‌کاری، تعطیل‌کاری و نوبت‌ها برای بررسی فیش حقوقی",
                        fontSize = 11.sp,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                }
                Row(
                    Modifier
                        .height(34.dp)
                        .glass(RoundedCornerShape(11.dp), tint = sk.primarySoft)
                        .border(1.dp, sk.primary.copy(alpha = 0.5f), RoundedCornerShape(11.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            exportReportOpen = true
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(SkIcons.Image, null, Modifier.size(14.dp), tint = sk.primary)
                    Text("تصویر گزارش", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.primary)
                }
            }

            SegmentedControl(
                options = listOf("گزارش ماهانه", "گزارش کل سال"),
                selectedIndex = if (yearlyScope) 1 else 0,
                modifier = Modifier.fillMaxWidth()
            ) { idx ->
                haptic.tick()
                yearlyScope = (idx == 1)
            }

            /* Month / Year Stepper */
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sk.surface)
                    .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SkIconButton(SkIcons.ChevronRight, "قبلی", size = 34, iconSize = 16) {
                    haptic.tick()
                    if (yearlyScope) {
                        reportYear = (reportYear - 1).coerceAtLeast(1300)
                    } else {
                        if (reportMonth == 1) {
                            reportMonth = 12
                            reportYear = (reportYear - 1).coerceAtLeast(1300)
                        } else {
                            reportMonth -= 1
                        }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickableNoRipple {
                            haptic.tick()
                            reportYear = today.jy
                            reportMonth = today.jm
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        reportData.periodTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.primary
                    )
                    Text(
                        "${reportData.groupTitle} · مجموع ${toPersianDigits(reportData.totalDays)} روز",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                }
                SkIconButton(SkIcons.ChevronLeft, "بعدی", size = 34, iconSize = 16) {
                    haptic.tick()
                    if (yearlyScope) {
                        reportYear = (reportYear + 1).coerceAtMost(1500)
                    } else {
                        if (reportMonth == 12) {
                            reportMonth = 1
                            reportYear = (reportYear + 1).coerceAtMost(1500)
                        } else {
                            reportMonth += 1
                        }
                    }
                }
            }
        }

        /* 2. Key Payslip Metrics 2x2 Grid */
        val otFormatted = if (reportData.totalOvertimeHours % 1f == 0f) {
            toPersianDigits(reportData.totalOvertimeHours.toInt())
        } else {
            toPersianDigits(String.format(java.util.Locale.US, "%.1f", reportData.totalOvertimeHours))
        }
        val activeWorkTotal = reportData.dayShiftDays + reportData.eveningShiftDays + reportData.nightShiftDays

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "مجموع اضافه‌کاری",
                    value = "$otFormatted ساعت",
                    subtitle = "ثبت‌شده در ${toPersianDigits(reportData.overtimeDaysCount)} روز",
                    accentColor = sk.primary,
                    highlighted = true
                )
                ReportMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "کل روزهای کاری",
                    value = "${toPersianDigits(activeWorkTotal)} روز",
                    subtitle = "از ${toPersianDigits(reportData.totalDays)} روز دوره",
                    accentColor = sk.dayInk,
                    highlighted = false
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "شیفت در تعطیلات رسمی",
                    value = "${toPersianDigits(reportData.holidayWorkDays)} روز",
                    subtitle = "نوبت کاری در تعطیلی رسمی",
                    accentColor = sk.holiday,
                    highlighted = false
                )
                ReportMetricTile(
                    modifier = Modifier.weight(1f),
                    title = "جمعه کاری و جابه‌جایی",
                    value = "${toPersianDigits(reportData.fridayWorkDays)} جمعه",
                    subtitle = "${toPersianDigits(reportData.swapCount)} مورد جابه‌جایی نوبت",
                    accentColor = sk.restInk,
                    highlighted = false
                )
            }
        }

        /* 3. 4-Shift Breakdown Card */
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "تفکیک نوبت‌های کاری در ${reportData.periodTitle}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IRANSansX,
                color = sk.primary
            )

            val shiftBreakdown = buildList {
                add(Triple(ShiftKind.DAY, "صبح‌کار", reportData.dayShiftDays))
                if (reportData.eveningShiftDays > 0 || activePattern.eveningCount > 0) {
                    add(Triple(ShiftKind.EVENING, "عصرکار", reportData.eveningShiftDays))
                }
                add(Triple(ShiftKind.NIGHT, "شب‌کار", reportData.nightShiftDays))
                add(Triple(ShiftKind.REST, "استراحت", reportData.restDays))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                shiftBreakdown.forEach { (k, label, cnt) ->
                    val kc = kindColors(k)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(kc.brush)
                            .border(1.dp, kc.ink.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(kindIcon(k, 16), null, Modifier.size(16.dp), tint = kc.ink)
                        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = kc.ink)
                        Text("${toPersianDigits(cnt)} روز", fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = kc.ink)
                    }
                }
            }
        }

        /* 4. Detailed Log of Overtime & Swaps */
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ریز سوابق اضافه‌کاری و جابه‌جایی نوبت",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
                Text(
                    "${toPersianDigits(reportData.logEntries.size)} مورد",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
            }

            if (reportData.logEntries.isEmpty()) {
                Text(
                    "در این دوره هیچ اضافه‌کاری یا جابه‌جایی نوبتی ثبت نشده است. برای ثبت، در صفحهٔ تقویم روی روز مورد نظر ضربه بزنید.",
                    fontSize = 11.5.sp,
                    lineHeight = 20.sp,
                    fontFamily = IRANSansX,
                    color = sk.textFaint
                )
            } else {
                reportData.logEntries.forEach { entry ->
                    val kc = kindColors(entry.kind)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(sk.surface)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                entry.dateLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.text
                            )
                            Text(
                                entry.detailText,
                                fontSize = 10.5.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(kc.brush)
                                .border(1.dp, kc.ink.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                entry.badgeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = kc.ink
                            )
                        }
                    }
                }
            }
        }

        /* 5. Team / Colleagues Summary in this Period (if employees exist) */
        if (s.employees.isNotEmpty()) {
            val prefixFilter = if (yearlyScope) "$reportYear-" else "$reportYear-${pad2(reportMonth)}-"
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "خلاصهٔ کارکرد و مرخصی پرسنل در ${reportData.periodTitle}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
                s.employees.forEach { emp ->
                    val periodLogs = emp.dayLogs.filterKeys { it.startsWith(prefixFilter) }.values
                    val empOt = periodLogs.sumOf { it.overtimeHours.toDouble() }.toFloat()
                    val empLeaves = periodLogs.count { it.status == EmployeeDayStatus.LEAVE }
                    val empSwaps = periodLogs.count { it.status != EmployeeDayStatus.SCHEDULED && it.status != EmployeeDayStatus.LEAVE }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(sk.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                            "اضافه‌کاری: ${toPersianDigits(empOt.toInt())}س  ·  مرخصی: ${toPersianDigits(empLeaves)}  ·  جابه‌جایی: ${toPersianDigits(empSwaps)}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                    }
                }
            }
        }
    }

    ExportImageDialog(
        visible = exportReportOpen,
        title = "تصویر گزارش کارکرد (${reportData.periodTitle})",
        fileName = "shiftkar-report-$reportYear-${if (yearlyScope) "ALL" else pad2(reportMonth)}.png",
        hapticOn = s.hapticOn,
        produceBitmap = {
            ImageExporter.renderWorkReportBitmap(
                context = context,
                sk = sk,
                report = reportData
            )
        },
        onDismiss = { exportReportOpen = false }
    )
}

@Composable
private fun ReportMetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    highlighted: Boolean
) {
    val sk = LocalSk.current
    Column(
        modifier
            .glass(RoundedCornerShape(18.dp), tint = if (highlighted) sk.primarySoft else null)
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) sk.primary else sk.borderSoft,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IRANSansX,
            color = sk.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = IRANSansX,
            color = accentColor
        )
        Text(
            subtitle,
            fontSize = 10.sp,
            fontFamily = IRANSansX,
            color = sk.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
