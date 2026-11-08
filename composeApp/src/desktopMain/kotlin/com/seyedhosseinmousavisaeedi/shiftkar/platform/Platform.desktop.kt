package com.seyedhosseinmousavisaeedi.shiftkar.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

actual val currentPlatform: PlatformTarget = PlatformTarget.DESKTOP

actual val supportsBackdropBlur: Boolean = true

private fun execCommandOutput(vararg cmd: String): String? = runCatching {
    val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val finished = proc.waitFor(1200, TimeUnit.MILLISECONDS)
    if (!finished) {
        proc.destroyForcibly()
        return@runCatching null
    }
    proc.inputStream.bufferedReader().readText().trim()
}.getOrNull()

@Volatile
private var cachedSystemDark: Boolean = false

@Volatile
private var lastDarkProbeAt: Long = 0L

@Volatile
private var cachedAccentRgb: Long? = null

@Volatile
private var lastAccentProbeAt: Long = 0L

/**
 * Probes OS dark mode on a background thread (never blocks startup UI thread).
 * Specifically parses `ColorScheme=` in `~/.config/kdeglobals` on KDE.
 */
private fun probeSystemDarkModeBlocking(): Boolean {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        os.contains("linux") -> {
            val gnomeScheme = execCommandOutput("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")?.lowercase()
            if (gnomeScheme != null && gnomeScheme.contains("dark")) return true
            if (gnomeScheme != null && gnomeScheme.contains("light")) return false

            val gtkTheme = execCommandOutput("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")?.lowercase()
            if (gtkTheme != null && gtkTheme.contains("dark")) return true

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

            val kdeGlobals = File(System.getProperty("user.home", "."), ".config/kdeglobals")
            if (kdeGlobals.exists()) {
                val lines = runCatching { kdeGlobals.readLines() }.getOrDefault(emptyList())
                val schemeLine = lines.firstOrNull { it.trim().startsWith("ColorScheme=", ignoreCase = true) }
                if (schemeLine != null && schemeLine.lowercase().contains("dark")) return true
            }
            false
        }
        os.contains("win") -> {
            val out = execCommandOutput(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            )?.lowercase()
            out != null && out.contains("0x0")
        }
        os.contains("mac") -> {
            val out = execCommandOutput("defaults", "read", "-g", "AppleInterfaceStyle")?.lowercase()
            out != null && out.contains("dark")
        }
        else -> false
    }
}

/**
 * Probes OS system accent color (`0xRRGGBB`) on Windows 10/11, macOS, KDE Plasma, and GNOME.
 */
private fun probeSystemAccentColorBlocking(): Long? {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        os.contains("win") -> {
            // Windows DWM AccentColor is stored as 0xAABBGGRR in HKCU\Software\Microsoft\Windows\DWM
            val dwmAccent = execCommandOutput(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\DWM",
                "/v", "AccentColor"
            )
            val hexMatch = dwmAccent?.let { Regex("0x([0-9a-fA-F]{6,8})").find(it) }?.groupValues?.getOrNull(1)
            if (hexMatch != null) {
                val raw = hexMatch.toLongOrNull(16)
                if (raw != null) {
                    val r = raw and 0xFFL
                    val g = (raw shr 8) and 0xFFL
                    val b = (raw shr 16) and 0xFFL
                    return (r shl 16) or (g shl 8) or b
                }
            }
            val dwmColorization = execCommandOutput(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\DWM",
                "/v", "ColorizationColor"
            )
            val colMatch = dwmColorization?.let { Regex("0x([0-9a-fA-F]{6,8})").find(it) }?.groupValues?.getOrNull(1)
            colMatch?.toLongOrNull(16)?.let { it and 0xFFFFFFL }
        }
        os.contains("linux") -> {
            // 1. Check KDE Plasma ~/.config/kdeglobals AccentColor=R,G,B
            val kdeGlobals = File(System.getProperty("user.home", "."), ".config/kdeglobals")
            if (kdeGlobals.exists()) {
                val lines = runCatching { kdeGlobals.readLines() }.getOrDefault(emptyList())
                val accentLine = lines.firstOrNull { it.trim().startsWith("AccentColor=", ignoreCase = true) }
                if (accentLine != null) {
                    val parts = accentLine.substringAfter("=").split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (parts.size >= 3) {
                        val r = parts[0].coerceIn(0, 255).toLong()
                        val g = parts[1].coerceIn(0, 255).toLong()
                        val b = parts[2].coerceIn(0, 255).toLong()
                        return (r shl 16) or (g shl 8) or b
                    }
                }
            }
            // 2. Check GNOME / Ubuntu accent-color
            val gnomeAccent = execCommandOutput("gsettings", "get", "org.gnome.desktop.interface", "accent-color")?.lowercase()
            when {
                gnomeAccent == null -> null
                gnomeAccent.contains("teal") -> 0x0EA5A0L
                gnomeAccent.contains("green") -> 0x0F9D72L
                gnomeAccent.contains("orange") -> 0xF46B16L
                gnomeAccent.contains("red") || gnomeAccent.contains("pink") -> 0xF43F5EL
                gnomeAccent.contains("purple") -> 0x8B5CF6L
                gnomeAccent.contains("yellow") -> 0xEAB308L
                gnomeAccent.contains("blue") -> 0x3D6BF5L
                else -> null
            }
        }
        os.contains("mac") -> {
            val macAccent = execCommandOutput("defaults", "read", "-g", "AppleAccentColor")?.trim()
            when (macAccent) {
                "0" -> 0xF43F5EL // Red
                "1" -> 0xF46B16L // Orange
                "2" -> 0xEAB308L // Yellow
                "3" -> 0x0F9D72L // Green
                "5" -> 0x8B5CF6L // Purple
                "6" -> 0xEC4899L // Pink
                else -> 0x3D6BF5L // Blue (4 or default)
            }
        }
        else -> null
    }
}

