package ir.shiftkar.neo

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ir.shiftkar.neo.engine.APP
import ir.shiftkar.neo.platform.loadLogoImageBitmap
import ir.shiftkar.neo.ui.AppViewModel
import ir.shiftkar.neo.ui.components.SkIcons
import ir.shiftkar.neo.ui.theme.IRANSansX
import ir.shiftkar.neo.ui.theme.LocalSk
import ir.shiftkar.neo.ui.theme.ShiftKarTheme
import ir.shiftkar.neo.ui.theme.effectiveDark
import ir.shiftkar.neo.updater.Updater

/**
 * Desktop entry point (Linux .deb/.rpm/.AppImage, macOS .dmg, Windows .msi/.exe).
 * Includes both native OS window frame AND an in-app themed header bar with
 * Minimize (—), Maximize/Restore (□), and Close (✕) buttons so window controls
 * are always visible and accessible on Windows, Linux, and macOS.
 */
fun main() = application {
    LaunchedEffect(Unit) {
        runCatching { Updater.cleanUpOldApkIfUpdated() }
    }

    val windowState = rememberWindowState(
        size = DpSize(960.dp, 760.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    val iconPainter = remember { BitmapPainter(loadLogoImageBitmap()) }
    val vm = remember { AppViewModel() }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "${APP.NAME} — ${APP.TAGLINE}",
        icon = iconPainter,
        undecorated = false,
        resizable = true
    ) {
        val s by vm.settings.collectAsState()
        val dark = effectiveDark(s.themeMode)

        ShiftKarTheme(theme = s.theme, dark = dark) {
            Column(Modifier.fillMaxSize().background(LocalSk.current.background)) {
                DesktopWindowHeaderBar(
                    windowState = windowState,
                    iconPainter = iconPainter,
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
                    AppRoot(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun WindowScope.DesktopWindowHeaderBar(
    windowState: WindowState,
    iconPainter: BitmapPainter,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    val sk = LocalSk.current
    val isMaximized = windowState.placement == WindowPlacement.Maximized

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
            // Right side (in RTL): App Logo + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                )
                Text(
                    "${APP.NAME} — ${APP.TAGLINE}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted
                )
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
