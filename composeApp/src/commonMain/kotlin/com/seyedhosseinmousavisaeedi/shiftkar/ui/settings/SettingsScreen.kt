package com.seyedhosseinmousavisaeedi.shiftkar.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.platform.openExternalUrl
import com.seyedhosseinmousavisaeedi.shiftkar.platform.openTextFileWithDialog
import com.seyedhosseinmousavisaeedi.shiftkar.platform.saveTextFileWithDialog
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.SkStrings
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.skPalette
import com.seyedhosseinmousavisaeedi.shiftkar.updater.AppUpdateConfig
import com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings screen (v6.7.3 Desktop Edition):
 *  - Dynamic shift groups + shortcut to `مدیریت شیفت`
 *  - Theme Mode (`روشن | تیره | هماهنگ با سیستم`)
 *  - 7 Theme Swatches (`پویا (سیستم)` OS Accent Color + 6 signature palettes)
 *  - Desktop Floating Mini-Widget (`ویجت شناور رومیزی`) toggle, Always-on-Top pin, and opacity
 *  - Unified Cross-Platform Backup & Restore (`.json` compatible with Android v6.7.0 & Web v6.7.3)
 *  - Online Calendar Sync (`CalendarSync`) + Desktop Updater
 *  - 2-Column Colored Brand Contact Cards (`راه‌های ارتباطی`)
 */

private data class BrandContact(
    val id: String,
    val title: String,
    val sub: String,
    val icon: ImageVector,
    val href: String,
    val accentHex: Long,
    val fullSpan: Boolean = false
)

private val BRAND_CONTACTS = listOf(
    BrandContact("whatsapp-pv", "پیام در واتساپ", "گفتگوی مستقیم", SkIcons.Whatsapp, "https://wa.me/989163548578", 0xFF10B981),
    BrandContact("whatsapp-group", "گروه واتساپ", "انجمن کاربران شیفت‌کار", SkIcons.Whatsapp, "https://chat.whatsapp.com/5HolMMoVnojCdh8GjhSZo2", 0xFF059669),
    BrandContact("telegram", "تلگرام", "@h_mosavi_s", SkIcons.Telegram, "https://t.me/h_mosavi_s", 0xFF0EA5E9),
    BrandContact("eitaa", "گروه ایتا", "کانال و گروه رسمی", SkIcons.Eitaa, "https://eitaa.com/joinchat/929104214C56d88e3e24", 0xFFF97316),
    BrandContact("phone", "شماره تماس مستقیم", "۰۹۱۶۳۵۴۸۵۷۸ (کلیک برای کپی شماره)", SkIcons.Phone, "tel:09163548578", 0xFF6366F1, fullSpan = true)
)

private data class HelpItem(val title: String, val body: String)

private val HELP_ITEMS = listOf(
    HelpItem(
        "محاسبه شیفت و ۴۵ الگوی آماده",
        "برنامه به‌صورت پیش‌فرض روی چرخهٔ ۸ روزهٔ پتروشیمی بندر امام (۲ روز صبح‌کار، ۲ روز شب‌کار، ۴ روز استراحت) تنظیم است و در تب «مدیریت شیفت» می‌توانید از بین ۴۵ الگوی آماده انتخاب کنید یا الگوی سفارشی خودتان را بسازید."
    ),
    HelpItem(
        "ویجت رومیزی دسکتاپ و آیکون کنار ساعت",
        "در نسخهٔ دسکتاپ، آیکون برنامه در کنار ساعت سیستم‌عامل (System Tray) عدد روزِ ماه و رنگ شیفت امروز شما را زنده نمایش می‌دهد. همچنین می‌توانید «ویجت شناور رومیزی» را روشن کنید تا کارت شیشه‌ای شیفت امروز روی دسکتاپ شما نمایش داده شود."
    ),
    HelpItem(
        "پشتیبان‌گیری یکپارچه بین اندروید، وب و دسکتاپ",
        "فایل پشتیبان (.json) شامل تمام یادداشت‌ها، ساعات اضافه‌کاری، جابه‌جایی نوبت‌ها، لیست همکاران و الگوهای سفارشی است و بین نسخهٔ اندروید، وب و دسکتاپ کاملاً قابل انتقال است."
    ),
    HelpItem(
        "میانبرهای صفحه‌کلید دسکتاپ",
        "• کلیدهای جهت‌نمای چپ و راست: حرکت بین ماه‌ها\n• کلید Ctrl+T: بازگشت سریع به ماه جاری (امروز)\n• کلید Ctrl+F: باز کردن پنجرهٔ جستجوی یادداشت‌ها\n• کلید Esc: بستن پنجره‌های باز\n• کلید F11: حالت تمام‌صفحه"
    )
)

