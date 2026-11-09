package com.seyedhosseinmousavisaeedi.shiftkar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.seyedhosseinmousavisaeedi.shiftkar.data.AppSettings
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.platform.copyTextToClipboard
import com.seyedhosseinmousavisaeedi.shiftkar.platform.createDynamicTrayAwtImage
import com.seyedhosseinmousavisaeedi.shiftkar.platform.loadLogoImageBitmap
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ToastCenter
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.rememberLiveToday
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.ShiftKarTheme
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.effectiveDark
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.SwingUtilities

/**
 * Desktop entry point (Linux .deb/.rpm/.AppImage, macOS .dmg, Windows .msi/.exe) — v6.7.3.
 *
 * Key Desktop & Windows Fixes in v6.7.3:
 *  1. Single Native OS Window Title Bar (`undecorated = false` without the duplicate inner
 *     `DesktopQuickStatusBar` that previously created two title bars on Windows).
 *  2. Custom Compose Frosted-Glass System Tray Menu (`TrayPopupWindow`) + Live Dynamic Tray Icon:
 *     Replaces AWT's legacy `java.awt.PopupMenu` (which rendered Persian UTF-8 text as `???` /
 *     garbled characters on Windows non-Unicode locales) with a crisp `IRANSansX` Compose popup
 *     window and rich quick actions.
 *  3. Desktop Floating Mini-Widget (`DesktopWidgetWindow`): Draggable, translucent frosted-glass
 *     desktop widget showing today's 3-calendar dates, your active shift, and all groups at a glance.
 */
