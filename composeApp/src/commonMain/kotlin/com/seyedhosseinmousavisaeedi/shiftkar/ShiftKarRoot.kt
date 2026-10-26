package com.seyedhosseinmousavisaeedi.shiftkar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.APP
import com.seyedhosseinmousavisaeedi.shiftkar.engine.HolidaysLoader
import com.seyedhosseinmousavisaeedi.shiftkar.engine.TabId
import com.seyedhosseinmousavisaeedi.shiftkar.platform.loadLogoImageBitmap
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.SkStrings
import com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar.CalendarScreen
import com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar.DaySheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar.MonthPickerSheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.AuroraBackground
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.FrostBarBackdrop
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ToastHost
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.tourTarget
import com.seyedhosseinmousavisaeedi.shiftkar.ui.notes.NotesSheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding.OnboardingScreen
import com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding.TOUR_STEPS
import com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding.TourOverlay
import com.seyedhosseinmousavisaeedi.shiftkar.ui.roster.RosterScreen
import com.seyedhosseinmousavisaeedi.shiftkar.ui.settings.SettingsScreen
import com.seyedhosseinmousavisaeedi.shiftkar.ui.systems.SystemsScreen
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.ShiftKarTheme
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.effectiveDark
import com.seyedhosseinmousavisaeedi.shiftkar.ui.updater.InstallPromptScreen
import com.seyedhosseinmousavisaeedi.shiftkar.ui.updater.UpdaterPopup
import com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import kotlinx.coroutines.delay

private const val RESUME_RECHECK_MS = 3L * 60 * 1000

private data class TabSpec(val id: TabId, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(TabId.CALENDAR, SkStrings.tab_calendar, SkIcons.Calendar),
    TabSpec(TabId.SYSTEMS, SkStrings.tab_systems, SkIcons.Systems),
    TabSpec(TabId.ROSTER, SkStrings.tab_roster, SkIcons.Roster),
    TabSpec(TabId.SETTINGS, SkStrings.tab_settings, SkIcons.Settings)
)

@Composable
fun ShiftKarMultiplatformApp(
    vm: AppViewModel = remember { AppViewModel() },
    openFromNotification: Boolean = false,
    autoInstallFromNotification: Boolean = false,
    onThemeChanged: (Boolean) -> Unit = {}
) {
    val s by vm.settings.collectAsState()
    val dark = effectiveDark(s.themeMode)

    LaunchedEffect(dark) {
        onThemeChanged(dark)
    }

    ShiftKarTheme(theme = s.theme, dark = dark) {
        AppRoot(
            vm = vm,
            openFromNotification = openFromNotification,
            autoInstallFromNotification = autoInstallFromNotification
        )
    }
}

