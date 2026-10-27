package com.seyedhosseinmousavisaeedi.shiftkar.ui.settings

import com.seyedhosseinmousavisaeedi.shiftkar.platform.openExternalUrl
import com.seyedhosseinmousavisaeedi.shiftkar.ui.SkStrings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.engine.APP
import com.seyedhosseinmousavisaeedi.shiftkar.updater.AppUpdateConfig
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ToastCenter

/**
 * Settings — full parity with the original ShiftKar settings page with the
 * signature 2px outlined option cards: segmented group, outlined theme-mode
 * cards, swatch theme grid, visual view-picker previews, UPDATER (native
 * replacement of the PWA install card), help accordion, about, contacts,
 * restart onboarding + tour.
 */

/* v2.2.8: برچسب تماس‌ها از فایل تک‌فایلی لوکال فارسی (values/strings.xml) */
private data class Contact(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val href: String)

private val CONTACTS = listOf(
    Contact("whatsapp-pv", SkStrings.contact_whatsapp_pv, SkIcons.Whatsapp, "https://wa.me/989163548578"),
    Contact("phone", SkStrings.contact_phone, SkIcons.Phone, "tel:09163548578"),
    Contact("whatsapp-group", SkStrings.contact_whatsapp_group, SkIcons.Whatsapp, "https://chat.whatsapp.com/5HolMMoVnojCdh8GjhSZo2"),
    Contact("eitaa", SkStrings.contact_eitaa, SkIcons.Eitaa, "https://eitaa.com/joinchat/929104214C56d88e3e24"),
    Contact("telegram", SkStrings.contact_telegram, SkIcons.Telegram, "https://t.me/h_mosavi_s")
)

private data class HelpItem(val title: String, val body: String)