fun main() = application {
    LaunchedEffect(Unit) {
        HolidaysLoader.kick()
        CalendarSync.syncInBackground()
        runCatching { Updater.cleanUpOldApkIfUpdated() }
    }

    val windowState = rememberWindowState(
        size = DpSize(1040.dp, 760.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    val iconPainter = remember { BitmapPainter(loadLogoImageBitmap()) }
    val vm = remember { AppViewModel() }
    val s by vm.settings.collectAsState()

    val today = rememberLiveToday()
    val todayKey = remember(today) { makeDateKey(today.jy, today.jm, today.jd) }
    val activePattern = remember(s.activePatternId, s.customPatterns) {
        ShiftEngine.resolvePattern(s.activePatternId, s.customPatterns)
    }
    val activeGroups = remember(s.activePatternId, s.customPatterns) {
        ShiftEngine.activeGroups
    }
    val myShiftToday = remember(todayKey, s.myGroup, s.activePatternId, s.customPatterns) {
        ShiftEngine.calculateShift(todayKey, s.myGroup.toShiftGroup())
    }
    val todayShiftLabel = remember(myShiftToday) {
        CODE_FA[myShiftToday.code] ?: myShiftToday.code.name
    }
    val todayGroupSummary = remember(myShiftToday, s.myGroup, activePattern) {
        if (!activePattern.hasGroups || activePattern.groupCount <= 1) {
            "شیفت امروز: $todayShiftLabel"
        } else {
            "شیفت ${s.myGroup.toShiftGroup().faLetter()}: $todayShiftLabel"
        }
    }

    var escapeTrigger by remember { mutableIntStateOf(0) }
    var openNotesTrigger by remember { mutableIntStateOf(0) }
    var requestedTabTrigger by remember { mutableStateOf<Pair<Int, TabId>?>(null) }
    var requestedDayKeyTrigger by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var tabTriggerSeq by remember { mutableIntStateOf(0) }

    // Custom System Tray state (using Compose popup window instead of garbled Win32 AWT PopupMenu)
    var trayMenuVisible by remember { mutableStateOf(false) }
    var trayMenuPos by remember { mutableStateOf(WindowPosition(100.dp, 100.dp)) }

    val trayKindArgb = remember(myShiftToday.type) {
        when (myShiftToday.type) {
            ShiftKind.DAY -> 0xFFF59E0B.toInt()
            ShiftKind.EVENING -> 0xFFF97316.toInt()
            ShiftKind.NIGHT -> 0xFF818CF8.toInt()
            ShiftKind.REST -> 0xFF10B981.toInt()
        }
    }
    val trayTooltip = remember(today, todayGroupSummary) {
        "${APP.NAME} — ${formatWeekday(today.jy, today.jm, today.jd)} ${formatJalali(today.jy, today.jm, today.jd)} | $todayGroupSummary"
    }

    // Install Live Dynamic System Tray Icon with crisp Compose right-click popup support
    DisposableEffect(Unit) {
        var installedTrayIcon: TrayIcon? = null
        if (runCatching { SystemTray.isSupported() }.getOrDefault(false)) {
            runCatching {
                val tray = SystemTray.getSystemTray()
                val traySize = tray.trayIconSize.width.coerceIn(16, 64)
                val initialImg = createDynamicTrayAwtImage(
                    dayText = toPersianDigits(today.jd),
                    badgeArgb = trayKindArgb,
                    size = traySize
                )
                val icon = TrayIcon(initialImg, trayTooltip).apply {
                    isImageAutoSize = true
                    addActionListener {
                        SwingUtilities.invokeLater {
                            windowState.isMinimized = false
                            trayMenuVisible = false
                        }
                    }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseReleased(e: MouseEvent) {
                            handleMouse(e)
                        }

                        override fun mousePressed(e: MouseEvent) {
                            if (e.isPopupTrigger) handleMouse(e)
                        }

                        private fun handleMouse(e: MouseEvent) {
                            if (e.isPopupTrigger || SwingUtilities.isRightMouseButton(e) || e.button == MouseEvent.BUTTON3) {
                                val menuWidth = 286
                                val menuHeight = 388
                                val bounds = runCatching {
                                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                                        .defaultScreenDevice.defaultConfiguration.bounds
                                }.getOrNull()
                                val rawX = e.xOnScreen
                                val rawY = e.yOnScreen
                                val clampedX = if (bounds != null) {
                                    (rawX - menuWidth / 2).coerceIn(bounds.x + 8, bounds.x + bounds.width - menuWidth - 8)
                                } else {
                                    (rawX - menuWidth / 2).coerceAtLeast(8)
                                }
                                val clampedY = if (bounds != null) {
                                    if (rawY > bounds.y + bounds.height / 2) {
                                        (rawY - menuHeight - 12).coerceAtLeast(bounds.y + 8)
                                    } else {
                                        (rawY + 12).coerceAtMost(bounds.y + bounds.height - menuHeight - 8)
                                    }
                                } else {
                                    (rawY - menuHeight - 12).coerceAtLeast(8)
                                }
                                SwingUtilities.invokeLater {
                                    trayMenuPos = WindowPosition(clampedX.dp, clampedY.dp)
                                    trayMenuVisible = true
                                }
                            } else if (SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 1) {
                                SwingUtilities.invokeLater {
                                    windowState.isMinimized = false
                                    trayMenuVisible = false
                                }
                            }
                        }
                    })
                }
                tray.add(icon)
                installedTrayIcon = icon
            }
        }
        onDispose {
            installedTrayIcon?.let { icon ->
                runCatching { SystemTray.getSystemTray().remove(icon) }
            }
        }
    }

    // Live-update tray icon image & tooltip whenever today's date or shift changes
    LaunchedEffect(today.jd, trayKindArgb, trayTooltip) {
        if (runCatching { SystemTray.isSupported() }.getOrDefault(false)) {
            runCatching {
                val tray = SystemTray.getSystemTray()
                val traySize = tray.trayIconSize.width.coerceIn(16, 64)
                val updatedImg = createDynamicTrayAwtImage(
                    dayText = toPersianDigits(today.jd),
                    badgeArgb = trayKindArgb,
                    size = traySize
                )
                tray.trayIcons.firstOrNull()?.let { icon ->
                    icon.image = updatedImg
                    icon.toolTip = trayTooltip
                }
            }
        }
    }

    val dark = effectiveDark(s.themeMode)

    // 1. Main Application Window (Single Native OS Title Bar — No Duplicate Inner Title Bar!)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "${APP.NAME} — ${formatWeekday(today.jy, today.jm, today.jd)} ${formatJalali(today.jy, today.jm, today.jd)} | $todayGroupSummary",
        icon = iconPainter,
        undecorated = false,
        resizable = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
                when {
                    event.key == Key.Escape -> {
                        escapeTrigger += 1
                        true
                    }
                    ctrlOrMeta && event.key == Key.F -> {
                        openNotesTrigger += 1
                        true
                    }
                    ctrlOrMeta && event.key == Key.T -> {
                        vm.setViewMonth(today.jy, today.jm)
                        ToastCenter.show("بازگشت به ماه جاری")
                        true
                    }
                    ctrlOrMeta && event.key == Key.W -> {
                        vm.setDesktopWidgetEnabled(!s.desktopWidgetEnabled)
                        ToastCenter.show(
                            if (!s.desktopWidgetEnabled) "ویجت شناور رومیزی فعال شد"
                            else "ویجت شناور رومیزی مخفی شد"
                        )
                        true
                    }
                    event.key == Key.F11 -> {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        ShiftKarTheme(theme = s.theme, dark = dark) {
            Box(Modifier.fillMaxSize().background(LocalSk.current.background)) {
                AppRoot(
                    vm = vm,
                    escapeTrigger = escapeTrigger,
                    openNotesTrigger = openNotesTrigger,
                    requestedTabTrigger = requestedTabTrigger,
                    requestedDayKeyTrigger = requestedDayKeyTrigger
                )
            }
        }
    }

    // 2. Crisp Compose Frosted-Glass System Tray Right-Click Menu Window (100% Clear Persian on Windows!)
    if (trayMenuVisible) {
        val trayWindowState = rememberWindowState(
            size = DpSize(286.dp, 388.dp),
            position = trayMenuPos
        )
        LaunchedEffect(trayMenuPos) {
            trayWindowState.position = trayMenuPos
        }

        Window(
            onCloseRequest = { trayMenuVisible = false },
            state = trayWindowState,
            title = "",
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
            focusable = true
        ) {
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) {}
                    override fun windowLostFocus(e: WindowEvent?) {
                        trayMenuVisible = false
                    }
                }
                window.addWindowFocusListener(listener)
                window.toFront()
                window.requestFocus()
                onDispose {
                    window.removeWindowFocusListener(listener)
                }
            }

            ShiftKarTheme(theme = s.theme, dark = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TrayPopupMenuContent(
                        today = today,
                        todayGroupSummary = todayGroupSummary,
                        myShiftToday = myShiftToday,
                        widgetEnabled = s.desktopWidgetEnabled,
                        onOpenMain = {
                            trayMenuVisible = false
                            windowState.isMinimized = false
                        },
                        onGoToday = {
                            trayMenuVisible = false
                            windowState.isMinimized = false
                            vm.setViewMonth(today.jy, today.jm)
                            tabTriggerSeq += 1
                            requestedTabTrigger = tabTriggerSeq to TabId.CALENDAR
                        },
                        onToggleWidget = {
                            trayMenuVisible = false
                            vm.setDesktopWidgetEnabled(!s.desktopWidgetEnabled)
                        },
                        onCopyTodayShift = {
                            trayMenuVisible = false
                            val summaryText = buildString {
                                append("📅 ")
                                append(formatWeekday(today.jy, today.jm, today.jd))
                                append(" ")
                                append(formatJalali(today.jy, today.jm, today.jd))
                                append("\n")
                                if (!activePattern.hasGroups || activePattern.groupCount <= 1) {
                                    append("• وضعیت شیفت: $todayShiftLabel")
                                } else {
                                    activeGroups.forEachIndexed { idx, g ->
                                        if (idx > 0) append(" | ")
                                        val info = ShiftEngine.calculateShift(todayKey, g)
                                        append("گروه ${g.faLetter()}: ${CODE_FA[info.code] ?: info.code.name}")
                                    }
                                }
                            }
                            copyTextToClipboard(summaryText)
                            ToastCenter.show("وضعیت شیفت امروز در کلیپ‌بورد کپی شد")
                        },
                        onOpenRoster = {
                            trayMenuVisible = false
                            windowState.isMinimized = false
                            tabTriggerSeq += 1
                            requestedTabTrigger = tabTriggerSeq to TabId.ROSTER
                        },
                        onOpenManager = {
                            trayMenuVisible = false
                            windowState.isMinimized = false
                            tabTriggerSeq += 1
                            requestedTabTrigger = tabTriggerSeq to TabId.MANAGER
                        },
                        onOpenNotes = {
                            trayMenuVisible = false
                            windowState.isMinimized = false
                            openNotesTrigger += 1
                        },
                        onExit = {
                            trayMenuVisible = false
                            exitApplication()
                        }
                    )
                }
            }
        }
    }

    // 3. Desktop Floating Mini-Widget Window (Draggable, Translucent Frosted-Glass Desktop Widget)
    if (s.desktopWidgetEnabled) {
        val widgetState = rememberWindowState(
            size = DpSize(320.dp, 224.dp),
            position = WindowPosition.Aligned(Alignment.TopEnd)
        )
        Window(
            onCloseRequest = { vm.setDesktopWidgetEnabled(false) },
            state = widgetState,
            title = "ویجت شیفت‌کار",
            icon = iconPainter,
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = s.desktopWidgetAlwaysOnTop,
            focusable = false
        ) {
            ShiftKarTheme(theme = s.theme, dark = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    WindowDraggableArea(Modifier.fillMaxSize()) {
                        DesktopMiniWidgetCard(
                            today = today,
                            todayKey = todayKey,
                            settings = s,
                            activePattern = activePattern,
                            activeGroups = activeGroups,
                            myShiftToday = myShiftToday,
                            todayShiftLabel = todayShiftLabel,
                            onTogglePin = { vm.setDesktopWidgetAlwaysOnTop(!s.desktopWidgetAlwaysOnTop) },
                            onOpenMainWindow = {
                                windowState.isMinimized = false
                                tabTriggerSeq += 1
                                requestedDayKeyTrigger = tabTriggerSeq to todayKey
                            },
                            onCloseWidget = { vm.setDesktopWidgetEnabled(false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayPopupMenuContent(
    today: JalaliDate,
    todayGroupSummary: String,
    myShiftToday: ShiftResult,
    widgetEnabled: Boolean,
    onOpenMain: () -> Unit,
    onGoToday: () -> Unit,
    onToggleWidget: () -> Unit,
    onCopyTodayShift: () -> Unit,
    onOpenRoster: () -> Unit,
    onOpenManager: () -> Unit,
    onOpenNotes: () -> Unit,
    onExit: () -> Unit
) {
    val sk = LocalSk.current
    val kindColor = when (myShiftToday.type) {
        ShiftKind.DAY -> sk.day
        ShiftKind.EVENING -> sk.evening
        ShiftKind.NIGHT -> sk.night
        ShiftKind.REST -> sk.rest
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(sk.sheetSolid.copy(alpha = 0.96f))
            .border(1.dp, sk.border, RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header status card
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(sk.surfaceStrong)
                .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${formatWeekday(today.jy, today.jm, today.jd)} ${formatJalali(today.jy, today.jm, today.jd)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = sk.text
                )
                Text(
                    todayGroupSummary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = kindColor
                )
            }
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(kindColor.copy(alpha = 0.18f))
                    .border(1.dp, kindColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    toPersianDigits(today.jd),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX,
                    color = kindColor
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        TrayMenuItem(
            icon = SkIcons.Expand,
            label = "نمایش پنجرهٔ اصلی شیفت‌کار",
            tint = sk.primary,
            onClick = onOpenMain
        )
        TrayMenuItem(
            icon = SkIcons.Calendar,
            label = "برو به امروز و ماه جاری",
            tint = sk.text,
            onClick = onGoToday
        )
        TrayMenuItem(
            icon = SkIcons.Spark,
            label = if (widgetEnabled) "مخفی کردن ویجت شناور رومیزی" else "نمایش ویجت شناور رومیزی",
            tint = sk.primary,
            onClick = onToggleWidget
        )
        TrayMenuItem(
            icon = SkIcons.Clipboard,
            label = "کپی وضعیت شیفت امروز",
            tint = sk.text,
            onClick = onCopyTodayShift
        )
        TrayMenuItem(
            icon = SkIcons.Roster,
            label = "مشاهدهٔ لوحهٔ سالانه",
            tint = sk.text,
            onClick = onOpenRoster
        )
        TrayMenuItem(
            icon = SkIcons.Group,
            label = "مدیریت شیفت و پرسنل",
            tint = sk.text,
            onClick = onOpenManager
        )
        TrayMenuItem(
            icon = SkIcons.Search,
            label = "جستجو در یادداشت‌ها (Ctrl+F)",
            tint = sk.text,
            onClick = onOpenNotes
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .height(1.dp)
                .background(sk.borderSoft)
        )

        TrayMenuItem(
            icon = SkIcons.Close,
            label = "خروج کامل از برنامه",
            tint = sk.holiday,
            onClick = onExit
        )
    }
}

@Composable
private fun TrayMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val sk = LocalSk.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint)
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IRANSansX,
            color = if (tint == sk.holiday) sk.holiday else sk.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DesktopMiniWidgetCard(
    today: JalaliDate,
    todayKey: String,
    settings: AppSettings,
    activePattern: ShiftPatternSpec,
    activeGroups: List<ShiftGroup>,
    myShiftToday: ShiftResult,
    todayShiftLabel: String,
    onTogglePin: () -> Unit,
    onOpenMainWindow: () -> Unit,
    onCloseWidget: () -> Unit
) {
    val sk = LocalSk.current
    val alpha = settings.widgetOpacity.coerceIn(0.45f, 1.0f)
    val isHolidayToday = remember(today) { Holidays.isHoliday(today.jy, today.jm, today.jd) }
    val greg = remember(today) { toGregorian(today.jy, today.jm, today.jd) }
    val hijri = remember(today) { toHijri(today.jy, today.jm, today.jd) }

    val myKindColor = when (myShiftToday.type) {
        ShiftKind.DAY -> sk.day
        ShiftKind.EVENING -> sk.evening
        ShiftKind.NIGHT -> sk.night
        ShiftKind.REST -> sk.rest
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
            .shadow(18.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        sk.sheetSolid.copy(alpha = alpha),
                        sk.background.copy(alpha = (alpha + 0.04f).coerceAtMost(1f))
                    )
                )
            )
            .border(1.dp, sk.border, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Title + Pin / Open / Close controls
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(sk.primary)
                    )
                    Text(
                        "ویجت زنده شیفت‌کار",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Pin Always-On-Top toggle
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (settings.desktopWidgetAlwaysOnTop) sk.primarySoft else sk.surface)
                            .border(
                                1.dp,
                                if (settings.desktopWidgetAlwaysOnTop) sk.primary.copy(alpha = 0.5f) else sk.borderSoft,
                                RoundedCornerShape(8.dp)
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTogglePin
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (settings.desktopWidgetAlwaysOnTop) "سنجاق‌شده" else "آزاد",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = if (settings.desktopWidgetAlwaysOnTop) sk.primary else sk.textMuted
                        )
                    }

                    // Open Main Window
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(sk.surface)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(7.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenMainWindow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Expand, contentDescription = "باز کردن برنامه", modifier = Modifier.size(12.dp), tint = sk.text)
                    }

                    // Close Widget
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(sk.holidaySoft)
                            .border(1.dp, sk.holiday.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCloseWidget
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Close, contentDescription = "بستن ویجت", modifier = Modifier.size(12.dp), tint = sk.holiday)
                    }
                }
            }

            // Middle: Date + My Shift Pill
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${formatWeekday(today.jy, today.jm, today.jd)} ${formatJalali(today.jy, today.jm, today.jd)}",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = if (isHolidayToday) sk.holiday else sk.text
                    )
                    Text(
                        "${formatGregorian(greg.gy, greg.gm, greg.gd)}  •  ${formatHijri(hijri.hy, hijri.hm, hijri.hd)}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                }

                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(myKindColor.copy(alpha = 0.16f))
                        .border(1.dp, myKindColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (!activePattern.hasGroups || activePattern.groupCount <= 1) todayShiftLabel
                        else "شیفت ${settings.myGroup.toShiftGroup().faLetter()}: $todayShiftLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = myKindColor
                    )
                }
            }

            // Bottom: All Shift Groups at a glance (up to 4 groups)
            if (activePattern.hasGroups && activePattern.groupCount > 1) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeGroups.take(4).forEach { g ->
                        val info = ShiftEngine.calculateShift(todayKey, g)
                        val gColor = when (info.type) {
                            ShiftKind.DAY -> sk.day
                            ShiftKind.EVENING -> sk.evening
                            ShiftKind.NIGHT -> sk.night
                            ShiftKind.REST -> sk.rest
                        }
                        val isMine = g == settings.myGroup.toShiftGroup()
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isMine) gColor.copy(alpha = 0.18f) else sk.surfaceStrong)
                                .border(
                                    1.dp,
                                    if (isMine) gColor.copy(alpha = 0.55f) else sk.borderSoft,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "گروه ${g.faLetter()}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = IRANSansX,
                                color = if (isMine) gColor else sk.textMuted,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                CODE_NUM_FA[info.code] ?: info.code.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IRANSansX,
                                color = gColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Single-person pattern summary strip
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.surfaceStrong)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "الگوی فعال: ${activePattern.name}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                    Text(
                        todayShiftLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = myKindColor
                    )
                }
            }
        }
    }
}
