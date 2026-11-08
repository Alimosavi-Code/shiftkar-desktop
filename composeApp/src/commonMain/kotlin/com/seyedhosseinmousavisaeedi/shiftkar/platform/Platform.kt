package com.seyedhosseinmousavisaeedi.shiftkar.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily

enum class PlatformTarget { ANDROID, IOS, DESKTOP }

expect val currentPlatform: PlatformTarget

expect val supportsBackdropBlur: Boolean

expect fun isSystemDarkMode(): Boolean

/**
 * Probes the OS system accent color as a 24-bit RGB integer (`0xRRGGBB`), or null if unavailable.
 * Used by `ThemeId.DYNAMIC` («پویا (سیستم)») on Windows 10/11 (DWM AccentColor), macOS, KDE Plasma, and GNOME.
 */
expect fun getSystemAccentColorRgb(): Long?

expect fun readAppStorageText(name: String): String?

expect fun recoverAppStorageBackup(name: String): String?

expect fun writeAppStorageText(name: String, content: String)

expect fun readBundledEventsJson(): String

expect fun loadBundledFontFamily(): FontFamily

expect fun loadBundledFontBytes(fileName: String): ByteArray

expect fun decodePngToImageBitmap(pngBytes: ByteArray): ImageBitmap

expect fun loadNoiseImageBitmap(): ImageBitmap?

expect fun loadLogoImageBitmap(): ImageBitmap

expect fun openExternalUrl(url: String)

expect fun copyTextToClipboard(text: String)

expect fun saveTextFileWithDialog(defaultFileName: String, content: String): String?

expect fun openTextFileWithDialog(): String?

expect fun savePngBytesWithDialog(defaultFileName: String, pngBytes: ByteArray): String?

expect fun copyPngBytesToClipboard(pngBytes: ByteArray): Boolean

expect fun saveRosterPngWithDialog(year: Int, groupCode: String): String?

expect fun triggerPlatformHaptic(ms: Int)

expect suspend fun httpGetText(url: String): String

fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