private val HELP_ITEMS = listOf(
    HelpItem(
        "محاسبه شیفت",
        "چرخهٔ شیفت دقیقاً ۸ روز است: دو روز کاری (M1، M2)، دو روز شب‌کاری (N1، N2) و سپس چهار روز استراحت (R1 تا R4). تاریخ پایه: ۱۴۰۵/۰۵/۰۴. افست گروه‌ها: A=7، B=1، C=5، D=3. شاخص هر روز با فرمول floorMod(تعداد روز از تاریخ پایه + افست گروه، ۸) به‌دست می‌آید؛ به این ترتیب تاریخ‌های قبل از تاریخ پایه هم درست محاسبه می‌شوند."
    ),
    HelpItem(
        "گروه‌های شیفت",
        "چهار گروه A تا D وجود دارد. گروه شخصی خود را از بخش «شیفت شما» انتخاب کنید؛ تقویم به‌طور پیش‌فرض بر اساس آن نمایش داده می‌شود. با فیلتر «همه» روی تقویم، وضعیت هر چهار گروه در یک نگاه دیده می‌شود."
    ),
    HelpItem(
        "انواع شیفت",
        "سه وضعیت نمایشی وجود دارد:\n• صبح‌کار — شیفت روزانه\n• شب‌کار — شیفت شبانه\n• استراحت — روزهای استراحت\nهر وضعیت با رنگ و نشان اختصاصی در تقویم نمایش داده می‌شود. در نمای چهارگروهی، نام وضعیت با شمارهٔ آن در چرخه می‌آید؛ مثل «صبح‌کار ۱» یا «شب‌کار ۲»."
    ),
    HelpItem(
        "تقویم",
        "تقویم بر اساس تاریخ هجری شمسی است و هفته از شنبه آغاز می‌شود. با دکمه‌های کنار عنوان ماه، کشیدن انگشت بین ماه‌ها جابه‌جا شوید؛ روی عنوان ماه بزنید تا ماه و سال را انتخاب کنید. دو حالت نمایش تقویم (شبکه‌ای) و جدولی با یک دکمهٔ ثابت کنار عنوان ماه عوض می‌شود؛ نمای جدولی دکمه‌ای برای تمام‌صفحه شدن دارد."
    ),
    HelpItem(
        "یادداشت‌ها",
        "برای هر روز یک یادداشت ذخیره می‌شود. روزهای دارای یادداشت با یک نقطهٔ کوچک در تقویم مشخص می‌شوند. با آیکون یادداشت در بالای تقویم، همهٔ یادداشت‌ها را مرور، ویرایش یا حذف کنید."
    ),
    HelpItem(
        "تم‌ها",
        "شش تم رنگی در دسترس است: آبی، زمردی، بنفش، نارنجی، رز و فیروزه‌ای. تم انتخابی ذخیره می‌شود و بلافاصله در کل برنامه اعمال می‌گردد."
    ),
    HelpItem(
        "تعطیلات",
        "تعطیلات و مناسبت‌های رسمی ایران برای هر سالی محاسبه می‌شود: تعطیلات ثابت شمسی (نوروز، روز جمهوری اسلامی، رحلت امام خمینی، قیام ۱۵ خرداد، پیروزی انقلاب اسلامی و ملی شدن صنعت نفت) و تعطیلات قمری (عید فطر، قربان، غدیر، تاسوعا و عاشورا، اربعین و …) بر پایهٔ تقویم رسمی ایران، به‌همراه صدها مناسبت دیگر. روزهای تعطیل با رنگ قرمز مشخص می‌شوند و نام مناسبت‌ها در جزئیات هر روز نمایش داده می‌شود."
    ),
    HelpItem(
        "به‌روزرسانی",
        "بررسی نسخه‌های جدید در هر ورود به برنامه به‌صورت خودکار انجام می‌شود و پنجرهٔ بروزرسانی باز می‌شود؛ دکمهٔ «بررسی به‌روزرسانی» همیشه هم در دسترس است. وقتی نسخهٔ جدیدی پیدا شود، بنر اعلان در همین بخش می‌ماند تا نصب کنید. پس از اتمام دانلود، فایل نصاب متناسب با سیستم‌عامل شما (ویندوز، لینوکس یا مک) به‌صورت خودکار اجرا می‌شود. اگر بروزرسانی «اجباری» علامت بخورد، پنجرهٔ آن بسته نمی‌شود و برنامه تا نصب نسخهٔ جدید از کار می‌افتد."
    )
)

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenUpdater: () -> Unit,
    onStartTour: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    /* v2.2.5: وضعیت بروزرسانی — وقتی APK دانلودشده موجود است، دکمهٔ نصب همیشه
     * اینجا دیده می‌شود (حتی اگر نوتیفیکیشن «دانلود شد» قلم گرفته باشد). */
    val upState = Updater.state.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "تنظیمات",
            Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
        )
        Text(
            "شخصی‌سازی برنامه",
            Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
            fontSize = 12.sp, fontFamily = IRANSansX, color = sk.textMuted
        )
        Spacer(Modifier.height(16.dp))

        /* ============ 1. شیفت شما ============ */
        Section(SkIcons.Group, "شیفت شما", "گروه کاری خود را انتخاب کنید؛ تقویم و لوحه بر همین اساس رنگ می‌شود.") {
            SegmentedControl(
                options = GROUPS.map { it.faLetter() }, // v2.2.4: حروف فارسی
                selectedIndex = GROUPS.indexOf(s.myGroup.toShiftGroup())
            ) { i ->
                haptic.tick()
                val g = GroupFilter.entries[i + 1] // skip ALL
                vm.set { it.copy(myGroup = g, filterGroup = g) }
                ToastCenter.show("شیفت شما: ${GROUP_FA.getValue(g.toShiftGroup())}") // v2.2.4
            }
        }

        /* ============ 2. حالت نمایش ============ */
        Section(SkIcons.Palette, "حالت نمایش", "روشن، تیره یا پیروی از تم سیستم؛ تغییر به‌صورت زنده اعمال می‌شود.") {
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(icon, fa, Modifier.size(20.dp), tint = if (active) sk.primary else sk.textMuted)
                            Text(
                                fa, fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX, textAlign = TextAlign.Center,
                                color = if (active) sk.primary else sk.textMuted
                            )
                        }
                        /* v2.2.1: نشان ✓ حذف شد — انتخاب با حاشیهٔ 2px سبز + رنگ
                         * عنوان مشخص است؛ خود کارت گویاست و علامت اضافه زشت بود. */
                    }
                }
            }
        }

        /* ============ 3. رنگ اصلی ============ */
        Section(SkIcons.Palette, "رنگ اصلی", "رنگ اصلی برنامه را انتخاب کنید.") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeId.entries.chunked(3).forEach { rowThemes ->
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
                                        .background(androidx.compose.ui.graphics.Color(t.hex))
                                        .border(2.dp, sk.surfaceStrong, CircleShape)
                                )
                                Text(
                                    t.fa, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = IRANSansX,
                                    color = if (active) sk.primary else sk.textMuted
                                )
                            }
                        }
                        if (rowThemes.size == 1) Spacer(Modifier.weight(2f))
                    }
                }
            }
        }

        /* ============ 4. نمای تقویم ============ */
        Section(SkIcons.Grid, "نمای تقویم", "حالت نمایش تقویم (شبکه‌ای) یا جدولی را انتخاب کنید.") {
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
                        /* v2.2.1: نشان ✓ حذف شد (همان منطق بخش حالت نمایش) */
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            ViewPreview(vt)
                            Text(
                                fa,
                                Modifier.padding(top = 10.dp),
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                color = if (active) sk.primary else sk.textMuted
                            )
                        }
                    }
                }
            }
        }

        /* ============ 5. به‌روزرسانی برنامه ============
         * v2.2.1 بازسازی: «نسخهٔ نصب‌شده» و «دانلود مستقیم از گیت‌هاب» از پنجرهٔ
         * popup به اینجا منتقل شد تا پنجره سبک و متمرکز بماند (خواستهٔ مالک).
         * v2.2.9 (مالک: «if one time it successfully fetched the update! … It
         * should prominent that there is an update in بروزرسانی section and
         * encourage the user to update it!»): بنر برجستهٔ «بروزرسانی جدید»
         * بالای بخش — از بررسی خودکار یا دستی، بین اجراها هم می‌ماند تا نصب. */
        Section(SkIcons.Download, "به‌روزرسانی برنامه", "بررسی نسخه‌های جدید دسکتاپ از مخزن گیت‌هاب (shiftkar-desktop) و دانلود و نصب درون‌برنامه‌ای.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val lastKnown = remember(
                    upState.status, upState.info?.versionCode
                ) { Updater.lastKnownAvailable() }
                val bannerInfo =
                    if (upState.status == com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState.Status.AVAILABLE && upState.info != null) upState.info
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
                            Icon(SkIcons.Download, null, Modifier.size(19.dp), tint = sk.sheetSolid)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                "بروزرسانی جدید منتشر شد!",
                                fontSize = 13.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary
                            )
                            Text(
                                if (upState.status == com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState.Status.DOWNLOADED)
                                    "نسخهٔ ${toPersianDigits(bannerInfo.versionName)} دانلود شده — برای نصب ضربه بزنید"
                                else
                                    "نسخهٔ ${toPersianDigits(bannerInfo.versionName)} آمادهٔ دانلود و نصب است — همین حالا به‌روزرسانی کنید",
                                Modifier.padding(top = 2.dp),
                                fontSize = 11.sp, lineHeight = 17.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                        }
                        Icon(SkIcons.ChevronLeft, null, Modifier.size(16.dp), tint = sk.primary)
                    }
                }
                /* نسخهٔ نصب‌شده */
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
                        Icon(SkIcons.Spark, null, Modifier.size(20.dp), tint = sk.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("نسخهٔ نصب‌شده", fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textMuted)
                        Text(
                            "${APP.NAME} ${toPersianDigits(APP.VERSION)}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text
                        )
                    }
                }
                /* دکمهٔ بررسی */
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
                    Icon(SkIcons.Refresh, null, Modifier.size(16.dp), tint = sk.sheetSolid)
                    Text("بررسی به‌روزرسانی", Modifier.padding(start = 8.dp), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.sheetSolid)
                }
                /* v2.2.5 (مالک: «when I dismiss it won't show me the option to
                 * install it!»): مسیر نصب همیشگی برای APK دانلودشده — مستقل از
                 * نوتیفیکیشن و پنجرهٔ بروزرسانی. */
                if (upState.status == com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState.Status.DOWNLOADED) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(sk.primary.copy(alpha = 0.14f))
                            .border(1.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickableNoRipple {
                                haptic.tick(20)
                                vm.installUpdate()
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(SkIcons.Check, null, Modifier.size(16.dp), tint = sk.primary)
                        Text(
                            "نصب نسخهٔ دانلودشده",
                            Modifier.padding(start = 8.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.primary
                        )
                    }
                }
                /* دانلود مستقیم از گیت‌هاب */
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
                        Text("اگر دانلود درون‌برنامه‌ای مشکل داشت", fontSize = 10.5.sp, fontFamily = IRANSansX, color = sk.textMuted)
                    }
                    Icon(SkIcons.External, null, Modifier.size(15.dp), tint = sk.textFaint)
                }
            }
        }

        /* ============ 6. راهنما ============ */
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
                            Text(h.title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = if (open) sk.primary else sk.text)
                            Icon(
                                SkIcons.ChevronDown, null, Modifier.size(15.dp),
                                tint = sk.textFaint
                            )
                        }
                        AnimatedVisibility(visible = open) {
                            Text(
                                h.body,
                                Modifier.padding(bottom = 12.dp),
                                fontSize = 12.sp, lineHeight = 24.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                        }
                    }
                }
            }
        }

        /* ============ 7. درباره ما ============ */
        Section(SkIcons.Info, "درباره ما", null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(sk.primary, sk.primaryStrong))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Spark, null, Modifier.size(26.dp), tint = sk.sheetSolid)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(APP.NAME, fontSize = 14.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
                    Text(
                        "${APP.NAME_EN} — نسخهٔ ${toPersianDigits(APP.VERSION)}",
                        fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.textMuted
                    )
                    Text(APP.TAGLINE, fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint)
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "این نرم‌افزار برای سهولت در برنامه‌ریزی وقت کارکنان شیفت ۱۲ ساعته شاغل در شرکت پتروشیمی بندر امام خمینی (ره) می‌باشد.",
                    fontSize = 12.sp, lineHeight = 24.sp, fontFamily = IRANSansX, color = sk.textMuted
                )
                Text(
                    "با تشکر و سپاس از تمامی همکاران و دوستانی که با پیشنهادات مفید خود ما را در پیشرفت برنامه یاری نموده‌اند.",
                    fontSize = 12.sp, lineHeight = 24.sp, fontFamily = IRANSansX, color = sk.textMuted
                )
                Text(
                    "لطفاً انتقادات و پیشنهادات خود را از قسمت «راه‌های ارتباط با ما» در میان بگذارید.",
                    fontSize = 12.sp, lineHeight = 24.sp, fontFamily = IRANSansX, color = sk.textMuted
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

        /* ============ 8. راه‌های ارتباط با ما ============
         * v6.0.0 (مالک: «The شبکه های اجتماعی could be in 5 beautiful glassy
         * style row! Like the تور راهنمای برنامه row for example!»): هر راه
         * ارتباطی یک ردیف شیشه‌ای مستقل — دقیقاً با همان زبانِ ردیف «تور
         * راهنمای برنامه»؛ ظرف یکپارچه و جداکننده‌های v2.2.8 حذف شد. */
        Section(SkIcons.Eitaa, SkStrings.contacts_title, SkStrings.contacts_subtitle) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CONTACTS.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(16.dp))
                            .clickableNoRipple {
                                haptic.tick()
                                openExternalUrl(c.href)
                                if (c.href.startsWith("tel:", ignoreCase = true)) {
                                    ToastCenter.show("شماره تماس در کلیپ‌بورد کپی شد: ۰۹۱۶۳۵۴۸۵۷۸")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(sk.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(c.icon, c.label, Modifier.size(19.dp), tint = sk.primary)
                        }
                        Text(
                            c.label,
                            Modifier.weight(1f).padding(start = 12.dp),
                            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = IRANSansX, color = sk.text
                        )
                        Icon(SkIcons.External, null, Modifier.size(15.dp), tint = sk.textFaint)
                    }
                }
            }
        }

        /* ============ 9. راهنمای شروع ============ */
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
                    Text("راه‌اندازی مجدد تنظیمات اولیهٔ نرم‌افزار", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.holiday)
                }
            }
        }

        Spacer(Modifier.height(108.dp))
    }
}

