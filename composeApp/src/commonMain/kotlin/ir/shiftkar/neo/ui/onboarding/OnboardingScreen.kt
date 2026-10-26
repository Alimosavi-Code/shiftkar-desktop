package ir.shiftkar.neo.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import ir.shiftkar.neo.engine.*
import ir.shiftkar.neo.ui.AppViewModel
import ir.shiftkar.neo.ui.components.CheckBadge
import ir.shiftkar.neo.ui.components.SkIcons
import ir.shiftkar.neo.ui.components.clickableNoRipple
import ir.shiftkar.neo.ui.components.glass
import ir.shiftkar.neo.ui.components.skOutline
import ir.shiftkar.neo.ui.theme.IRANSansX
import ir.shiftkar.neo.ui.theme.LocalSk
import ir.shiftkar.neo.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Desktop Onboarding — 5 clean steps (Android-only permission slides removed):
 *   ۱. تقویم هوشمند شیفت   ۲. شیفت شما   ۳. شخصی‌سازی نمایش
 *   ۴. رنگ دلخواه   ۵. همه‌چیز آماده است!
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

    val today = remember { todayJalaali() }
    val todayKey = remember(today) { makeDateKey(today.jy, today.jm, today.jd) }

    fun finish() {
        haptic.tick(20)
        vm.set {
            it.copy(
                onboardingDone = true,
                myGroup = GroupFilter.valueOf(group.name),
                filterGroup = GroupFilter.valueOf(group.name),
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
                .widthIn(max = 560.dp)
                .fillMaxSize()
        ) {
            /* step counter — TOP */
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "گام ${toPersianDigits(step + 1)} از ${toPersianDigits(TOTAL)}",
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .background(sk.primarySoft)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.primary
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (page) {
                        0 -> {
                            HeroIcon(SkIcons.Calendar, 38)
                            Text(
                                "تقویم هوشمند شیفت",
                                Modifier.padding(top = 24.dp), textAlign = TextAlign.Center,
                                fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                            )
                            Text(
                                "${APP.NAME} بر اساس تاریخ پایه و گروه شما، شیفت روز، شب و استراحت را برای هر روز به‌صورت خودکار محاسبه می‌کند — بدون نیاز به محاسبه دستی.",
                                Modifier.padding(top = 12.dp), textAlign = TextAlign.Center,
                                fontSize = 14.sp, lineHeight = 28.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                        }

                        1 -> {
                            SoftIcon(SkIcons.Group, 30)
                            Text(
                                "شیفت شما کدام است؟",
                                Modifier.padding(top = 24.dp), textAlign = TextAlign.Center,
                                fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                            )
                            Text(
                                "تقویم بر اساس گروه شما شخصی‌سازی می‌شود",
                                Modifier.padding(top = 8.dp),
                                fontSize = 14.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GROUPS.forEach { g ->
                                    val active = group == g
                                    val preview = ShiftEngine.calculateShift(todayKey, g).type
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .then(
                                                if (active) Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(sk.primary)
                                                else Modifier.glass(RoundedCornerShape(16.dp))
                                            )
                                            .clickableNoRipple { haptic.tick(12); group = g }
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(g.faLetter(), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = if (active) sk.sheetSolid else sk.text)
                                        val kc = when (preview) {
                                            ShiftKind.DAY -> sk.daySoft to sk.dayInk
                                            ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
                                            ShiftKind.REST -> sk.restSoft to sk.restInk
                                        }
                                        Row(
                                            Modifier
                                                .padding(top = 4.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (active) Color.White.copy(alpha = 0.2f) else kc.first)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                when (preview) {
                                                    ShiftKind.DAY -> SkIcons.Sun
                                                    ShiftKind.NIGHT -> SkIcons.Moon
                                                    ShiftKind.REST -> SkIcons.Rest
                                                }, null, Modifier.size(11.dp),
                                                tint = if (active) sk.sheetSolid else kc.second
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(ShiftKind.DAY, "صبح‌کار", SkIcons.Sun),
                                    Triple(ShiftKind.NIGHT, "شب‌کار", SkIcons.Moon),
                                    Triple(ShiftKind.REST, "استراحت", SkIcons.Rest)
                                ).forEach { (kind, fa, icon) ->
                                    val kc = when (kind) {
                                        ShiftKind.DAY -> sk.daySoft to sk.dayInk
                                        ShiftKind.NIGHT -> sk.nightSoft to sk.nightInk
                                        ShiftKind.REST -> sk.restSoft to sk.restInk
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(kc.first)
                                            .padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(icon, fa, Modifier.size(15.dp), tint = kc.second)
                                        Text(fa, Modifier.padding(top = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = kc.second)
                                    }
                                }
                            }
                            Text(
                                "همین نشان‌ها را در کل تقویم می‌بینید: خورشید یعنی صبح‌کار، ماه یعنی شب‌کار و نشان استراحت یعنی روز آزاد شما.",
                                Modifier.padding(top = 12.dp), textAlign = TextAlign.Center,
                                fontSize = 11.sp, lineHeight = 20.sp, fontFamily = IRANSansX, color = sk.textFaint
                            )
                        }

                        2 -> {
                            SoftIcon(SkIcons.Monitor, 30)
                            Text(
                                "شخصی‌سازی نمایش",
                                Modifier.padding(top = 20.dp), textAlign = TextAlign.Center,
                                fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                            )
                            Text(
                                "این تنظیمات بعداً در «تنظیمات» هم در دسترس است",
                                Modifier.padding(top = 8.dp),
                                fontSize = 13.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                            Spacer(Modifier.height(20.dp))
                            Text("حالت نمایش", Modifier.fillMaxWidth(), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(ThemeMode.SYSTEM to "سیستم", ThemeMode.DARK to "تیره", ThemeMode.LIGHT to "روشن").forEach { (mode, fa) ->
                                    val active = themeMode == mode
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .skOutline(active, RoundedCornerShape(16.dp))
                                            .clickableNoRipple { haptic.tick(12); applyModeLive(mode) }
                                            .padding(vertical = 10.dp)
                                    ) {
                                        if (active) CheckBadge(Modifier.align(Alignment.TopEnd).padding(7.dp))
                                        Column(
                                            Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                when (mode) {
                                                    ThemeMode.SYSTEM -> SkIcons.Monitor
                                                    ThemeMode.DARK -> SkIcons.Moon
                                                    ThemeMode.LIGHT -> SkIcons.Sun
                                                }, fa, Modifier.size(19.dp),
                                                tint = if (active) sk.primary else sk.textMuted
                                            )
                                            Text(fa, Modifier.padding(top = 4.dp), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = if (active) sk.primary else sk.textMuted)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("حالت نمایش جدول", Modifier.fillMaxWidth(), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(ViewType.TABLE to "جدولی", ViewType.GRID to "تقویم").forEach { (vt, fa) ->
                                    val active = viewType == vt
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .skOutline(active, RoundedCornerShape(16.dp))
                                            .clickableNoRipple { haptic.tick(12); applyViewLive(vt) }
                                            .padding(vertical = 10.dp)
                                    ) {
                                        if (active) CheckBadge(Modifier.align(Alignment.TopEnd).padding(7.dp))
                                        Column(
                                            Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                Modifier.height(23.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (vt == ViewType.TABLE) SkIcons.Table else SkIcons.Grid, fa,
                                                    Modifier.size(if (vt == ViewType.TABLE) 19.dp else 23.dp),
                                                    tint = if (active) sk.primary else sk.textMuted
                                                )
                                            }
                                            Text(fa, Modifier.padding(top = 6.dp), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = if (active) sk.primary else sk.textMuted)
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            SoftIcon(SkIcons.Palette, 30)
                            Text(
                                "رنگ دلخواه را انتخاب کنید",
                                Modifier.padding(top = 24.dp), textAlign = TextAlign.Center,
                                fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                            )
                            Text(
                                "بعداً از تنظیمات هم قابل تغییر است — همراه با حالت تیره",
                                Modifier.padding(top = 8.dp),
                                fontSize = 14.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeId.entries.forEach { t ->
                                    val active = theme == t
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Brush.linearGradient(listOf(Color(t.hex), Color(t.hex).copy(alpha = 0.53f))))
                                            .then(
                                                if (active) Modifier.border(
                                                    2.dp, Color(t.hex), RoundedCornerShape(16.dp)
                                                ) else Modifier
                                            )
                                            .clickableNoRipple { haptic.tick(12); applyThemeLive(t) }
                                    )
                                }
                            }
                            Text(
                                theme.fa,
                                Modifier.padding(top = 12.dp),
                                fontSize = 12.sp, fontFamily = IRANSansX, color = sk.textFaint
                            )
                        }

                        4 -> {
                            HeroIcon(SkIcons.Spark, 30, soft = false)
                            Text(
                                "همه‌چیز آماده است!",
                                Modifier.padding(top = 20.dp), textAlign = TextAlign.Center,
                                fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .glass(RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("شیفت شما", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                Text(GROUP_FA.getValue(group), fontSize = 12.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .glass(RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("حالت نمایش", fontSize = 12.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                                Text(
                                    "${themeMode.fa} • ${if (viewType == ViewType.GRID) "تقویم" else "جدولی"}",
                                    fontSize = 12.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                                )
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
                            fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.sheetSolid
                        )
                        Icon(
                            SkIcons.ChevronLeft, null, Modifier.padding(start = 8.dp).size(17.dp),
                            tint = sk.sheetSolid
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
private fun HeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int, soft: Boolean = true) {
    val sk = LocalSk.current
    Box(
        Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(sk.primary, sk.primaryStrong))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size.dp), tint = sk.sheetSolid)
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
