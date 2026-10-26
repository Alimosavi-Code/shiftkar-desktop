package com.seyedhosseinmousavisaeedi.shiftkar.updater

import com.seyedhosseinmousavisaeedi.shiftkar.engine.APP
import com.seyedhosseinmousavisaeedi.shiftkar.platform.currentTimeMillis
import com.seyedhosseinmousavisaeedi.shiftkar.platform.httpGetText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.writeAppStorageText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile

object AppUpdateConfig {
    /**
     * آدرس فایل JSON بروزرسانی نسخهٔ دسکتاپ در مخزن Alimosavi-Code/Shiftkar
     */
    const val DEFAULT_UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/Alimosavi-Code/Shiftkar/refs/heads/main/update-desktop.json"

    /** صفحهٔ Releases مخزن دسکتاپ برای «دانلود مستقیم از گیت‌هاب» در تنظیمات. */
    const val GITHUB_RELEASES_URL =
        "https://github.com/Alimosavi-Code/shiftkar-desktop/releases/latest"

    const val CURRENT_VERSION_CODE = 65
    const val CURRENT_VERSION_NAME = APP.VERSION
}

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val downloads: Map<String, String> = emptyMap(),
    val changelog: List<String>,
    val isForceUpdate: Boolean,
    val releaseDate: String
)