@Composable
fun AppRoot(
    vm: AppViewModel,
    openFromNotification: Boolean = false,
    autoInstallFromNotification: Boolean = false
) {
    val s = vm.settings.collectAsState().value
    val sk = LocalSk.current
    val haptic = rememberHaptics(s.hapticOn)

    var showSplash by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(TabId.CALENDAR) }
    var dayKey by remember { mutableStateOf<String?>(null) }
    var monthPickerOpen by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var updaterOpen by remember { mutableStateOf(false) }
    var updaterAutoInstall by remember { mutableStateOf(false) }
    var tourStep by remember { mutableStateOf<Int?>(null) }
    var tableFsActive by remember { mutableStateOf(false) }
    var tourPending by remember { mutableStateOf(false) }
    var auroraSize by remember { mutableStateOf(IntSize.Zero) }
    var showSafetyInstall by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HolidaysLoader.kick()
        delay(750)
        while (!HolidaysLoader.ready) {
            delay(40)
        }
        showSplash = false
    }

    LaunchedEffect(s.onboardingDone, s.tourDone) {
        if (s.onboardingDone && !s.tourDone && !tourPending) {
            tourPending = true
            delay(700)
            tourStep = 0
        }
    }

    LaunchedEffect(s.onboardingDone, showSplash, openFromNotification) {
        if (showSplash) return@LaunchedEffect
        if (openFromNotification && s.onboardingDone) {
            updaterAutoInstall = autoInstallFromNotification
            updaterOpen = true
        }
    }

    var coldStartChecked by remember { mutableStateOf(false) }
    LaunchedEffect(s.onboardingDone, s.tourDone, showSplash, openFromNotification) {
        if (!Updater.autoCheckEnabled) return@LaunchedEffect
        if (showSplash || !s.onboardingDone || openFromNotification) return@LaunchedEffect
        if (updaterOpen) return@LaunchedEffect
        when (Updater.state.value.status) {
            UpdateState.Status.DOWNLOADING,
            UpdateState.Status.DOWNLOADED,
            UpdateState.Status.INSTALLING -> return@LaunchedEffect
            else -> Unit
        }
        if (!s.tourDone) return@LaunchedEffect
        val cold = !coldStartChecked
        if (!cold && !Updater.shouldAutoCheck(RESUME_RECHECK_MS)) {
            val cur = Updater.state.value
            if (cur.status == UpdateState.Status.AVAILABLE && cur.info != null) {
                updaterAutoInstall = false
                updaterOpen = true
            }
            return@LaunchedEffect
        }
        if (cold) delay(900)
        coldStartChecked = true
        Updater.markAutoChecked()
        val st = Updater.check()
        val info = st.info
        if (st.status == UpdateState.Status.AVAILABLE && info != null) {
            updaterAutoInstall = false
            updaterOpen = true
        }
    }

    var installPrompted by remember { mutableStateOf(false) }
    val upState by Updater.state.collectAsState()
    LaunchedEffect(upState.status) {
        when {
            upState.status == UpdateState.Status.DOWNLOADING ->
                installPrompted = false
            upState.status == UpdateState.Status.DOWNLOADED && !installPrompted -> {
                installPrompted = true
                if (Updater.hasDownloadedApk()) {
                    Updater.clearNotifications()
                    Updater.install()
                }
            }
            upState.status == UpdateState.Status.ERROR && Updater.hasDownloadedApk() -> {
                showSafetyInstall = true
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(sk.background)
            .onGloballyPositioned { auroraSize = it.size }
    ) {
        AuroraBackground(Modifier.fillMaxSize())

        if (showSplash) {
            SplashArt(Modifier.align(Alignment.Center))
        } else if (!s.onboardingDone) {
            OnboardingScreen(vm) { }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                ) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            TabId.CALENDAR -> CalendarScreen(
                                vm = vm,
                                onOpenDay = { dayKey = it },
                                onOpenNotes = { notesOpen = true },
                                onOpenMonthPicker = { monthPickerOpen = true },
                                onTableFullscreen = { tableFsActive = it }
                            )
                            TabId.SYSTEMS -> SystemsScreen()
                            TabId.ROSTER -> RosterScreen(vm)
                            TabId.SETTINGS -> SettingsScreen(
                                vm = vm,
                                onOpenUpdater = {
                                    updaterAutoInstall =
                                        Updater.state.value.status == UpdateState.Status.DOWNLOADED
                                    updaterOpen = true
                                },
                                onStartTour = { tourStep = 0 },
                                onRestartOnboarding = { vm.restartOnboarding() }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !tableFsActive,
                enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 3 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NavRow(tab, auroraSize) { t ->
                    haptic.tick()
                    tab = t
                }
            }

            MonthPickerSheet(vm, monthPickerOpen) { monthPickerOpen = false }
            NotesSheet(vm, notesOpen, { notesOpen = false }) { k -> dayKey = k }
            DaySheet(vm, dayKey) { dayKey = null }
            if (updaterOpen) {
                UpdaterPopup(
                    vm = vm,
                    autoInstallRequest = updaterAutoInstall,
                    openedFromSettings = true,
                    onDismiss = {
                        updaterAutoInstall = false
                        updaterOpen = false
                    }
                )
            }

            if (showSafetyInstall && upState.status == UpdateState.Status.DOWNLOADED) {
                InstallPromptScreen(
                    versionName = upState.info?.versionName,
                    forced = upState.info?.isForceUpdate == true,
                    onInstall = {
                        if (Updater.hasDownloadedApk()) {
                            Updater.clearNotifications()
                            showSafetyInstall = false
                            Updater.install()
                        }
                    },
                    onClose = { showSafetyInstall = false }
                )
            }

            TourOverlay(
                active = tourStep != null && tab == TabId.CALENDAR,
                stepIndex = tourStep ?: 0,
                onNext = {
                    haptic.tick(10)
                    tourStep = tourStep?.plus(1)?.takeIf { it < TOUR_STEPS.size }
                    if (tourStep == null) vm.setTourDone()
                },
                onFinish = {
                    tourStep = null
                    vm.setTourDone()
                }
            )
        }

        ToastHost()
    }
}

@Composable
private fun NavRow(current: TabId, auroraSize: IntSize, onSelect: (TabId) -> Unit) {
    val sk = LocalSk.current
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        FrostBarBackdrop(
            auroraSizePx = auroraSize,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .tourTarget("bottom-nav")
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TABS.forEach { t ->
                    val active = current == t.id
                    val label = t.label
                    Column(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .then(
                                if (active) Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(sk.primary.copy(alpha = 0.22f), sk.primary.copy(alpha = 0.08f))
                                        )
                                    )
                                    .border(1.dp, sk.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                else Modifier
                            )
                            .clickableNoRipple { onSelect(t.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            t.icon, label, Modifier.size(20.dp),
                            tint = if (active) sk.primary else sk.textFaint
                        )
                        Text(
                            label,
                            Modifier.padding(top = 4.dp),
                            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                            color = if (active) sk.primary else sk.textFaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashArt(modifier: Modifier = Modifier) {
    val sk = LocalSk.current
    val logo = remember { BitmapPainter(loadLogoImageBitmap()) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = logo,
            contentDescription = null,
            modifier = Modifier
                .size(92.dp)
                .shadow(10.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
        )
        Text(
            APP.NAME,
            Modifier.padding(top = 16.dp),
            fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX,
            color = sk.primary
        )
    }
}
