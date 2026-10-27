package com.seyedhosseinmousavisaeedi.shiftkar.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.seyedhosseinmousavisaeedi.shiftkar.engine.Holidays
import com.seyedhosseinmousavisaeedi.shiftkar.engine.JALALI_MONTHS
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftEngine
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftGroup
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ShiftKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.codeToKind
import com.seyedhosseinmousavisaeedi.shiftkar.engine.jalaaliMonthLength
import com.seyedhosseinmousavisaeedi.shiftkar.engine.jalaaliWeekday
import com.seyedhosseinmousavisaeedi.shiftkar.engine.makeDateKey
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
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
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
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
        cachedSystemDark = runCatching { probeSystemDarkModeBlocking() }.getOrDefault(cachedSystemDark)
    }
    return cachedSystemDark
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

/**
 * If a primary storage file fails JSON parsing, moves it to `.corrupt` and returns `.bak` content if available.
 */
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

/**
 * Atomic file write: writes to `<name>.tmp`, flushes descriptor to disk, copies previous valid
 * file to `<name>.bak`, and atomically renames `<name>.tmp` -> `<name>`.
 */
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

actual fun saveRosterPngWithDialog(year: Int, groupCode: String): String? = runCatching {
    val group = runCatching { ShiftGroup.valueOf(groupCode) }.getOrDefault(ShiftGroup.A)
    val dialog = FileDialog(null as Frame?, "Save Annual Roster (PNG)", FileDialog.SAVE)
    dialog.file = "ShiftKar-Roster-$year-Group-${group.name}.png"
    dialog.isVisible = true
    val dir = dialog.directory ?: return@runCatching null
    val fn = dialog.file ?: return@runCatching null
    val target = File(dir, if (fn.endsWith(".png", ignoreCase = true)) fn else "$fn.png")

    val width = 1600
    val height = 1180
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2 = img.createGraphics()
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    val awtFontBase = runCatching {
        java.awt.Font.createFont(
            java.awt.Font.TRUETYPE_FONT,
            readResourceBytes("font/iransansx_bold.ttf").inputStream()
        )
    }.getOrElse { java.awt.Font("SansSerif", java.awt.Font.BOLD, 16) }

    g2.color = Color(12, 18, 36)
    g2.fillRect(0, 0, width, height)

    g2.font = awtFontBase.deriveFont(java.awt.Font.BOLD, 28f)
    g2.color = Color(240, 245, 255)
    val title = "لوحه سالانه شیفت‌کار — سال ${toPersianDigits(year)} (گروه ${group.name})"
    val titleW = g2.fontMetrics.stringWidth(title)
    g2.drawString(title, (width - titleW) / 2, 52)

    g2.font = awtFontBase.deriveFont(java.awt.Font.BOLD, 15f)
    val legends = listOf(
        Triple("صبح‌کار", Color(245, 158, 11), Color(68, 48, 14)),
        Triple("شب‌کار", Color(99, 102, 241), Color(30, 35, 82)),
        Triple("استراحت", Color(16, 185, 129), Color(14, 58, 47)),
        Triple("تعطیل / جمعه", Color(239, 68, 68), Color(68, 22, 32))
    )
    var lx = width / 2 - 250
    for ((label, dotCol, _) in legends) {
        g2.color = dotCol
        g2.fillRoundRect(lx, 70, 16, 16, 6, 6)
        g2.color = Color(200, 210, 230)
        g2.drawString(label, lx + 24, 84)
        lx += 135
    }

    val cols = 4
    val marginX = 36
    val startY = 110
    val gapX = 20
    val gapY = 20
    val cardW = (width - marginX * 2 - gapX * (cols - 1)) / cols
    val cardH = (height - startY - 40 - gapY * 2) / 3
    val weekdays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    for (idx in 0 until 12) {
        val jm = idx + 1
        val row = idx / cols
        val colRtl = (cols - 1) - (idx % cols)
        val x0 = marginX + colRtl * (cardW + gapX)
        val y0 = startY + row * (cardH + gapY)

        g2.color = Color(22, 31, 56)
        g2.fillRoundRect(x0, y0, cardW, cardH, 24, 24)
        g2.color = Color(45, 60, 98)
        g2.stroke = BasicStroke(1.5f)
        g2.drawRoundRect(x0, y0, cardW, cardH, 24, 24)

        g2.font = awtFontBase.deriveFont(java.awt.Font.BOLD, 18f)
        g2.color = Color(235, 242, 255)
        val mTitle = "${JALALI_MONTHS[jm - 1]} ${toPersianDigits(year)}"
        val mW = g2.fontMetrics.stringWidth(mTitle)
        g2.drawString(mTitle, x0 + (cardW - mW) / 2, y0 + 30)

        val cellPadX = 14
        val gridW = cardW - cellPadX * 2
        val cellW = gridW / 7
        val cellH = 34

        g2.font = awtFontBase.deriveFont(java.awt.Font.BOLD, 12f)
        for (wIdx in 0 until 7) {
            val colX = x0 + cellPadX + (6 - wIdx) * cellW
            g2.color = if (wIdx == 6) Color(248, 113, 113) else Color(148, 163, 184)
            val wStr = weekdays[wIdx]
            val wW = g2.fontMetrics.stringWidth(wStr)
            g2.drawString(wStr, colX + (cellW - wW) / 2, y0 + 54)
        }

        val len = jalaaliMonthLength(year, jm)
        val firstWd = jalaaliWeekday(year, jm, 1)
        g2.font = awtFontBase.deriveFont(java.awt.Font.BOLD, 13f)

        for (d in 1..len) {
            val pos = firstWd + (d - 1)
            val r = pos / 7
            val c = pos % 7
            val cx = x0 + cellPadX + (6 - c) * cellW
            val cy = y0 + 64 + r * (cellH + 4)

            val key = makeDateKey(year, jm, d)
            val code = ShiftEngine.getShiftCode(key, group)
            val kind = codeToKind(code)
            val isHol = Holidays.isHoliday(year, jm, d) || jalaaliWeekday(year, jm, d) == 6

            val bgCol = when {
                isHol -> Color(92, 28, 42)
                kind == ShiftKind.DAY -> Color(78, 54, 16)
                kind == ShiftKind.NIGHT -> Color(36, 42, 96)
                else -> Color(16, 66, 54)
            }
            val fgCol = when {
                isHol -> Color(252, 165, 165)
                kind == ShiftKind.DAY -> Color(253, 224, 71)
                kind == ShiftKind.NIGHT -> Color(165, 180, 252)
                else -> Color(110, 231, 183)
            }

            g2.color = bgCol
            g2.fillRoundRect(cx + 2, cy, cellW - 4, cellH, 10, 10)
            g2.color = fgCol
            val dStr = toPersianDigits(d)
            val dW = g2.fontMetrics.stringWidth(dStr)
            g2.drawString(dStr, cx + (cellW - dW) / 2, cy + 22)
        }
    }

    g2.dispose()
    ImageIO.write(img, "PNG", target)
    target.absolutePath
}.getOrNull()

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
