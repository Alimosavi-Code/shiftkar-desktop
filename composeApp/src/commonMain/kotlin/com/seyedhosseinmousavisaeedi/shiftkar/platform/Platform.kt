package com.seyedhosseinmousavisaeedi.shiftkar.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily

enum class PlatformTarget { ANDROID, IOS, DESKTOP }

expect val currentPlatform: PlatformTarget

expect val supportsBackdropBlur: Boolean

expect fun isSystemDarkMode(): Boolean

expect fun readAppStorageText(name: String): String?

expect fun recoverAppStorageBackup(name: String): String?

expect fun writeAppStorageText(name: String, content: String)

expect fun readBundledEventsJson(): String

expect fun loadBundledFontFamily(): FontFamily

expect fun loadNoiseImageBitmap(): ImageBitmap?

expect fun loadLogoImageBitmap(): ImageBitmap

expect fun openExternalUrl(url: String)

expect fun copyTextToClipboard(text: String)

expect fun saveTextFileWithDialog(defaultFileName: String, content: String): String?

expect fun openTextFileWithDialog(): String?

expect fun saveRosterPngWithDialog(year: Int, groupCode: String): String?

expect fun triggerPlatformHaptic(ms: Int)

expect suspend fun httpGetText(url: String): String

fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