actual fun isSystemDarkMode(): Boolean {
    val now = System.currentTimeMillis()
    if (lastDarkProbeAt == 0L) {
        lastDarkProbeAt = now
        Thread({
            cachedSystemDark = runCatching { probeSystemDarkModeBlocking() }.getOrDefault(false)
        }, "shiftkar-theme-init").apply { isDaemon = true; start() }
        return cachedSystemDark
    }
    if (now - lastDarkProbeAt >= 8_000L) {
        lastDarkProbeAt = now
        Thread({
            cachedSystemDark = runCatching { probeSystemDarkModeBlocking() }.getOrDefault(cachedSystemDark)
        }, "shiftkar-theme-poll").apply { isDaemon = true; start() }
    }
    return cachedSystemDark
}

actual fun getSystemAccentColorRgb(): Long? {
    val now = System.currentTimeMillis()
    if (lastAccentProbeAt == 0L) {
        lastAccentProbeAt = now
        cachedAccentRgb = runCatching { probeSystemAccentColorBlocking() }.getOrNull()
        return cachedAccentRgb
    }
    if (now - lastAccentProbeAt >= 12_000L) {
        lastAccentProbeAt = now
        Thread({
            cachedAccentRgb = runCatching { probeSystemAccentColorBlocking() }.getOrDefault(cachedAccentRgb)
        }, "shiftkar-accent-poll").apply { isDaemon = true; start() }
    }
    return cachedAccentRgb
}

private fun desktopStorageDir(): File {
    val home = System.getProperty("user.home") ?: "."
    val legacyDir = File(home, ".shiftkar-neo")
    if (legacyDir.exists()) return legacyDir
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    val base = when {
        os.contains("win") -> System.getenv("APPDATA")?.let { File(it, "ShiftKar") }
        os.contains("mac") -> File(home, "Library/Application Support/ShiftKar")
        else -> (System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: File(home, ".config")).let { File(it, "shiftkar") }
    } ?: legacyDir
    return base.apply { mkdirs() }
}

actual fun readAppStorageText(name: String): String? {
    val dir = desktopStorageDir()
    val file = File(dir, name)
    if (file.exists() && file.length() > 0L) {
        runCatching { return file.readText(StandardCharsets.UTF_8) }
    }
    val bak = File(dir, "$name.bak")
    if (bak.exists() && bak.length() > 0L) {
        runCatching { return bak.readText(StandardCharsets.UTF_8) }
    }
    return null
}