data class UpdateState(
    val status: Status = Status.IDLE,
    val info: UpdateInfo? = null,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {
    enum class Status { IDLE, CHECKING, AVAILABLE, UP_TO_DATE, DOWNLOADING, DOWNLOADED, INSTALLING, ERROR }
}

/** Platform-specific downloader/installer hook (full in-app downloader + native installer launcher on Desktop). */
expect object PlatformUpdaterBridge {
    fun resolvePlatformDownloadUrl(info: UpdateInfo): String
    fun hasInstallPermission(): Boolean
    fun hasNotificationPermission(): Boolean
    fun openInstallPermissionSettings()
    fun hasDownloadedUpdate(targetVersionCode: Int): Boolean
    fun startBackgroundDownload(info: UpdateInfo, onStateUpdate: (UpdateState) -> Unit)
    fun installDownloadedUpdate(info: UpdateInfo?, onStateUpdate: (UpdateState) -> Unit)
    fun clearNotifications()
    fun cleanUpOldUpdateIfNeeded(currentVersionCode: Int)
}

@Serializable
private data class UpdaterPrefsDto(
    val lastAutoCheckAt: Long = 0L,
    val autoDismissedVersion: Int = -1,
    val lastAvailable: UpdateInfo? = null
)

object Updater {
    private const val PREFS_FILE = "shiftkar_updater_prefs_v1.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    @Volatile
    var autoCheckEnabled: Boolean = true

    @Volatile
    var pendingInstallRetry: Boolean = false

    const val DEFAULT_AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    internal fun setStateForTesting(state: UpdateState) {
        _state.value = state
    }

    private fun readPrefs(): UpdaterPrefsDto {
        val raw = runCatching { readAppStorageText(PREFS_FILE) }.getOrNull() ?: return UpdaterPrefsDto()
        return runCatching { json.decodeFromString(UpdaterPrefsDto.serializer(), raw) }.getOrDefault(UpdaterPrefsDto())
    }

    private fun writePrefs(dto: UpdaterPrefsDto) {
        runCatching { writeAppStorageText(PREFS_FILE, json.encodeToString(UpdaterPrefsDto.serializer(), dto)) }
    }

    fun getCurrentVersionCode(): Int = AppUpdateConfig.CURRENT_VERSION_CODE
    fun getCurrentVersionName(): String = AppUpdateConfig.CURRENT_VERSION_NAME

    fun shouldAutoCheck(intervalMs: Long = DEFAULT_AUTO_CHECK_INTERVAL_MS): Boolean {
        if (!autoCheckEnabled) return false
        val last = readPrefs().lastAutoCheckAt
        return currentTimeMillis() - last >= intervalMs
    }

    fun markAutoChecked() {
        val cur = readPrefs()
        writePrefs(cur.copy(lastAutoCheckAt = currentTimeMillis()))
    }

    fun isAutoDismissed(versionCode: Int): Boolean =
        readPrefs().autoDismissedVersion >= versionCode

    fun markAutoDismissed(versionCode: Int) {
        val cur = readPrefs()
        writePrefs(cur.copy(autoDismissedVersion = versionCode))
    }

    fun rememberAvailable(info: UpdateInfo) {
        if (info.versionCode <= getCurrentVersionCode()) return
        val cur = readPrefs()
        writePrefs(cur.copy(lastAvailable = info))
    }

    fun lastKnownAvailable(): UpdateInfo? {
        val info = readPrefs().lastAvailable ?: return null
        return if (info.versionCode > getCurrentVersionCode() && info.downloadUrl.isNotBlank()) info else null
    }

    fun clearLastKnownAvailable() {
        val cur = readPrefs()
        writePrefs(cur.copy(lastAvailable = null))
    }

    suspend fun check(customUrl: String? = null): UpdateState {
        val baseUrl = if (!customUrl.isNullOrBlank()) customUrl else AppUpdateConfig.DEFAULT_UPDATE_JSON_URL
        val targetUrl = if (baseUrl.contains("?")) "$baseUrl&t=${currentTimeMillis()}"
                        else "$baseUrl?t=${currentTimeMillis()}"
        return try {
            _state.value = UpdateState(status = UpdateState.Status.CHECKING)
            val bodyString = httpGetText(targetUrl)
            if (bodyString.isBlank()) {
                val st = UpdateState(status = UpdateState.Status.ERROR, error = "پاسخ خالی از سرور")
                _state.value = st
                return st
            }

            val root = json.parseToJsonElement(bodyString).jsonObject
            val vc = root["versionCode"]?.jsonPrimitive?.intOrNull ?: getCurrentVersionCode()
            val vn = root["versionName"]?.jsonPrimitive?.content ?: getCurrentVersionName()
            val rawDl = root["downloadUrl"]?.jsonPrimitive?.content ?: ""
            val force = root["isForceUpdate"]?.jsonPrimitive?.booleanOrNull ?: false
            val rDate = root["releaseDate"]?.jsonPrimitive?.content ?: ""

            val downloadsMap = mutableMapOf<String, String>()
            val dlObj = root["downloads"] as? JsonObject
            dlObj?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { downloadsMap[k] = it }
            }

            val changelogElem: JsonElement? = root["changelog"]
            val changelogList = when (changelogElem) {
                is JsonArray -> changelogElem.mapNotNull { (it as? JsonPrimitive)?.content }
                is JsonPrimitive -> if (changelogElem.content.isNotBlank()) listOf(changelogElem.content) else emptyList()
                else -> emptyList()
            }

            val preliminary = UpdateInfo(
                versionCode = vc,
                versionName = vn,
                downloadUrl = rawDl,
                downloads = downloadsMap,
                changelog = changelogList,
                isForceUpdate = force,
                releaseDate = rDate
            )
            val resolvedUrl = PlatformUpdaterBridge.resolvePlatformDownloadUrl(preliminary)
            val info = preliminary.copy(downloadUrl = resolvedUrl)

            val st = if (info.versionCode > getCurrentVersionCode() && info.downloadUrl.isNotBlank()) {
                rememberAvailable(info)
                UpdateState(status = UpdateState.Status.AVAILABLE, info = info)
            } else {
                if (info.versionCode <= getCurrentVersionCode()) clearLastKnownAvailable()
                UpdateState(status = UpdateState.Status.UP_TO_DATE, info = info)
            }
            _state.value = st
            st
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val st = UpdateState(
                status = UpdateState.Status.ERROR,
                error = e.message ?: "خطای شبکه در دریافت اطلاعات بروزرسانی — دوباره تلاش کنید"
            )
            _state.value = st
            st
        }
    }

    fun promoteDownloadedIfNeeded() {
        val st = _state.value
        val info = st.info ?: return
        if (st.status == UpdateState.Status.AVAILABLE && PlatformUpdaterBridge.hasDownloadedUpdate(info.versionCode)) {
            _state.value = st.copy(status = UpdateState.Status.DOWNLOADED, progress = 100)
        }
    }

    fun hasDownloadedApk(): Boolean {
        val vc = _state.value.info?.versionCode ?: lastKnownAvailable()?.versionCode ?: -1
        return PlatformUpdaterBridge.hasDownloadedUpdate(vc)
    }

    fun startBackgroundDownload() {
        val info = _state.value.info ?: lastKnownAvailable()
        if (info == null) {
            _state.value = _state.value.copy(status = UpdateState.Status.ERROR, error = "ابتدا بروزرسانی را بررسی کنید")
            return
        }
        PlatformUpdaterBridge.startBackgroundDownload(info) { newState ->
            _state.value = newState
        }
    }

    fun install() {
        val info = _state.value.info ?: lastKnownAvailable()
        PlatformUpdaterBridge.installDownloadedUpdate(info) { newState ->
            _state.value = newState
        }
    }

    fun abortInstallAttempt() {
        if (_state.value.status == UpdateState.Status.INSTALLING) {
            _state.value = _state.value.copy(status = UpdateState.Status.DOWNLOADED, progress = 100)
        }
    }

    fun hasInstallPermission(): Boolean = PlatformUpdaterBridge.hasInstallPermission()
    fun hasNotificationPermission(): Boolean = PlatformUpdaterBridge.hasNotificationPermission()
    fun openInstallPermissionSettings() = PlatformUpdaterBridge.openInstallPermissionSettings()
    fun clearNotifications() = PlatformUpdaterBridge.clearNotifications()
    fun cleanUpOldApkIfUpdated() {
        PlatformUpdaterBridge.cleanUpOldUpdateIfNeeded(getCurrentVersionCode())
        if (lastKnownAvailable() == null) clearLastKnownAvailable()
    }
}
