package com.seyedhosseinmousavisaeedi.shiftkar.updater

import com.seyedhosseinmousavisaeedi.shiftkar.platform.openExternalUrl
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.writeAppStorageText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real In-App Updater for Desktop (Windows, Linux, macOS):
 * 1. Detects the current OS & CPU architecture (x64 vs arm64) and Linux distro package type (.rpm on Fedora/RHEL, .deb on Ubuntu/Debian, or .AppImage)
 * 2. Streams the matching installer to `~/.shiftkar-neo/updates/` with live 0..100% progress in `UpdaterPopup`
 * 3. Launches the native OS installer automatically on completion or when "نصب نسخهٔ جدید" is clicked!
 */
actual object PlatformUpdaterBridge {
    private const val META_FILE = "downloaded_desktop_update_version.txt"
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun updatesDir(): File {
        val home = System.getProperty("user.home") ?: "."
        return File(home, ".shiftkar-neo/updates").apply { mkdirs() }
    }

    private fun isArm64(): Boolean {
        val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
        return arch.contains("aarch64") || arch.contains("arm64")
    }

    private fun detectLinuxPreferredFormat(): String {
        val appImageEnv = System.getenv("APPIMAGE")
        if (!appImageEnv.isNullOrBlank()) return "appimage"
        if (File("/etc/fedora-release").exists() || File("/etc/redhat-release").exists() || File("/usr/bin/rpm").exists() && !File("/usr/bin/dpkg").exists()) {
            return "rpm"
        }
        if (File("/etc/debian_version").exists() || File("/usr/bin/dpkg").exists()) {
            return "deb"
        }
        return "appimage"
    }

    actual fun resolvePlatformDownloadUrl(info: UpdateInfo): String {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val arm = isArm64()
        val archKey = if (arm) "arm64" else "x64"
        val d = info.downloads
        if (d.isNotEmpty()) {
            val matched = when {
                os.contains("win") -> d["windows-$archKey"] ?: d["windows-$archKey-exe"] ?: d["windows-$archKey-msi"] ?: d["windows-x64"] ?: d["windows"]
                os.contains("mac") -> d["macos-$archKey"] ?: d["macos"]
                else -> {
                    val fmt = detectLinuxPreferredFormat()
                    d["linux-$archKey-$fmt"] ?: d["linux-$archKey-appimage"] ?: d["linux-$archKey"] ?: d["linux"]
                }
            }
            if (!matched.isNullOrBlank()) return matched
        }
        // Automatic fallback: if downloadUrl points to Alimosavi-Code/shiftkar-desktop/releases/download/vX.Y.Z/...,
        // automatically map to the current OS & CPU standard asset filename!
        val v = info.versionName.removePrefix("v")
        val baseRelease = "https://github.com/Alimosavi-Code/shiftkar-desktop/releases/download/v$v"
        return when {
            os.contains("win") -> "$baseRelease/ShiftKar-$v-windows-$archKey-setup.exe"
            os.contains("mac") -> if (arm) "$baseRelease/ShiftKar-$v-macos-arm64.dmg" else "$baseRelease/ShiftKar-$v-macos-x64.dmg"
            else -> {
                val fmt = detectLinuxPreferredFormat()
                when (fmt) {
                    "rpm" -> "$baseRelease/ShiftKar-$v-linux-$archKey.rpm"
                    "deb" -> "$baseRelease/ShiftKar-$v-linux-$archKey.deb"
                    else -> "$baseRelease/ShiftKar-$v-linux-$archKey.AppImage"
                }
            }
        }
    }

    private fun targetFileName(url: String, versionName: String): String {
        val cleanUrl = url.substringBefore("?")
        val ext = when {
            cleanUrl.endsWith(".msi", ignoreCase = true) -> ".msi"
            cleanUrl.endsWith(".exe", ignoreCase = true) -> ".exe"
            cleanUrl.endsWith(".dmg", ignoreCase = true) -> ".dmg"
            cleanUrl.endsWith(".rpm", ignoreCase = true) -> ".rpm"
            cleanUrl.endsWith(".deb", ignoreCase = true) -> ".deb"
            cleanUrl.endsWith(".AppImage", ignoreCase = true) -> ".AppImage"
            cleanUrl.endsWith(".tar.gz", ignoreCase = true) -> ".tar.gz"
            cleanUrl.endsWith(".zip", ignoreCase = true) -> ".zip"
            else -> {
                val os = System.getProperty("os.name")?.lowercase().orEmpty()
                when {
                    os.contains("win") -> ".exe"
                    os.contains("mac") -> ".dmg"
                    else -> ".AppImage"
                }
            }
        }
        return "ShiftKar-update-$versionName$ext"
    }

    private fun findDownloadedInstallerFile(): File? {
        val files = updatesDir().listFiles() ?: return null
        return files.firstOrNull { it.isFile && it.length() > 0L && it.name.startsWith("ShiftKar-update-") }
    }

    actual fun hasInstallPermission(): Boolean = true
    actual fun hasNotificationPermission(): Boolean = true
    actual fun openInstallPermissionSettings() {}

    actual fun hasDownloadedUpdate(targetVersionCode: Int): Boolean {
        val f = findDownloadedInstallerFile() ?: return false
        if (targetVersionCode <= 0) return f.exists()
        val saved = runCatching { readAppStorageText(META_FILE)?.trim()?.toIntOrNull() }.getOrNull() ?: -1
        return saved == targetVersionCode && f.exists()
    }

    actual fun startBackgroundDownload(info: UpdateInfo, onStateUpdate: (UpdateState) -> Unit) {
        val downloadUrl = resolvePlatformDownloadUrl(info)
        if (downloadUrl.isBlank()) {
            openExternalUrl(AppUpdateConfig.GITHUB_RELEASES_URL)
            return
        }
        if (hasDownloadedUpdate(info.versionCode)) {
            val existing = findDownloadedInstallerFile()!!
            onStateUpdate(
                UpdateState(
                    status = UpdateState.Status.DOWNLOADED,
                    info = info,
                    progress = 100,
                    downloadedBytes = existing.length(),
                    totalBytes = existing.length()
                )
            )
            return
        }

        bgScope.launch {
            // Clean old update files
            updatesDir().listFiles()?.forEach { runCatching { it.delete() } }
            val dest = File(updatesDir(), targetFileName(downloadUrl, info.versionName))
            var lastError: Exception? = null

            for (attempt in 1..2) {
                try {
                    val req = Request.Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "ShiftKar-Desktop-Updater")
                        .build()
                    http.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("خطا در دانلود فایل بروزرسانی (کد ${response.code})")
                        }
                        val body = response.body ?: throw IllegalStateException("پاسخ دانلود خالی است")
                        val contentLength = body.contentLength()
                        var done = 0L

                        body.byteStream().use { input ->
                            dest.outputStream().use { output ->
                                val buf = ByteArray(16 * 1024)
                                var lastReported = -1
                                while (true) {
                                    val n = input.read(buf)
                                    if (n == -1) break
                                    output.write(buf, 0, n)
                                    done += n
                                    val percent = if (contentLength > 0) ((done * 100) / contentLength).toInt().coerceIn(0, 100) else 0
                                    if (percent != lastReported) {
                                        lastReported = percent
                                        onStateUpdate(
                                            UpdateState(
                                                status = UpdateState.Status.DOWNLOADING,
                                                info = info,
                                                progress = percent,
                                                downloadedBytes = done,
                                                totalBytes = contentLength
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (contentLength > 0 && done != contentLength) {
                            throw IllegalStateException("دانلود ناقص بود ($done از $contentLength بایت)")
                        }
                    }

                    if (dest.name.endsWith(".AppImage", ignoreCase = true)) {
                        runCatching { dest.setExecutable(true) }
                    }
                    writeAppStorageText(META_FILE, info.versionCode.toString())
                    onStateUpdate(
                        UpdateState(
                            status = UpdateState.Status.DOWNLOADED,
                            info = info,
                            progress = 100,
                            downloadedBytes = dest.length(),
                            totalBytes = dest.length()
                        )
                    )
                    return@launch
                } catch (ce: CancellationException) {
                    dest.delete()
                    throw ce
                } catch (e: Exception) {
                    lastError = e
                    dest.delete()
                    if (attempt < 2) delay(800)
                }
            }

            onStateUpdate(
                UpdateState(
                    status = UpdateState.Status.ERROR,
                    info = info,
                    error = lastError?.message ?: "دانلود ناموفق بود"
                )
            )
        }
    }

    actual fun installDownloadedUpdate(info: UpdateInfo?, onStateUpdate: (UpdateState) -> Unit) {
        val installer = findDownloadedInstallerFile()
        if (installer == null || !installer.exists()) {
            onStateUpdate(
                UpdateState(
                    status = UpdateState.Status.ERROR,
                    info = info,
                    error = "فایل نصب یافت نشد — دوباره دانلود کنید"
                )
            )
            return
        }

        try {
            onStateUpdate(UpdateState(status = UpdateState.Status.INSTALLING, info = info, progress = 100))
            val os = System.getProperty("os.name")?.lowercase().orEmpty()
            val path = installer.absolutePath
            when {
                os.contains("win") -> {
                    if (path.endsWith(".msi", ignoreCase = true)) {
                        ProcessBuilder("msiexec", "/i", path).start()
                    } else {
                        ProcessBuilder("cmd", "/c", "start", "", path).start()
                    }
                }
                os.contains("mac") -> {
                    ProcessBuilder("open", path).start()
                }
                else -> {
                    if (path.endsWith(".AppImage", ignoreCase = true)) {
                        installer.setExecutable(true)
                        // Open folder containing the new AppImage and launch it
                        runCatching { ProcessBuilder("xdg-open", installer.parentFile.absolutePath).start() }
                        ProcessBuilder(path).start()
                    } else {
                        // Open .rpm or .deb in GNOME Software / KDE Discover, and also open folder as fallback
                        val launched = runCatching { ProcessBuilder("xdg-open", path).start() }.isSuccess
                        if (!launched && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(installer)
                        }
                    }
                }
            }
            // Keep status as DOWNLOADED after launching installer so user can re-trigger if needed
            onStateUpdate(UpdateState(status = UpdateState.Status.DOWNLOADED, info = info, progress = 100))
        } catch (e: Exception) {
            runCatching {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(installer.parentFile)
            }
            onStateUpdate(
                UpdateState(
                    status = UpdateState.Status.DOWNLOADED,
                    info = info,
                    error = e.message ?: "اجرای خودکار نصاب ناموفق بود؛ پوشهٔ فایل باز شد"
                )
            )
        }
    }

    actual fun clearNotifications() {}

    actual fun cleanUpOldUpdateIfNeeded(currentVersionCode: Int) {
        val saved = runCatching { readAppStorageText(META_FILE)?.trim()?.toIntOrNull() }.getOrNull() ?: -1
        if (saved != -1 && currentVersionCode >= saved) {
            updatesDir().listFiles()?.forEach { runCatching { it.delete() } }
            writeAppStorageText(META_FILE, "")
        }
    }
}
