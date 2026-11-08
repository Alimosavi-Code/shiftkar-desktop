package com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.kindColors
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.kindIcon
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.skOutline
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.skPalette
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Desktop Onboarding (v6.7.3) — 5 steps:
 *   ۱. تقویم هوشمند شیفت   ۲. شیفت شما   ۳. شخصی‌سازی نمایش
 *   ۴. رنگ دلخواه (شامل رنگ دینامیک سیستم)   ۵. همه‌چیز آماده است! (با کارت‌های بازشوندهٔ قابل ویرایش!)
 */

private const val TOTAL = 5

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)

    var group by remember { mutableStateOf(if (s.myGroup == GroupFilter.ALL) ShiftGroup.A else s.myGroup.toShiftGroup()) }
    var theme by remember { mutableStateOf(s.theme) }
    var themeMode by remember { mutableStateOf(s.themeMode) }
    var viewType by remember { mutableStateOf(s.viewType) }

    var expandGroup by remember { mutableStateOf(false) }
    var expandDisplay by remember { mutableStateOf(false) }
    var expandTheme by remember { mutableStateOf(false) }

    val today = remember { todayJalaali() }
    val todayKey = remember(today) { makeDateKey(today.jy, today.jm, today.jd) }

    fun finish() {
        haptic.tick(20)
        vm.set {
            it.copy(
                onboardingDone = true,
                myGroup = group.toGroupFilter(),
                filterGroup = group.toGroupFilter(),
                theme = theme,
                themeMode = themeMode,
                viewType = viewType
            )
        }
        onDone()
    }

    fun applyModeLive(mode: ThemeMode) { themeMode = mode; vm.setThemeMode(mode) }
    fun applyThemeLive(t: ThemeId) { theme = t; vm.setTheme(t) }
    fun applyViewLive(vt: ViewType) { viewType = vt; vm.setViewType(vt) }

    val pagerState = rememberPagerState(pageCount = { TOTAL })
    val scope = rememberCoroutineScope()
    val step = pagerState.currentPage
    fun go(i: Int) = scope.launch {
        haptic.tick(if (i > step) 10 else 6)
        pagerState.animateScrollToPage(i.coerceIn(0, TOTAL - 1))
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /* Step counter — TOP */
            Box(
                Modifier
                    .padding(top = 28.dp)
                    .clip(RoundedCornerShape(100))
                    .background(sk.primarySoft)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "گام ${toPersianDigits(step + 1)} از ${toPersianDigits(TOTAL)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.primary
                )
            }

            /* Pager body */
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        /* -------- Step 1: Welcome -------- */
                        0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HeroIcon(SkIcons.Calendar, 38)
                            Spacer(Modifier.height(22.dp))
                            Text(
                                "تقویم هوشمند شیفت",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.text,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "شیفت‌کار بر اساس تاریخ پایه، الگوی نوبت‌کاری و گروه شما، شیفت صبح‌کار، عصرکار، شب‌کار و استراحت را به‌صورت خودکار محاسبه می‌کند.",
                                Modifier.padding(top = 12.dp),
                                fontSize = 14.sp,
                                lineHeight = 26.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }

                        /* -------- Step 2: Pick Group -------- */
                        1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SoftIcon(SkIcons.Group, 28)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "شیفت شما کدام است؟",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.text
                            )
                            Text(
                                "وضعیت امروز هر گروه در پایینش نمایش داده شده",
                                Modifier.padding(top = 4.dp, bottom = 16.dp),
                                fontSize = 12.5.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                GROUPS.take(4).chunked(2).forEach { rowGroups ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowGroups.forEach { g ->
                                            val sToday = ShiftEngine.calculateShift(todayKey, g)
                                            val kc = kindColors(sToday.type)
                                            val active = group == g
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .skOutline(active, RoundedCornerShape(18.dp))
                                                    .clickableNoRipple { haptic.tick(12); group = g }
                                                    .padding(14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    GROUP_FA.getValue(g),
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = IRANSansX,
                                                    color = if (active) sk.primary else sk.text
                                                )
                                                Row(
                                                    Modifier
                                                        .clip(RoundedCornerShape(100))
                                                        .background(kc.brush)
                                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(kindIcon(sToday.type, 13), null, Modifier.size(13.dp), tint = kc.ink)
                                                    Text(
                                                        "امروز: ${SHIFT_KIND_FA.getValue(sToday.type)}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
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

                        /* -------- Step 3: Display Customization -------- */
                        2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SoftIcon(SkIcons.Monitor, 28)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "شخصی‌سازی نمایش",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.text
                            )
                            Text(
                                "این تنظیمات بعداً در «تنظیمات» هم در دسترس است",
                                Modifier.padding(top = 4.dp, bottom = 16.dp),
                                fontSize = 12.5.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                            Text(
                                "حالت نمایش",
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(ThemeMode.LIGHT, "روشن", SkIcons.Sun),
                                    Triple(ThemeMode.DARK, "تیره", SkIcons.Moon),
                                    Triple(ThemeMode.SYSTEM, "سیستم", SkIcons.Monitor)
                                ).forEach { (m, fa, ic) ->
                                    val active = themeMode == m
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .skOutline(active, RoundedCornerShape(16.dp))
                                            .clickableNoRipple { haptic.tick(12); applyModeLive(m) }
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(ic, fa, Modifier.size(19.dp), tint = if (active) sk.primary else sk.textMuted)
                                        Text(
                                            fa,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = IRANSansX,
                                            color = if (active) sk.primary else sk.textMuted
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "حالت نمایش جدول",
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(ViewType.GRID, "تقویم", SkIcons.Grid),
                                    Triple(ViewType.TABLE, "جدولی", SkIcons.Table)
                                ).forEach { (vt, fa, ic) ->
                                    val active = viewType == vt
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .skOutline(active, RoundedCornerShape(16.dp))
                                            .clickableNoRipple { haptic.tick(12); applyViewLive(vt) }
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(ic, fa, Modifier.size(19.dp), tint = if (active) sk.primary else sk.textMuted)
                                        Text(
                                            fa,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = IRANSansX,
                                            color = if (active) sk.primary else sk.textMuted
                                        )
                                    }
                                }
                            }
                        }

                        /* -------- Step 4: Theme Picker -------- */
                        3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SoftIcon(SkIcons.Palette, 28)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "رنگ دلخواه را انتخاب کنید",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.text
                            )
                            Text(
                                "با انتخاب هر رنگ، تم برنامه همان لحظه تغییر می‌کند",
                                Modifier.padding(top = 4.dp, bottom = 16.dp),
                                fontSize = 12.5.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                            val dynPal = remember(themeMode) {
                                skPalette(ThemeId.DYNAMIC, dark = themeMode != ThemeMode.LIGHT)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ThemeId.entries.chunked(4).forEach { rowThemes ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowThemes.forEach { t ->
                                            val active = theme == t
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .skOutline(active, RoundedCornerShape(18.dp))
                                                    .clickableNoRipple { haptic.tick(12); applyThemeLive(t) }
                                                    .padding(vertical = 14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(if (t == ThemeId.DYNAMIC) dynPal.primary else Color(t.hex))
                                                        .border(2.dp, sk.surfaceStrong, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (t == ThemeId.DYNAMIC) {
                                                        Icon(SkIcons.Spark, null, Modifier.size(14.dp), tint = Color.White)
                                                    }
                                                }
                                                Text(
                                                    t.fa,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = IRANSansX,
                                                    color = if (active) sk.primary else sk.textMuted
                                                )
                                            }
                                        }
                                        if (rowThemes.size < 4) {
                                            repeat(4 - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }

                        /* -------- Step 5: Interactive Expandable Summary -------- */
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HeroIcon(SkIcons.Spark, 36)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "همه‌چیز آماده است!",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.text
                            )
                            Text(
                                "خلاصهٔ انتخاب‌های شما (برای تغییر روی هر مورد کلیک کنید):",
                                Modifier.padding(top = 4.dp, bottom = 14.dp),
                                fontSize = 12.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )

                            /* 1. Interactive شیفت شما card */
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .glass(RoundedCornerShape(16.dp))
                                    .clickableNoRipple {
                                        haptic.tick(10)
                                        expandGroup = !expandGroup
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("شیفت شما", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(GROUP_FA.getValue(group), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary)
                                        Icon(if (expandGroup) SkIcons.ChevronUp else SkIcons.ChevronDown, null, Modifier.size(14.dp), tint = sk.textFaint)
                                    }
                                }
                                AnimatedVisibility(visible = expandGroup) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        GROUPS.take(4).forEach { g ->
                                            val active = group == g
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (active) sk.primary else sk.surface)
                                                    .border(1.dp, if (active) sk.primary else sk.borderSoft, RoundedCornerShape(12.dp))
                                                    .clickableNoRipple {
                                                        haptic.tick(12)
                                                        group = g
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    GROUP_FA.getValue(g),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = IRANSansX,
                                                    color = if (active) Color.White else sk.text
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            /* 2. Interactive حالت نمایش card */
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .glass(RoundedCornerShape(16.dp))
                                    .clickableNoRipple {
                                        haptic.tick(10)
                                        expandDisplay = !expandDisplay
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("حالت نمایش", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "${themeMode.fa} • ${if (viewType == ViewType.GRID) "تقویم" else "جدولی"}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = IRANSansX,
                                            color = sk.primary
                                        )
                                        Icon(if (expandDisplay) SkIcons.ChevronUp else SkIcons.ChevronDown, null, Modifier.size(14.dp), tint = sk.textFaint)
                                    }
                                }
                                AnimatedVisibility(visible = expandDisplay) {
                                    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(ThemeMode.SYSTEM to "سیستم", ThemeMode.DARK to "تیره", ThemeMode.LIGHT to "روشن").forEach { (m, label) ->
                                                val active = themeMode == m
                                                Box(
                                                    Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (active) sk.primary else sk.surface)
                                                        .border(1.dp, if (active) sk.primary else sk.borderSoft, RoundedCornerShape(12.dp))
                                                        .clickableNoRipple {
                                                            haptic.tick(12)
                                                            applyModeLive(m)
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        label,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = IRANSansX,
                                                        color = if (active) Color.White else sk.text
                                                    )
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(ViewType.GRID to "نمای تقویم", ViewType.TABLE to "نمای جدولی").forEach { (vt, label) ->
                                                val active = viewType == vt
                                                Box(
                                                    Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (active) sk.primary else sk.surface)
                                                        .border(1.dp, if (active) sk.primary else sk.borderSoft, RoundedCornerShape(12.dp))
                                                        .clickableNoRipple {
                                                            haptic.tick(12)
                                                            applyViewLive(vt)
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        label,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = IRANSansX,
                                                        color = if (active) Color.White else sk.text
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            /* 3. Interactive رنگ برنامه card */
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .glass(RoundedCornerShape(16.dp))
                                    .clickableNoRipple {
                                        haptic.tick(10)
                                        expandTheme = !expandTheme
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("رنگ برنامه", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(if (theme == ThemeId.DYNAMIC) sk.primary else Color(theme.hex))
                                        )
                                        Text(
                                            theme.fa,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = IRANSansX,
                                            color = sk.primary
                                        )
                                        Icon(if (expandTheme) SkIcons.ChevronUp else SkIcons.ChevronDown, null, Modifier.size(14.dp), tint = sk.textFaint)
                                    }
                                }
                                AnimatedVisibility(visible = expandTheme) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ThemeId.entries.forEach { t ->
                                            val active = theme == t
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (t == ThemeId.DYNAMIC) skPalette(ThemeId.DYNAMIC, dark = themeMode != ThemeMode.LIGHT).primary
                                                        else Color(t.hex)
                                                    )
                                                    .then(
                                                        if (active) Modifier.border(2.5.dp, Color.White, RoundedCornerShape(12.dp))
                                                        else Modifier.border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                                                    )
                                                    .clickableNoRipple {
                                                        haptic.tick(12)
                                                        applyThemeLive(t)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (active) {
                                                    Icon(SkIcons.Check, null, Modifier.size(13.dp), tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* nav buttons */
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    Box(
                        Modifier
                            .glass(RoundedCornerShape(16.dp))
                            .clickableNoRipple { haptic.tick(); go(step - 1) }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text("قبلی", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.primary)
                        .clickableNoRipple {
                            if (step < TOTAL - 1) go(step + 1) else finish()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (step < TOTAL - 1) "بعدی" else "شروع کنید",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = IRANSansX,
                            color = Color.White
                        )
                        Icon(
                            SkIcons.ChevronLeft,
                            null,
                            Modifier.padding(start = 8.dp).size(17.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            /* progress dots — BOTTOM */
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TOTAL) { i ->
                    val active = i == step
                    val passed = i < step
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (active) 28.dp else 10.dp, height = 6.dp)
                            .clip(RoundedCornerShape(100))
                            .background(
                                when {
                                    active -> sk.primary
                                    passed -> sk.primary.copy(alpha = 0.45f)
                                    else -> sk.border
                                }
                            )
                            .clickableNoRipple { haptic.tick(8); go(i) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int) {
    val sk = LocalSk.current
    Box(
        Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(sk.primary, sk.primaryStrong))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size.dp), tint = Color.White)
    }
}

@Composable
private fun SoftIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int) {
    val sk = LocalSk.current
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(sk.primarySoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size.dp), tint = sk.primary)
    }
}
