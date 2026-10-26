package com.seyedhosseinmousavisaeedi.shiftkar.ui.updater


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.updater.AppUpdateConfig
import com.seyedhosseinmousavisaeedi.shiftkar.engine.APP
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SheetButton
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ToastCenter
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics

/**
 * پنجرهٔ بروزرسانی — یک popup مرکزی شیشه‌ای (نه modal تمام‌صفحه):
 *
 *  - مقایسهٔ نسخه‌ها: نسخهٔ فعلی خاکستری با خط‌خورده → فلش → نسخهٔ جدید برجسته
 *    (درخواست صریح صاحب برنامه؛ «نسخهٔ نصب‌شده» و «دانلود از گیت‌هاب» به
 *    بخش بروزرسانیِ تنظیمات منتقل شد تا پنجره سبک و تمیز بماند).
 *  - مجوزها قبل از دانلود، با توضیح: اعلان (API 33+) برای نوتیفیکیشن پیشرفت
 *    و «نصب از منابع ناشناس» برای نصب مستقیم — هر دو با اجازهٔ کاربر.
 *  - دانلود در پس‌زمینه ادامه پیدا می‌کند حتی اگر پنجره بسته شود
 *    (Updater.startBackgroundDownload در اسکوپ برنامه).
 *  - v2.2.5: پنجره فشرده شد (بنر کم‌ارتفاع + لیست تغییرات در پنجرهٔ اسکرول
 *    محدود) و اگر APK از قبل دانلود باشد مستقیم «نصب» نشان داده می‌شود.
 *  - v2.2.9: بروزرسانیِ «اجباری» (isForceUpdate) پنجره را غیرقابل‌بستن
 *    می‌کند — بدون دکمهٔ بستن، بدون بستن با بیرون‌زدن، بدون فرار با دکمهٔ
 *    بازگشت؛ برنامه تا نصب نسخهٔ جدید از کار می‌افتد (مالک: «make the
 *    forced update real! … And if he don't… The app won't work at all! And
 *    the update prompt won't close by user!»). و وقتی پنجره از تنظیمات
 *    («بررسی به‌روزرسانی») باز شده باشد و APK دانلودشده موجود باشد، پرامپت
 *    نصب بلافاصله و خودکار ساخته می‌شود.
 */

private enum class AskStep { NONE, NOTIFY, INSTALL }

