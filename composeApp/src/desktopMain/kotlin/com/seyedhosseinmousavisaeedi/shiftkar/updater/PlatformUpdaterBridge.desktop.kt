package com.seyedhosseinmousavisaeedi.shiftkar.updater

import com.seyedhosseinmousavisaeedi.shiftkar.platform.openExternalUrl
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.writeAppStorageText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Hardened In-App Updater for Desktop (v6.2.0):
 *  1. Strict HTTPS & repository URL allowlist (`https://github.com/Alimosavi-Code/...`)
 *  2. Sanitized versionName & single-job concurrency guard
 *  3. Atomic `.part` streaming download with live 0..100% progress
 *  4. Cryptographic SHA-256 verification (via `sha256` field in `update-desktop.json` OR
 *     automatic companion `<installer>.sha256` / `SHA256SUMS.txt` from GitHub Releases)
 *  5. Automatic exit on Windows/macOS installer launch so files are never locked
 */
actual object PlatformUpdaterBridge {
    private const val META_FILE = "downloaded_desktop_update_version.txt"
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeDownloadJob: Job? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val ALLOWED_URL_PREFIXES = listOf(
        "https://github.com/Alimosavi-Code/",
        "https://raw.githubusercontent.com/Alimosavi-Code/",
        "https://objects.githubusercontent.com/"
    )

    private fun isAllowedDownloadUrl(url: String): Boolean =
        ALLOWED_URL_PREFIXES.any { url.startsWith(it, ignoreCase = true) }

    private fun sanitizeVersion(raw: String): String =
        raw.replace(Regex("[^0-9A-Za-z._-]"), "").removePrefix("v").ifBlank { "0.0.0" }

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
        if (File("/etc/fedora-release").exists() || File("/etc/redhat-release").exists() || (File("/usr/bin/rpm").exists() && !File("/usr/bin/dpkg").exists())) {
            return "rpm"
        }
        if (File("/etc/debian_version").exists() || File("/usr/bin/dpkg").exists()) {
            return "deb"
        }
        return "appimage"
    }

    private fun currentPlatformKeys(): List<String> {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val arm = isArm64()
        val archKey = if (arm) "arm64" else "x64"
        return when {
            os.contains("win") -> listOf("windows-$archKey", "windows-$archKey-exe", "windows-$archKey-msi", "windows-x64", "windows")
            os.contains("mac") -> listOf("macos-$archKey", "macos")
            else -> {
                val fmt = detectLinuxPreferredFormat()
                listOf("linux-$archKey-$fmt", "linux-$archKey-appimage", "linux-$archKey", "linux")
            }
        }
    }

    actual fun resolvePlatformDownloadUrl(info: UpdateInfo): String {
        val d = info.downloads
        if (d.isNotEmpty()) {
            val matched = currentPlatformKeys().firstNotNullOfOrNull { k -> d[k]?.takeIf { it.isNotBlank() } }
            if (!matched.isNullOrBlank() && isAllowedDownloadUrl(matched)) return matched
        }
        val v = sanitizeVersion(info.versionName)
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val arm = isArm64()
        val archKey = if (arm) "arm64" else "x64"
        val baseRelease = "https://github.com/Alimosavi-Code/shiftkar-desktop/releases/download/v$v"
        return when {
            os.contains("win") -> "$baseRelease/ShiftKar-$v-windows-$archKey-setup.exe"
            os.contains("mac") -> if (arm) "$baseRelease/ShiftKar-$v-macos-arm64.dmg" else "$baseRelease/ShiftKar-$v-macos-x64.dmg"
            else -> {
                when (detectLinuxPreferredFormat()) {
                    "rpm" -> "$baseRelease/ShiftKar-$v-linux-$archKey.rpm"
                    "deb" -> "$baseRelease/ShiftKar-$v-linux-$archKey.deb"
                    else -> "$baseRelease/ShiftKar-$v-linux-$archKey.AppImage"
                }
            }
        }
    }

    /**
     * Resolves the expected SHA-256 hex string for the downloaded installer:
     *  1. Checks `info.sha256` from `update-desktop.json` for the current platform key
     *  2. Otherwise fetches `<downloadUrl>.sha256` or `SHA256SUMS.txt` published by GitHub Actions
     */
    private fun resolveExpectedSha256(info: UpdateInfo, downloadUrl: String): String? {
        val hexRegex = Regex("([0-9a-fA-F]{64})")
        if (info.sha256.isNotEmpty()) {
            val fromJson = (currentPlatformKeys() + "default")
                .firstNotNullOfOrNull { k -> info.sha256[k]?.trim()?.takeIf { hexRegex.matches(it) } }
            if (fromJson != null) return fromJson.lowercase()
        }
        // Automatic companion .sha256 lookup on GitHub Releases
        val cleanUrl = downloadUrl.substringBefore("?")
        val fileName = cleanUrl.substringAfterLast("/")
        val companionUrl = "$cleanUrl.sha256"
        if (isAllowedDownloadUrl(companionUrl)) {
            runCatching {
                val req = Request.Builder().url(companionUrl).header("User-Agent", "ShiftKar-Desktop-Updater").build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val txt = resp.body?.string().orEmpty()
                        hexRegex.find(txt)?.groupValues?.get(1)?.lowercase()?.let { return it }
                    }
                }
            }
        }
        // Also check SHA256SUMS.txt in the same GitHub Release directory
        val releaseBase = cleanUrl.substringBeforeLast("/", "")
        if (releaseBase.isNotBlank() && isAllowedDownloadUrl(releaseBase)) {
            runCatching {
                val sumsUrl = "$releaseBase/SHA256SUMS.txt"
                val req = Request.Builder().url(sumsUrl).header("User-Agent", "ShiftKar-Desktop-Updater").build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val lines = resp.body?.string().orEmpty().lines()
                        val matchingLine = lines.firstOrNull { it.contains(fileName, ignoreCase = true) }
                        if (matchingLine != null) {
                            hexRegex.find(matchingLine)?.groupValues?.get(1)?.lowercase()?.let { return it }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun targetFileName(url: String, versionName: String): String {
        val cleanUrl = url.substringBefore("?")
        val safeVer = sanitizeVersion(versionName)
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
        return "ShiftKar-update-$safeVer$ext"
    }

    private fun findDownloadedInstallerFile(): File? {
        val files = updatesDir().listFiles() ?: return null
        return files.firstOrNull {
            it.isFile &&
                it.length() > 0L &&
                it.name.startsWith("ShiftKar-update-") &&
                !it.name.endsWith(".part", ignoreCase = true)
        }
    }

    actual fun hasInstallPermission(): Boolean = true
    actual fun hasNotificationPermission(): Boolean = true
    actual fun openInstallPermissionSettings() {}

    actual fun hasDownloadedUpdate(targetVersionCode: Int): Boolean {
        if (targetVersionCode <= 0) return false
        val f = findDownloadedInstallerFile() ?: return false
        val saved = runCatching { readAppStorageText(META_FILE)?.trim()?.toIntOrNull() }.getOrNull() ?: -1
        return saved == targetVersionCode && f.exists() && f.length() > 0L
    }

    actual fun startBackgroundDownload(info: UpdateInfo, onStateUpdate: (UpdateState) -> Unit) {
        if (activeDownloadJob?.isActive == true) return
        val downloadUrl = resolvePlatformDownloadUrl(info)
        if (downloadUrl.isBlank() || !isAllowedDownloadUrl(downloadUrl)) {
            onStateUpdate(
                UpdateState(
                    status = UpdateState.Status.ERROR,
                    info = info,
                    error = "آدرس دانلود نامعتبر یا خارج از مخزن رسمی است"
                )
            )
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

        activeDownloadJob = bgScope.launch {
            updatesDir().listFiles()?.forEach { runCatching { it.delete() } }
            val finalFile = File(updatesDir(), targetFileName(downloadUrl, info.versionName))
            val partFile = File(updatesDir(), "${finalFile.name}.part")
            var lastError: Exception? = null

            for (attempt in 1..2) {
                try {
                    val expectedSha256 = resolveExpectedSha256(info, downloadUrl)
                    val digest = MessageDigest.getInstance("SHA-256")

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
                            partFile.outputStream().use { output ->
                                val buf = ByteArray(16 * 1024)
                                var lastReported = -1
                                while (true) {
                                    val n = input.read(buf)
                                    if (n == -1) break
                                    output.write(buf, 0, n)
                                    digest.update(buf, 0, n)
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
                                output.flush()
                            }
                        }
                        if (contentLength > 0 && done != contentLength) {
                            throw IllegalStateException("دانلود ناقص بود ($done از $contentLength بایت)")
                        }
                    }

                    // Verify SHA-256 hash when present in update-desktop.json or GitHub Release .sha256
                    val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!expectedSha256.isNullOrBlank() && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        partFile.delete()
                        throw SecurityException("خطای امنیتی: هش SHA-256 فایل دانلودشده با مقدار رسمی مطابقت ندارد!")
                    }

                    // Atomically promote .part -> finalFile
                    Files.move(
                        partFile.toPath(),
                        finalFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )

                    if (finalFile.name.endsWith(".AppImage", ignoreCase = true)) {
                        runCatching { finalFile.setExecutable(true) }
                    }
                    writeAppStorageText(META_FILE, info.versionCode.toString())
                    onStateUpdate(
                        UpdateState(
                            status = UpdateState.Status.DOWNLOADED,
                            info = info,
                            progress = 100,
                            downloadedBytes = finalFile.length(),
                            totalBytes = finalFile.length()
                        )
                    )
                    return@launch
                } catch (ce: CancellationException) {
                    partFile.delete()
                    throw ce
                } catch (se: SecurityException) {
                    partFile.delete()
                    lastError = se
                    break // Never retry a failed cryptographic hash check
                } catch (e: Exception) {
                    lastError = e
                    partFile.delete()
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
                    bgScope.launch {
                        delay(900)
                        kotlin.system.exitProcess(0)
                    }
                }
                os.contains("mac") -> {
                    ProcessBuilder("open", path).start()
                }
                else -> {
                    if (path.endsWith(".AppImage", ignoreCase = true)) {
                        installer.setExecutable(true)
                        runCatching { ProcessBuilder("xdg-open", installer.parentFile.absolutePath).start() }
                        ProcessBuilder(path).start()
                    } else {
                        val launched = runCatching { ProcessBuilder("xdg-open", path).start() }.isSuccess
                        if (!launched && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(installer)
                        }
                    }
                }
            }
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
