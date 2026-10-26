package com.seyedhosseinmousavisaeedi.shiftkar.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

actual val currentPlatform: PlatformTarget = PlatformTarget.DESKTOP

actual val supportsBackdropBlur: Boolean = true

private fun execCommandOutput(vararg cmd: String): String? = runCatching {
    val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val finished = proc.waitFor(1500, TimeUnit.MILLISECONDS)
    if (!finished) {
        proc.destroyForcibly()
        return@runCatching null
    }
    proc.inputStream.bufferedReader().readText().trim()
}.getOrNull()

/**
 * Reliable OS dark-mode detector for Linux (Fedora GNOME / KDE Plasma / XDG Desktop Portal),
 * Windows (Registry AppsUseLightTheme), and macOS (AppleInterfaceStyle).
 */
actual fun isSystemDarkMode(): Boolean {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        os.contains("linux") -> {
            // 1. Check GNOME / GTK4 color-scheme (Fedora Workstation default)
            val gnomeScheme = execCommandOutput("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")?.lowercase()
            if (gnomeScheme != null && gnomeScheme.contains("dark")) return true
            if (gnomeScheme != null && gnomeScheme.contains("light")) return false

            // 2. Check GNOME gtk-theme name
            val gtkTheme = execCommandOutput("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")?.lowercase()
            if (gtkTheme != null && gtkTheme.contains("dark")) return true

            // 3. Check XDG Desktop Portal (works on Fedora KDE, GNOME, Sway, Hyprland)
            // Returns uint32 1 for prefer-dark, 2 for prefer-light
            val portal = execCommandOutput(
                "dbus-send", "--session", "--print-reply=literal", "--reply-timeout=800",
                "--dest=org.freedesktop.portal.Desktop",
                "/org/freedesktop/portal/desktop",
                "org.freedesktop.portal.Settings.Read",
                "string:org.freedesktop.appearance", "string:color-scheme"
            )?.lowercase()
            if (portal != null) {
                if (portal.contains("uint32 1")) return true
                if (portal.contains("uint32 2")) return false
            }

            // 4. Check KDE globals file (~/.config/kdeglobals)
            val kdeGlobals = File(System.getProperty("user.home", "."), ".config/kdeglobals")
            if (kdeGlobals.exists()) {
                val text = runCatching { kdeGlobals.readText().lowercase() }.getOrDefault("")
                if (text.contains("breezedark") || text.contains("dark")) return true
            }
            false
        }
        os.contains("win") -> {
            val out = execCommandOutput(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            )?.lowercase()
            // 0x0 means Dark Mode is active; 0x1 means Light Mode
            out != null && out.contains("0x0")
        }
        os.contains("mac") -> {
            val out = execCommandOutput("defaults", "read", "-g", "AppleInterfaceStyle")?.lowercase()
            out != null && out.contains("dark")
        }
        else -> false
    }
}

private fun desktopStorageDir(): File {
    val home = System.getProperty("user.home") ?: "."
    return File(home, ".shiftkar-neo").apply { mkdirs() }
}

actual fun readAppStorageText(name: String): String? {
    val file = File(desktopStorageDir(), name)
    return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
}

actual fun writeAppStorageText(name: String, content: String) {
    val file = File(desktopStorageDir(), name)
    runCatching { file.writeText(content) }
}

private fun readResourceBytes(path: String): ByteArray {
    val clean = path.removePrefix("/")
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(clean)
        ?: PlatformTarget::class.java.classLoader?.getResourceAsStream(clean)
        ?: error("Resource not found: $clean")
    return stream.use { it.readBytes() }
}

actual fun readBundledEventsJson(): String =
    readResourceBytes("files/events.json").decodeToString()

private val cachedFontFamily: FontFamily by lazy {
    FontFamily(
        Font("font/iransansx_regular.ttf", FontWeight.Normal),
        Font("font/iransansx_medium.ttf", FontWeight.Medium),
        Font("font/iransansx_demibold.ttf", FontWeight.SemiBold),
        Font("font/iransansx_bold.ttf", FontWeight.Bold),
        Font("font/iransansx_extrabold.ttf", FontWeight.ExtraBold),
        Font("font/iransansx_black.ttf", FontWeight.Black)
    )
}

actual fun loadBundledFontFamily(): FontFamily = cachedFontFamily

private val cachedNoiseBitmap: ImageBitmap? by lazy {
    runCatching {
        Image.makeFromEncoded(readResourceBytes("drawable/noise.png")).toComposeImageBitmap()
    }.getOrNull()
}

actual fun loadNoiseImageBitmap(): ImageBitmap? = cachedNoiseBitmap

private val cachedLogoBitmap: ImageBitmap by lazy {
    Image.makeFromEncoded(readResourceBytes("drawable/sk_logo.png")).toComposeImageBitmap()
}

actual fun loadLogoImageBitmap(): ImageBitmap = cachedLogoBitmap

actual fun openExternalUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            return
        }
    }
    runCatching {
        val os = System.getProperty("os.name").lowercase()
        val cmd = when {
            os.contains("mac") -> arrayOf("open", url)
            os.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
            else -> arrayOf("xdg-open", url)
        }
        Runtime.getRuntime().exec(cmd)
    }
}

actual fun triggerPlatformHaptic(ms: Int) {
    // No-op on Desktop
}

private val okHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

actual suspend fun httpGetText(url: String): String = withContext(Dispatchers.IO) {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", "ShiftKar-Desktop-App")
        .header("Cache-Control", "no-cache, no-store, must-revalidate")
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()
    okHttp.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
            if (resp.code == 404) {
                throw IllegalStateException("فایل بروزرسانی هنوز روی مخزن منتشر نشده (404) — بعداً دوباره بررسی کنید")
            }
            throw IllegalStateException("خطا در دریافت اطلاعات بروزرسانی (کد ${resp.code})")
        }
        resp.body?.string() ?: ""
    }
}