@Composable
fun UpdaterPopup(
    vm: AppViewModel,
    autoInstallRequest: Boolean,
    openedFromSettings: Boolean = false,
    onDismiss: () -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val state by vm.updateState.collectAsState()

    var bounds by remember { mutableStateOf(Rect.Zero) }
    var askStep by remember { mutableStateOf(AskStep.NONE) }
    var autoInstallDone by remember { mutableStateOf(false) }

    /* v2.2.9: بروزرسانی اجباری — پنجره بسته نمی‌شود (نه دکمهٔ بستن، نه
     * بیرون‌زدن، نه دکمهٔ بازگشت)؛ برنامه تا نصب از کار می‌افتد. */
    val forced = state.info?.isForceUpdate == true


    /* v2.2.3: پنجره هرگز «خالی» باز نمی‌شود — اگر وضعیت IDLE است (باز شدن دستی
     * از تنظیمات)، بلافاصله بررسی شروع می‌شود تا یکی از این‌ها دیده شود:
     * «در حال بررسی…» → «شما آخرین نسخه را دارید» / «نسخهٔ جدید منتشر شده» /
     * «خطای شبکه» (گزارش مالک: «بروزرسانی برنامه هیچ چیزی نشان نمی‌دهد!»). */
    LaunchedEffect(Unit) {
        if (state.status == UpdateState.Status.IDLE) {
            vm.checkForUpdates()
        }
        /* v2.2.5 (مالک: «when I dismiss it won't show me the option to install
         * it!»): اگر APK همین نسخه از قبل دانلود شده، بلافاصله وضعیتِ «دانلود
         * شد» با دکمهٔ نصب نشان داده شود — نه دانلود دوباره. */
        Updater.promoteDownloadedIfNeeded()
    }
    LaunchedEffect(state.status) {
        if (state.status == UpdateState.Status.AVAILABLE) {
            Updater.promoteDownloadedIfNeeded()
        }
    }

    /* v2.2.3: انیمیشن باز شدن مدال — مقیاس + شفافیت (فقط ورود) */
    val intro = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(240, easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.8f, 0.36f, 1f)))
    }

    /* باز شدن از نوتیفیکیشن: به‌محض DOWNLOADED نصب را خودکار شروع کن */
    LaunchedEffect(autoInstallRequest, state.status) {
        if (autoInstallRequest && !autoInstallDone && state.status == UpdateState.Status.DOWNLOADED) {
            autoInstallDone = true
            Updater.clearNotifications()
            doInstall(vm)
        }
    }

    /* v2.2.9 (مالک: «It should open the install prompt automatically if the
     * user opened the بررسی بروزرسانی!»): پنجره‌ای که از تنظیمات باز شده،
     * به‌محض DOWNLOADED (همین حالا، یا بعد از promote/download داخل پنجره)
     * پرامپت نصب را خودکار می‌سازد. */
    LaunchedEffect(openedFromSettings, state.status) {
        if (openedFromSettings && !autoInstallDone && state.status == UpdateState.Status.DOWNLOADED) {
            autoInstallDone = true
            Updater.clearNotifications()
            doInstall(vm)
        }
    }

    fun requestDownloadFlow() {
        when {
            !Updater.hasNotificationPermission() -> askStep = AskStep.NOTIFY
            !Updater.hasInstallPermission() -> askStep = AskStep.INSTALL
            else -> startDownload(vm)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x9E081024))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    /* v2.2.9: در بروزرسانی اجباری، بیرونِ پنجره هم بسته نمی‌شود */
                    if (!forced && bounds != Rect.Zero && !bounds.contains(pos)) onDismiss()
                }
            }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .graphicsLayer {
                    alpha = intro.value
                    val sc = 0.94f + 0.06f * intro.value
                    scaleX = sc
                    scaleY = sc
                }
                .clip(RoundedCornerShape(26.dp))
                .background(sk.sheetSolid)
                .background(Brush.verticalGradient(listOf(sk.sheetBg, sk.sheetBg)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* ضربه‌های داخل پنجره جذب شوند — مثل SkSheet */ }
                .border(1.dp, sk.border, RoundedCornerShape(26.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            /* ---------- header ---------- */
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(sk.primarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SkIcons.Spark, null, Modifier.size(21.dp), tint = sk.primary)
                }
                Text(
                    "به‌روزرسانی برنامه",
                    Modifier.weight(1f).padding(start = 10.dp),
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = IRANSansX, color = sk.text
                )
                /* v2.2.9: در بروزرسانی اجباری دکمهٔ بستن وجود ندارد */
                if (!forced) {
                    SheetButton(icon = SkIcons.Close, desc = "بستن") { onDismiss() }
                }
            }

            Spacer(Modifier.height(10.dp))
            val info = state.info

            when (state.status) {
                UpdateState.Status.CHECKING, UpdateState.Status.IDLE -> StatusCard(
                    SkIcons.Refresh, "در حال بررسی نسخه‌های جدید…", sk.textMuted, sk.surface
                )

                UpdateState.Status.UP_TO_DATE -> StatusCard(
                    SkIcons.Check, "شما آخرین نسخه را دارید",
                    sk.restInk, sk.restSoft, iconBg = sk.rest
                )

                UpdateState.Status.AVAILABLE -> {
                    /* ---------- مقایسهٔ نسخه‌ها: قدیم خط‌خورده → جدید برجسته ---------- */
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(sk.primarySoft)
                            .border(1.dp, sk.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "نسخهٔ جدید ${toPersianDigits(info?.versionName ?: "")} منتشر شده است",
                            fontSize = 11.5.sp, fontFamily = IRANSansX, color = sk.primaryInk
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            /* RTL: قدیم سمت راست، فلش، جدید سمت چپ */
                            Text(
                                toPersianDigits(AppUpdateConfig.CURRENT_VERSION_NAME),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX, color = sk.textFaint,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Icon(
                                SkIcons.ChevronLeft, null,
                                Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(18.dp),
                                tint = sk.primary
                            )
                            Text(
                                toPersianDigits(info?.versionName ?: ""),
                                fontSize = 20.sp, fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX, color = sk.primary
                            )
                        }
                        if (info?.isForceUpdate == true) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "این به‌روزرسانی اجباری است — تا نصب نسخهٔ جدید، برنامه قابل استفاده نیست",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX, color = sk.holiday
                            )
                        }
                        if (!info?.releaseDate.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "تاریخ انتشار: ${info?.releaseDate}",
                                fontSize = 10.5.sp, fontFamily = IRANSansX, color = sk.textMuted
                            )
                        }
                    }

                    if (!info?.changelog.isNullOrEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        /* v2.2.5 (مالک: «Put the change log on limited fixed
                         * scrollable window!»): لیست تغییرات در پنجره‌ای با ارتفاع
                         * محدود اسکرول می‌شود تا پنجرهٔ بروزرسانی غول‌آسا نشود. */
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(sk.surface)
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Text(
                                "تغییرات این نسخه", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX, color = sk.text
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 164.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                info?.changelog?.forEach { item ->
                                    Text(
                                        "• $item",
                                        Modifier.padding(top = 6.dp),
                                        fontSize = 12.sp, lineHeight = 21.sp,
                                        fontFamily = IRANSansX, color = sk.textMuted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("دانلود و نصب نسخهٔ جدید", SkIcons.Download) {
                        haptic.tick()
                        requestDownloadFlow()
                    }
                }

                UpdateState.Status.DOWNLOADING -> {
                    ProgressBlock(state.progress)
                    Text(
                        "می‌توانید پنجره را ببندید؛ دانلود در پس‌زمینه ادامه دارد و پس از اتمام آمادهٔ نصب می‌شود.",
                        Modifier.padding(top = 8.dp),
                        fontSize = 10.5.sp, lineHeight = 17.sp,
                        fontFamily = IRANSansX, color = sk.textFaint
                    )
                }

                UpdateState.Status.DOWNLOADED -> PrimaryButton("نصب نسخهٔ جدید", SkIcons.Check) {
                    haptic.tick(20)
                    Updater.clearNotifications()
                    doInstall(vm)
                }

                UpdateState.Status.INSTALLING -> StatusCard(
                    SkIcons.Check, "در حال آماده‌سازی نصب…", sk.primaryInk, sk.primarySoft
                )

                UpdateState.Status.ERROR -> {
                    StatusCard(
                        SkIcons.Info, state.error ?: "خطا در بررسی بروزرسانی",
                        sk.holiday, sk.holidaySoft, iconBg = sk.holiday
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("تلاش دوباره", SkIcons.Refresh) {
                        haptic.tick()
                        vm.checkForUpdates()
                    }
                }

                // null-branch حذف شد — IDLE حالا مثل CHECKING رندر می‌شود
            }

            /* ---------- مجوز: توضیح قبل از درخواست (بدون سورپرایز) ---------- */
            AnimatedVisibility(visible = askStep != AskStep.NONE) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(sk.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (askStep == AskStep.NOTIFY) SkIcons.Info else SkIcons.Download,
                                null, Modifier.size(16.dp), tint = sk.primary
                            )
                        }
                        Text(
                            if (askStep == AskStep.NOTIFY) "اجازهٔ اعلان" else "اجازهٔ نصب",
                            Modifier.padding(start = 10.dp),
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX, color = sk.text
                        )
                    }
                    Text(
                        if (askStep == AskStep.NOTIFY)
                            "برای دیدن درصد پیشرفت دانلود و خبر دادن از اتمام آن، شیفت‌کار به اجازهٔ اعلان نیاز دارد. این اعلان‌ها فقط برای بروزرسانی هستند."
                        else
                            "برای نصب مستقیم نسخهٔ دانلودشده، اندروید اجازهٔ «نصب برنامه‌های ناشناس» را می‌خواهد. این اجازه فقط برای نصب فایل رسمی همین بروزرسانی استفاده می‌شود.",
                        Modifier.padding(top = 8.dp),
                        fontSize = 11.5.sp, lineHeight = 20.sp,
                        fontFamily = IRANSansX, color = sk.textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(sk.primary)
                                .clickableNoRipple {
                                    Updater.openInstallPermissionSettings()
                                    askStep = AskStep.NONE
                                    startDownload(vm)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (askStep == AskStep.NOTIFY) "اجازه می‌دهم" else "رفتن به تنظیمات",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX, color = sk.sheetSolid
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(sk.surfaceStrong)
                                .clickableNoRipple {
                                    haptic.tick()
                                    // کاربر فعلاً نه گفت — مستقیم دانلود؛ بدون اعلان، و
                                    // برای نصب بعداً مسیر دستی وجود دارد
                                    askStep = AskStep.NONE
                                    startDownload(vm)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "فعلاً نه",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX, color = sk.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- helpers ---------------- */

private fun startDownload(vm: AppViewModel) {
    Updater.clearNotifications()
    vm.downloadUpdateInBackground()
}

private fun doInstall(vm: AppViewModel) {
    if (Updater.hasDownloadedApk()) vm.installUpdate() else vm.downloadUpdateInBackground()
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    ink: Color,
    bg: Color,
    iconBg: Color? = null
) {
    val sk = LocalSk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(iconBg ?: sk.primarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (iconBg != null) sk.sheetSolid else sk.primary)
        }
        Text(
            text,
            Modifier.padding(start = 12.dp).weight(1f),
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = ink
        )
    }
}

@Composable
private fun ProgressBlock(progress: Int) {
    val sk = LocalSk.current
    val animated by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(220), label = "dl"
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(12.dp)
                .clip(RoundedCornerShape(100))
                .background(sk.surfaceStrong)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(sk.primary, sk.primaryStrong)))
            )
        }
        Text(
            "در حال دانلود… ${toPersianDigits(progress)}٪",
            Modifier.padding(top = 8.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text
        )
    }
}

@Composable
private fun PrimaryButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val sk = LocalSk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sk.primary)
            .clickableNoRipple { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = sk.sheetSolid)
        Text(
            label,
            Modifier.padding(start = 8.dp),
            fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.sheetSolid
        )
    }
}