@Composable
private fun Section(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String?,
    content: @Composable () -> Unit
) {
    val sk = LocalSk.current
    Column(
        Modifier
            .fillMaxWidth()
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
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                fontSize = 11.5.sp, lineHeight = 20.sp, fontFamily = IRANSansX, color = sk.textMuted
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** Mini visual previews of the view-picker (grid-preview.tsx art). */
@Composable
private fun ViewPreview(vt: ViewType) {
    val sk = LocalSk.current
    Column(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(sk.surfaceStrong)
            .border(1.dp, sk.borderSoft, RoundedCornerShape(9.dp))
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        // head bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(100))
                .background(sk.primary.copy(alpha = 0.75f))
        )
        if (vt == ViewType.GRID) {
            val rows = listOf(
                listOf(ShiftKind.REST, ShiftKind.REST, ShiftKind.DAY, ShiftKind.DAY, ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST),
                listOf(ShiftKind.REST, ShiftKind.DAY, ShiftKind.DAY, ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST, ShiftKind.REST),
                listOf(ShiftKind.DAY, ShiftKind.DAY, ShiftKind.NIGHT, ShiftKind.NIGHT, ShiftKind.REST, ShiftKind.REST, null),
                /* 4th row so the grid preview is the SAME height as the table
                 * preview (3 vs 4 rows made the تقویم card read smaller) */
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
            // table preview rows
            listOf(
                Triple(sk.rest.copy(alpha = 0.45f), sk.holiday.copy(alpha = 0.6f), true),
                Triple(sk.night.copy(alpha = 0.7f), null, false),
                Triple(sk.day.copy(alpha = 0.7f), sk.holiday.copy(alpha = 0.6f), true),
                Triple(sk.day.copy(alpha = 0.7f), null, false)
            ).forEach { (dot, bar, hasBar) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
