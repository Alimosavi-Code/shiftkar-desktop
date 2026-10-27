package com.seyedhosseinmousavisaeedi.shiftkar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.seyedhosseinmousavisaeedi.shiftkar.engine.APP
import com.seyedhosseinmousavisaeedi.shiftkar.engine.CODE_FA
import com.seyedhosseinmousavisaeedi.shiftkar.engine.HolidaysLoader
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftEngine
import com.seyedhosseinmousavisaeedi.shiftkar.engine.formatJalali
import com.seyedhosseinmousavisaeedi.shiftkar.engine.formatWeekday
import com.seyedhosseinmousavisaeedi.shiftkar.engine.makeDateKey
import com.seyedhosseinmousavisaeedi.shiftkar.engine.todayJalaali
import com.seyedhosseinmousavisaeedi.shiftkar.platform.loadLogoImageBitmap
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.rememberLiveToday
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ToastCenter
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.ShiftKarTheme
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.effectiveDark
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater

/**
 * Desktop entry point (Linux .deb/.rpm/.AppImage, macOS .dmg, Windows .msi/.exe).
 * Includes:
 *  - System Tray icon & quick menu (when supported by the OS)
 *  - Keyboard shortcuts: Esc (close sheet), Ctrl+F (search notes), Ctrl+T (go to Today), F11 (maximize)
 *  - Desktop Quick Status Bar with today's date, active shift badge, and window controls
 */
fun main() = application {
    LaunchedEffect(Unit) {
        HolidaysLoader.kick()
        runCatching { Updater.cleanUpOldApkIfUpdated() }
    }

    val windowState = rememberWindowState(
        size = DpSize(1020.dp, 760.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    val iconPainter = remember { BitmapPainter(loadLogoImageBitmap()) }
    val vm = remember { AppViewModel() }
    val s by vm.settings.collectAsState()

    val today = rememberLiveToday()
    val todayKey = remember(today) { makeDateKey(today.jy, today.jm, today.jd) }
    val myShiftToday = remember(todayKey, s.myGroup) {
        ShiftEngine.calculateShift(todayKey, s.myGroup.toShiftGroup())
    }
    val todayShiftLabel = remember(myShiftToday) { CODE_FA.getValue(myShiftToday.code) }

    var escapeTrigger by remember { mutableIntStateOf(0) }
    var openNotesTrigger by remember { mutableIntStateOf(0) }

    if (isTraySupported) {
        Tray(
            icon = iconPainter,
            tooltip = "${APP.NAME} — شیفت امروز (${s.myGroup.toShiftGroup().name}): $todayShiftLabel",
            onAction = { windowState.isMinimized = false },
            menu = {
                Item("نمایش پنجرهٔ شیفت‌کار") {
                    windowState.isMinimized = false
                }
                Item("برو به ماه جاری (امروز)") {
                    windowState.isMinimized = false
                    vm.setViewMonth(today.jy, today.jm)
                }
                Item("جستجو در یادداشت‌ها") {
                    windowState.isMinimized = false
                    openNotesTrigger += 1
                }
                Separator()
                Item("خروج از برنامه", onClick = ::exitApplication)
            }
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "${APP.NAME} — ${APP.TAGLINE}",
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
        val windowScope: WindowScope = this
        val dark = effectiveDark(s.themeMode)

        ShiftKarTheme(theme = s.theme, dark = dark) {
            Column(Modifier.fillMaxSize().background(LocalSk.current.background)) {
                DesktopQuickStatusBar(
                    windowScope = windowScope,
                    windowState = windowState,
                    iconPainter = iconPainter,
                    todayText = "${formatWeekday(today.jy, today.jm, today.jd)} ${formatJalali(today.jy, today.jm, today.jd)}",
                    groupText = "گروه ${s.myGroup.toShiftGroup().faLetter()}: $todayShiftLabel",
                    onOpenNotes = { openNotesTrigger += 1 },
                    onGoToday = { vm.setViewMonth(today.jy, today.jm) },
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                    },
                    onClose = ::exitApplication
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AppRoot(
                        vm = vm,
                        escapeTrigger = escapeTrigger,
                        openNotesTrigger = openNotesTrigger
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopQuickStatusBar(
    windowScope: WindowScope,
    windowState: WindowState,
    iconPainter: BitmapPainter,
    todayText: String,
    groupText: String,
    onOpenNotes: () -> Unit,
    onGoToday: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    val sk = LocalSk.current
    val isMaximized = windowState.placement == WindowPlacement.Maximized

    with(windowScope) {
        WindowDraggableArea(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(sk.sheetSolid.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Right side (in RTL): Today's date + Active shift chip + Quick Notes button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(sk.surface)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(8.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onGoToday
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(SkIcons.Calendar, null, Modifier.size(12.dp), tint = sk.primary)
                        Text(
                            "امروز: $todayText",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.text
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(sk.primarySoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            groupText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.primary
                        )
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(sk.surface)
                            .border(1.dp, sk.borderSoft, RoundedCornerShape(8.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenNotes
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(SkIcons.Search, null, Modifier.size(11.dp), tint = sk.textMuted)
                        Text(
                            "یادداشت‌ها (Ctrl+F)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.textMuted
                        )
                    }
                }

                // Left side (in RTL): Minimize, Maximize/Restore, Close buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Minimize button (—)
                    WindowControlBtn(
                        bg = sk.surfaceStrong,
                        border = sk.borderSoft,
                        onClick = onMinimize
                    ) {
                        Box(
                            Modifier
                                .width(10.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(sk.text)
                        )
                    }

                    // Maximize / Restore button (□)
                    WindowControlBtn(
                        bg = sk.surfaceStrong,
                        border = sk.borderSoft,
                        onClick = onToggleMaximize
                    ) {
                        Icon(
                            if (isMaximized) SkIcons.Collapse else SkIcons.Expand,
                            contentDescription = if (isMaximized) "بازگردانی" else "تمام‌صفحه",
                            modifier = Modifier.size(13.dp),
                            tint = sk.text
                        )
                    }

                    // Close button (✕)
                    WindowControlBtn(
                        bg = sk.holidaySoft,
                        border = sk.holiday.copy(alpha = 0.35f),
                        onClick = onClose
                    ) {
                        Icon(
                            SkIcons.Close,
                            contentDescription = "بستن برنامه",
                            modifier = Modifier.size(14.dp),
                            tint = sk.holiday
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowControlBtn(
    bg: Color,
    border: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