actual fun recoverAppStorageBackup(name: String): String? {
    val dir = desktopStorageDir()
    val file = File(dir, name)
    if (file.exists()) {
        runCatching {
            Files.move(
                file.toPath(),
                File(dir, "$name.corrupt").toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
    val bak = File(dir, "$name.bak")
    return if (bak.exists() && bak.length() > 0L) {
        runCatching { bak.readText(StandardCharsets.UTF_8) }.getOrNull()
    } else null
}

actual fun writeAppStorageText(name: String, content: String) {
    runCatching {
        val dir = desktopStorageDir()
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        val bak = File(dir, "$name.bak")

        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            runCatching { fos.fd.sync() }
        }

        if (target.exists() && target.length() > 0L) {
            runCatching {
                Files.copy(target.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}

fun readDesktopResourceBytes(path: String): ByteArray {
    val clean = path.removePrefix("/")
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(clean)
        ?: PlatformTarget::class.java.classLoader?.getResourceAsStream(clean)
        ?: error("Resource not found: $clean")
    return stream.use { it.readBytes() }
}

actual fun readBundledEventsJson(): String =
    readDesktopResourceBytes("files/events.json").decodeToString()

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

actual fun loadBundledFontBytes(fileName: String): ByteArray =
    readDesktopResourceBytes("font/$fileName")

actual fun decodePngToImageBitmap(pngBytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(pngBytes).toComposeImageBitmap()

private val cachedNoiseBitmap: ImageBitmap? by lazy {
    runCatching {
        Image.makeFromEncoded(readDesktopResourceBytes("drawable/noise.png")).toComposeImageBitmap()
    }.getOrNull()
}

actual fun loadNoiseImageBitmap(): ImageBitmap? = cachedNoiseBitmap

private val cachedLogoBitmap: ImageBitmap by lazy {
    Image.makeFromEncoded(readDesktopResourceBytes("drawable/sk_logo.png")).toComposeImageBitmap()
}

actual fun loadLogoImageBitmap(): ImageBitmap = cachedLogoBitmap

actual fun copyTextToClipboard(text: String) {
    runCatching {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }
}

actual fun openExternalUrl(url: String) {
    if (url.startsWith("tel:", ignoreCase = true)) {
        copyTextToClipboard(url.substringAfter(":"))
        return
    }
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

actual fun saveTextFileWithDialog(defaultFileName: String, content: String): String? = runCatching {
    val dialog = FileDialog(null as Frame?, "Save Backup File", FileDialog.SAVE)
    dialog.file = defaultFileName
    dialog.isVisible = true
    val dir = dialog.directory ?: return@runCatching null
    val fn = dialog.file ?: return@runCatching null
    val target = File(dir, fn)
    target.writeText(content, StandardCharsets.UTF_8)
    target.absolutePath
}.getOrNull()

actual fun openTextFileWithDialog(): String? = runCatching {
    val dialog = FileDialog(null as Frame?, "Open Backup File", FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return@runCatching null
    val fn = dialog.file ?: return@runCatching null
    val target = File(dir, fn)
    if (target.exists() && target.isFile) target.readText(StandardCharsets.UTF_8) else null
}.getOrNull()

actual fun savePngBytesWithDialog(defaultFileName: String, pngBytes: ByteArray): String? = runCatching {
    val safeName = if (defaultFileName.endsWith(".png", ignoreCase = true)) defaultFileName else "$defaultFileName.png"
    val dialog = FileDialog(null as Frame?, "Save Image (PNG)", FileDialog.SAVE)
    dialog.file = safeName
    dialog.isVisible = true
    val dir = dialog.directory ?: return@runCatching null
    val fn = dialog.file ?: return@runCatching null
    val finalFn = if (fn.endsWith(".png", ignoreCase = true)) fn else "$fn.png"
    val target = File(dir, finalFn)
    target.writeBytes(pngBytes)
    target.absolutePath
}.getOrNull()

private class TransferableAwtImage(private val image: java.awt.Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.imageFlavor.equals(flavor)
    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}

actual fun copyPngBytesToClipboard(pngBytes: ByteArray): Boolean = runCatching {
    val buffered: BufferedImage = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: return false
    val transferable = TransferableAwtImage(buffered)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
    true
}.getOrDefault(false)

actual fun saveRosterPngWithDialog(year: Int, groupCode: String): String? = null

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