private fun pad2(n: Int): String = n.toString().padStart(2, '0')

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenUpdater: () -> Unit,
    onStartTour: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val upState = Updater.state.collectAsState().value
    val notes = vm.notesFlow.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "تنظیمات",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = IRANSansX,
            color = sk.text
        )
        Text(
            "شخصی‌سازی برنامه و امکانات دسکتاپ",
            Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            fontFamily = IRANSansX,
            color = sk.textMuted
        )
        Spacer(Modifier.height(16.dp))

        /* ============ 1. شیفت شما ============ */
        Section(SkIcons.Group, "شیفت شما", "گروه کاری خود را انتخاب کنید؛ تقویم، لوحه و ویجت دسکتاپ بر همین اساس رنگ می‌شود.") {
            if (ShiftEngine.activePattern.hasGroups && GROUPS.size > 1) {
                SegmentedControl(
                    options = GROUPS.map { it.faLetter() },
                    selectedIndex = GROUPS.indexOf(s.myGroup.toShiftGroup()).coerceAtLeast(0)
                ) { i ->
                    haptic.tick()
                    val g = GROUPS.getOrElse(i) { ShiftGroup.A }.toGroupFilter()
                    vm.set { it.copy(myGroup = g, filterGroup = g) }
                    ToastCenter.show("شیفت شما: ${GROUP_FA.getValue(g.toShiftGroup())}")
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(sk.primarySoft)
                        .border(1.dp, sk.primary, RoundedCornerShape(14.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "الگوی فعلی: شیفت فردی (بدون گروه)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.primary
                    )
                }
            }
        }

        /* ============ 2. حالت نمایش ============ */
        Section(SkIcons.Palette, "حالت نمایش", "روشن، تیره یا پیروی خودکار از تم سیستم‌عامل؛ تغییر به‌صورت زنده اعمال می‌شود.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(ThemeMode.LIGHT, "روشن", SkIcons.Sun),
                    Triple(ThemeMode.DARK, "تیره", SkIcons.Moon),
                    Triple(ThemeMode.SYSTEM, "هماهنگ با سیستم", SkIcons.Monitor)
                ).forEach { (mode, fa, icon) ->
                    val active = s.themeMode == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .skOutline(active, RoundedCornerShape(16.dp))
                            .clickableNoRipple { haptic.tick(); vm.setThemeMode(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(icon, fa, Modifier.size(20.dp), tint = if (active) sk.primary else sk.textMuted)
                            Text(
                                fa,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                textAlign = TextAlign.Center,
                                color = if (active) sk.primary else sk.textMuted
                            )
                        }
                    }
                }
            }
        }

        /* ============ 3. رنگ اصلی (شامل رنگ دینامیک سیستم‌عامل) ============ */
        Section(
            SkIcons.Palette,
            "رنگ اصلی",
            "رنگ اصلی برنامه را انتخاب کنید. گزینهٔ «پویا (سیستم)» رنگ تأکیدی ویندوز، مک یا لینوکس شما را به‌صورت هوشمند استخراج می‌کند."
        ) {
            val dynPal = remember(s.themeMode) {
                skPalette(ThemeId.DYNAMIC, dark = s.themeMode != ThemeMode.LIGHT)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeId.entries.chunked(4).forEach { rowThemes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowThemes.forEach { t ->
                            val active = s.theme == t
                            Column(
                                Modifier
                                    .weight(1f)
                                    .skOutline(active, RoundedCornerShape(16.dp))
                                    .clickableNoRipple { haptic.tick(); vm.setTheme(t) }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (t == ThemeId.DYNAMIC) {
                                                Modifier.background(
                                                    Brush.linearGradient(listOf(dynPal.primary, dynPal.primaryStrong))
                                                )
                                            } else {
                                                Modifier.background(Color(t.hex))
                                            }
                                        )
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
                                    color = if (active) sk.primary else sk.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                        if (rowThemes.size < 4) {
                            repeat(4 - rowThemes.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        /* ============ 4. نمای تقویم و نمایش مناسبت‌ها ============ */
        Section(SkIcons.Grid, "نمای تقویم", "حالت نمایش تقویم (شبکه‌ای) یا جدولی و نمایش مناسبت‌ها را تنظیم کنید.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ViewType.GRID to "تقویم", ViewType.TABLE to "جدولی").forEach { (vt, fa) ->
                        val active = s.viewType == vt
                        Box(
                            Modifier
                                .weight(1f)
                                .skOutline(active, RoundedCornerShape(16.dp))
                                .clickableNoRipple { haptic.tick(); vm.setViewType(vt) }
                                .padding(12.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ViewPreview(vt)
                                Text(
                                    fa,
                                    Modifier.padding(top = 10.dp),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = if (active) sk.primary else sk.textMuted
                                )
                            }
                        }
                    }
                }

                ToggleRow(
                    title = "نمایش تعطیلات و مناسبت‌ها روی تقویم",
                    subtitle = "رنگ قرمز تعطیلات رسمی و نمایش مناسبت‌های روز",
                    checked = s.showEvents,
                    onToggle = {
                        haptic.tick()
                        vm.setShowEvents(!s.showEvents)
                    }
                )
            }
        }

        /* ============ 4.5. ویجت شناور رومیزی دسکتاپ (Desktop Floating Mini-Widget) ============ */
        Section(
            SkIcons.Spark,
            "ویجت شناور رومیزی (Desktop Widget)",
            "یک ویجت شیشه‌ای و ظریف روی دسکتاپ شما که تاریخ امروز، نوبت کاری شما و وضعیت هر ۴ گروه را به‌صورت زنده نشان می‌دهد و با ماوس قابل جابه‌جایی است."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleRow(
                    title = "نمایش ویجت شناور روی دسکتاپ",
                    subtitle = "نمایش پنجرهٔ ویجت کوچک کنار صفحهٔ دسکتاپ",
                    checked = s.desktopWidgetEnabled,
                    onToggle = {
                        haptic.tick()
                        val next = !s.desktopWidgetEnabled
                        vm.setDesktopWidgetEnabled(next)
                        ToastCenter.show(if (next) "ویجت شناور دسکتاپ فعال شد" else "ویجت شناور دسکتاپ بسته شد")
                    }
                )

                ToggleRow(
                    title = "ثابت ماندن روی همهٔ پنجره‌ها (Always on Top)",
                    subtitle = "ویجت همیشه بالای سایر پنجره‌های باز قرار بگیرد",
                    checked = s.desktopWidgetAlwaysOnTop,
                    onToggle = {
                        haptic.tick()
                        vm.setDesktopWidgetAlwaysOnTop(!s.desktopWidgetAlwaysOnTop)
                    }
                )

                val opacities = listOf(100 to "شیشه‌ای (۱۰۰٪)", 75 to "نیمه‌شفاف (۷۵٪)", 50 to "شفاف (۵۰٪)")
                SegmentedControl(
                    options = opacities.map { it.second },
                    selectedIndex = opacities.indexOfFirst { it.first == s.widgetOpacity }.coerceAtLeast(0)
                ) { idx ->
                    haptic.tick()
                    val picked = opacities[idx].first
                    vm.setWidgetOpacity(picked)
                    ToastCenter.show("شفافیت ویجت دسکتاپ: ${opacities[idx].second}")
                }
            }
        }

        /* ============ 5. پشتیبان‌گیری و بازیابی یکپارچه (Android ↔ Web ↔ Desktop) ============ */
        Section(
            SkIcons.Notes,
            "پشتیبان‌گیری و بازیابی یکپارچه",
            "فایل پشتیبان (.json) شامل یادداشت‌ها، اضافه‌کاری‌ها، جابه‌جایی‌ها، همکاران، تنظیمات و الگوهای سفارشی است و با نسخه‌های اندروید و وب کاملاً سازگار است."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "اطلاعات آمادهٔ پشتیبان‌گیری:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pills = listOf(
                            "${toPersianDigits(notes.size)} یادداشت",
                            "${toPersianDigits(s.dayRecords.size)} اضافه‌کاری/جابه‌جایی",
                            "${toPersianDigits(s.employees.size)} همکار",
                            "${toPersianDigits(s.customPatterns.size)} الگوی سفارشی"
                        )
                        pills.forEach { badgeText ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(sk.primarySoft)
                                    .border(1.dp, sk.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = IRANSansX,
                                    color = sk.primary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(sk.primary)
                            .clickableNoRipple {
                                haptic.tick()
                                scope.launch(Dispatchers.Default) {
                                    val t = todayJalaali()
                                    val defaultFn = "shiftkar-backup-${t.jy}-${pad2(t.jm)}-${pad2(t.jd)}.json"
                                    val json = vm.notesRepo.exportJson(
                                        dayRecords = s.dayRecords,
                                        customPatterns = s.customPatterns,
                                        employees = s.employees,
                                        activePatternId = s.activePatternId,
                                        settings = s
                                    )
                                    val savedPath = saveTextFileWithDialog(defaultFn, json)
                                    withContext(Dispatchers.Main) {
                                        if (savedPath != null) {
                                            ToastCenter.show("فایل پشتیبان با موفقیت ذخیره شد")
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(SkIcons.Download, null, Modifier.size(16.dp), tint = Color.White)
                        Text(
                            "ذخیرهٔ فایل پشتیبان",
                            Modifier.padding(start = 7.dp),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = Color.White
                        )
                    }

                    Row(
                        Modifier
                            .weight(1f)
                            .glass(RoundedCornerShape(16.dp))
                            .border(1.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickableNoRipple {
                                haptic.tick()
                                scope.launch(Dispatchers.Default) {
                                    val raw = openTextFileWithDialog() ?: return@launch
                                    val restored = runCatching {
                                        val bundle = vm.notesRepo.importFullBackup(raw)
                                        vm.mergeImportedBundle(bundle)
                                        bundle.totalRestoredCount
                                    }.getOrDefault(-1)
                                    withContext(Dispatchers.Main) {
                                        if (restored >= 0) {
                                            ToastCenter.show("${toPersianDigits(restored)} مورد (یادداشت، اضافه‌کاری، پرسنل و تنظیمات) بازیابی شد")
                                        } else {
                                            ToastCenter.show("فایل پشتیبان معتبر نیست")
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(SkIcons.Refresh, null, Modifier.size(16.dp), tint = sk.primary)
                        Text(
                            "بازیابی از فایل پشتیبان",
                            Modifier.padding(start = 7.dp),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                    }
                }
            }
        }

        /* ============ 6. به‌روزرسانی برنامه و تقویم ============ */
        Section(SkIcons.Download, "به‌روزرسانی برنامه و تقویم", "بررسی نسخه‌های جدید دسکتاپ از گیت‌هاب و همگام‌سازی آنلاین تعطیلات رسمی.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val lastKnown = remember(upState.status, upState.info?.versionCode) { Updater.lastKnownAvailable() }
                val bannerInfo =
                    if (upState.status == UpdateState.Status.AVAILABLE && upState.info != null) upState.info
                    else lastKnown
                if (bannerInfo != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(sk.primary.copy(alpha = 0.22f), sk.primary.copy(alpha = 0.10f))
                                )
                            )
                            .border(2.dp, sk.primary.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .clickableNoRipple {
                                haptic.tick()
                                onOpenUpdater()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(sk.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SkIcons.Download, null, Modifier.size(19.dp), tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                "بروزرسانی جدید منتشر شد!",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = sk.primary
                            )
                            Text(
                                "نسخهٔ ${toPersianDigits(bannerInfo.versionName)} آمادهٔ دریافت و نصب است",
                                Modifier.padding(top = 2.dp),
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                        }
                        Icon(SkIcons.ChevronLeft, null, Modifier.size(16.dp), tint = sk.primary)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(sk.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Calendar, null, Modifier.size(20.dp), tint = sk.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("نسخهٔ نصب‌شده", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted)
                        Text(
                            "${APP.NAME} دسکتاپ — نسخهٔ ${toPersianDigits(APP.VERSION)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.text
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.primary)
                        .clickableNoRipple { haptic.tick(); onOpenUpdater() }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(SkIcons.Refresh, null, Modifier.size(16.dp), tint = Color.White)
                    Text(
                        "بررسی به‌روزرسانی برنامه",
                        Modifier.padding(start = 8.dp),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = Color.White
                    )
                }

                val calSyncRev = CalendarSync.revision.collectAsState().value
                val calSyncing = CalendarSync.isSyncing.collectAsState().value
                @Suppress("UNUSED_VARIABLE")
                val revWatch = calSyncRev
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickableNoRipple(enabled = !calSyncing) {
                            haptic.tick()
                            scope.launch {
                                val res = CalendarSync.syncNow()
                                when (res.getOrNull()) {
                                    CalendarSync.SyncOutcome.UPDATED ->
                                        ToastCenter.show("تقویم و مناسبت‌ها به نسخهٔ ${toPersianDigits(CalendarSync.syncVersion)} بروزرسانی شد")
                                    CalendarSync.SyncOutcome.UP_TO_DATE ->
                                        ToastCenter.show("تقویم و مناسبت‌ها هم‌اکنون در آخرین نسخه است")
                                    null ->
                                        ToastCenter.show("خطا در دریافت فایل تقویم — از جدول آفلاین برنامه استفاده می‌شود")
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(sk.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Calendar, null, Modifier.size(16.dp), tint = sk.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            if (calSyncing) "در حال بررسی بروزرسانی تقویم و مناسبت‌ها…"
                            else "بروزرسانی آنلاین تقویم و مناسبت‌ها",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.text
                        )
                        Text(
                            "نسخهٔ داده تقویم: ${toPersianDigits(CalendarSync.syncVersion)}  •  آخرین بروزرسانی: ${CalendarSync.updatedAt}",
                            fontSize = 10.5.sp,
                            fontFamily = IRANSansX,
                            color = sk.textMuted
                        )
                    }
                    Icon(SkIcons.Refresh, null, Modifier.size(15.dp), tint = sk.primary)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(16.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            openExternalUrl(AppUpdateConfig.GITHUB_RELEASES_URL)
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(sk.surfaceStrong),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Download, null, Modifier.size(16.dp), tint = sk.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("دانلود مستقیم از گیت‌هاب", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                        Text("مشاهدهٔ تمام نسخه‌های ویندوز، مک و لینوکس", fontSize = 10.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                    }
                    Icon(SkIcons.External, null, Modifier.size(15.dp), tint = sk.textFaint)
                }
            }
        }

        /* ============ 7. راهنما ============ */
        Section(SkIcons.Help, "راهنما", null) {
            Column {
                HELP_ITEMS.forEachIndexed { idx, h ->
                    var open by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth()) {
                        if (idx > 0) {
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(sk.borderSoft))
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableNoRipple { open = !open }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                h.title,
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                color = if (open) sk.primary else sk.text
                            )
                            Icon(SkIcons.ChevronDown, null, Modifier.size(15.dp), tint = sk.textFaint)
                        }
                        AnimatedVisibility(visible = open) {
                            Text(
                                h.body,
                                Modifier.padding(bottom = 12.dp),
                                fontSize = 12.sp,
                                lineHeight = 24.sp,
                                fontFamily = IRANSansX,
                                color = sk.textMuted
                            )
                        }
                    }
                }
            }
        }

        /* ============ 8. درباره ما ============ */
        Section(SkIcons.Info, "درباره ما", null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(sk.surfaceStrong)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(sk.primary, sk.glowB, sk.primaryStrong)),
                            shape = RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val dayKc = kindColors(ShiftKind.DAY)
                    val eveKc = kindColors(ShiftKind.EVENING)
                    val nightKc = kindColors(ShiftKind.NIGHT)
                    val restKc = kindColors(ShiftKind.REST)
                    androidx.compose.foundation.Canvas(Modifier.size(46.dp)) {
                        val strokeW = 3.2.dp.toPx()
                        listOf(dayKc, eveKc, nightKc, restKc).forEachIndexed { idx, kc ->
                            drawArc(
                                brush = Brush.linearGradient(listOf(kc.ink, kc.bg, kc.ink)),
                                startAngle = idx * 90f + 12f,
                                sweepAngle = 66f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeW,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(sk.primary, sk.primaryStrong))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Calendar, null, Modifier.size(15.dp), tint = Color.White)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(APP.NAME, fontSize = 14.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
                    Text(
                        "${APP.NAME_EN} Desktop — نسخهٔ ${toPersianDigits(APP.VERSION)}",
                        fontSize = 11.5.sp,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                    Text(APP.TAGLINE, fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint)
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "این نرم‌افزار برای سهولت در برنامه‌ریزی وقت کارکنان شیفت شاغل در شرکت پتروشیمی بندر امام خمینی (ره) و سایر صنایع نوبت‌کاری طراحی شده است.",
                    fontSize = 12.sp,
                    lineHeight = 24.sp,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
                Text(
                    "با تشکر و سپاس از تمامی همکاران و دوستانی که با پیشنهادات مفید خود ما را در پیشرفت برنامه یاری نموده‌اند.",
                    fontSize = 12.sp,
                    lineHeight = 24.sp,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(sk.borderSoft))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تهیه شده توسط", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint)
                Text("سید حسین موسوی سعیدی — واحد SBR", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
            }
        }

        /* ============ 9. راه‌های ارتباطی (2-Column Colored Brand Cards matching Web v6.7.3) ============ */
        Section(SkIcons.Eitaa, SkStrings.contacts_title, SkStrings.contacts_subtitle) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BRAND_CONTACTS.filter { !it.fullSpan }.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { c ->
                            BrandContactCard(
                                contact = c,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.tick()
                                    openExternalUrl(c.href)
                                }
                            )
                        }
                    }
                }
                BRAND_CONTACTS.filter { it.fullSpan }.forEach { c ->
                    BrandContactCard(
                        contact = c,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            haptic.tick()
                            openExternalUrl(c.href)
                            ToastCenter.show("شماره تماس ۰۹۱۶۳۵۴۸۵۷۸ در کلیپ‌بورد کپی شد")
                        }
                    )
                }
            }
        }

        /* ============ 10. راهنمای شروع ============ */
        Section(SkIcons.Refresh, "راهنمای شروع", "تور راهنمای برنامه را دوباره ببینید یا تنظیمات اولیهٔ نرم‌افزار را از نو شروع کنید.") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(16.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            onStartTour()
                            ToastCenter.show("تور راهنما در صفحهٔ تقویم اجرا می‌شود")
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(SkIcons.Info, null, Modifier.size(15.dp), tint = sk.text)
                        Text("تور راهنمای برنامه", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, sk.holiday.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickableNoRipple {
                            haptic.tick()
                            ToastCenter.show("راهنما دوباره نمایش داده می‌شود؛ تنظیمات شما حفظ می‌شود")
                            onRestartOnboarding()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "راه‌اندازی مجدد تنظیمات اولیهٔ نرم‌افزار",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.holiday
                    )
                }
            }
        }

        Spacer(Modifier.height(108.dp))
    }
}

@Composable
private fun BrandContactCard(
    contact: BrandContact,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sk = LocalSk.current
    val accent = Color(contact.accentHex)
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.06f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(contact.icon, contact.title, Modifier.size(19.dp), tint = accent)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                contact.title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IRANSansX,
                color = sk.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                contact.sub,
                fontSize = 10.5.sp,
                fontFamily = IRANSansX,
                color = sk.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(SkIcons.External, null, Modifier.size(14.dp), tint = accent)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val sk = LocalSk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sk.surface)
            .border(1.dp, if (checked) sk.primary.copy(alpha = 0.45f) else sk.borderSoft, RoundedCornerShape(16.dp))
            .clickableNoRipple(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
            Text(subtitle, fontSize = 10.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(100))
                .background(if (checked) sk.primary else sk.surfaceStrong)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                if (checked) "فعال" else "غیرفعال",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = if (checked) Color.White else sk.textMuted
            )
        }
    }
}

@Composable
private fun Section(
    icon: ImageVector,
    title: String,
    desc: String?,
    content: @Composable () -> Unit
) {
    val sk = LocalSk.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .glass(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = sk.primary)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IRANSansX, color = sk.primary)
        }
        if (desc != null) {
            Text(
                desc,
                Modifier.padding(top = 4.dp),
                fontSize = 11.5.sp,
                lineHeight = 20.sp,
                fontFamily = IRANSansX,
                color = sk.textMuted
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ViewPreview(vt: ViewType) {
    val sk = LocalSk.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(sk.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (vt == ViewType.GRID) {
            val rows = listOf(
                listOf(ShiftKind.REST, ShiftKind.REST, ShiftKind.DAY, ShiftKind.DAY, ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST),
                listOf(ShiftKind.REST, ShiftKind.DAY, ShiftKind.DAY, ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST, ShiftKind.REST),
                listOf(ShiftKind.DAY, ShiftKind.DAY, ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST, ShiftKind.REST, null),
                listOf(ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST, ShiftKind.REST, ShiftKind.REST, ShiftKind.DAY, ShiftKind.DAY)
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { kind ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when (kind) {
                                        ShiftKind.DAY -> sk.day.copy(alpha = 0.7f)
                                        ShiftKind.EVENING -> sk.evening.copy(alpha = 0.7f)
                                        ShiftKind.NIGHT -> sk.night.copy(alpha = 0.7f)
                                        ShiftKind.REST -> sk.rest.copy(alpha = 0.45f)
                                        null -> sk.holiday.copy(alpha = 0.7f)
                                    }
                                )
                        )
                    }
                }
            }
        } else {
            listOf(
                Triple(sk.rest.copy(alpha = 0.45f), sk.holiday.copy(alpha = 0.6f), true),
                Triple(sk.night.copy(alpha = 0.7f), null, false),
                Triple(sk.day.copy(alpha = 0.7f), sk.holiday.copy(alpha = 0.6f), true),
                Triple(sk.day.copy(alpha = 0.7f), null, false)
            ).forEach { (dot, bar, hasBar) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(sk.textFaint.copy(alpha = 0.4f)))
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(100)).background(sk.textFaint.copy(alpha = 0.35f)))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    if (hasBar && bar != null) {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(100)).background(bar))
                    } else {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(100)).background(sk.textFaint.copy(alpha = 0.25f)))
                    }
                }
            }
        }
    }
}
