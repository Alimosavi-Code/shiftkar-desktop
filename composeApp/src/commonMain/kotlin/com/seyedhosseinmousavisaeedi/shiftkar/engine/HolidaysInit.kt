package com.seyedhosseinmousavisaeedi.shiftkar.engine

import com.seyedhosseinmousavisaeedi.shiftkar.platform.httpGetText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readBundledEventsJson
import com.seyedhosseinmousavisaeedi.shiftkar.platform.writeAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.updater.AppUpdateConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Multiplatform Holiday Bootstrapper & Remote Calendar Sync Engine (v6.7.3).
 * Uses `readAppStorageText`, `writeAppStorageText`, and `httpGetText` (zero Android dependencies).
 */
object HolidaysLoader {
    @Volatile
    var ready: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun ensureLoaded() {
        try {
            if (!EventsRepo.isLoaded) {
                CalendarSync.loadCached()
                EventsRepo.initWithJson(readBundledEventsJson())
            }
        } catch (_: Throwable) {
        } finally {
            ready = true
        }
    }

    fun kick() {
        if (ready && EventsRepo.isLoaded) return
        scope.launch {
            ensureLoaded()
        }
    }
}

data class RemoteExtraEvent(
    val date: String,       // "1406-02-28" (specific year) or "02-28" (every year)
    val title: String,
    val isHoliday: Boolean
)

object CalendarSync {

    const val DEFAULT_CALENDAR_SYNC_URL =
        "https://github.com/Alimosavi-Code/Shiftkar/blob/main/calendar-sync.json"

    private const val CACHED_FILE_NAME = "shiftkar_calendar_sync_v1.json"
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var syncVersion: Int = 1
        private set

    @Volatile
    var updatedAt: String = "پیش‌فرض برنامه"
        private set

    @Volatile
    var globalHijriDayOffset: Int = 0
        private set

    private val hijriMonthDayOffsets = ConcurrentHashMap<String, Int>()
    private val hijriMonthLengths = ConcurrentHashMap<String, Int>()
    private val hijriYearMonths = ConcurrentHashMap<Int, List<Int>>()

    @Volatile
    var extraEvents: List<RemoteExtraEvent> = emptyList()
        private set

    @Volatile
    var removeHolidayDates: Set<String> = emptySet()
        private set

    @Volatile
    var removeEventsOnDates: Set<String> = emptySet()
        private set

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private fun normYm(raw: String): String {
        val parts = raw.trim().split("-", "/")
        if (parts.size != 2) return raw.trim()
        val y = parts[0].trim().toIntOrNull() ?: return raw.trim()
        val m = parts[1].trim().toIntOrNull() ?: return raw.trim()
        return "%04d-%02d".format(Locale.US, y, m)
    }

    private fun normYmd(raw: String): String {
        val parts = raw.trim().split("-", "/")
        if (parts.size == 3) {
            val y = parts[0].trim().toIntOrNull() ?: return raw.trim()
            val m = parts[1].trim().toIntOrNull() ?: return raw.trim()
            val d = parts[2].trim().toIntOrNull() ?: return raw.trim()
            return "%04d-%02d-%02d".format(Locale.US, y, m, d)
        } else if (parts.size == 2) {
            val m = parts[0].trim().toIntOrNull() ?: return raw.trim()
            val d = parts[1].trim().toIntOrNull() ?: return raw.trim()
            return "%02d-%02d".format(Locale.US, m, d)
        }
        return raw.trim()
    }

    fun getMonthDayOffset(hy: Int, hm: Int): Int {
        if (hijriMonthDayOffsets.isEmpty()) return 0
        val key = "%04d-%02d".format(Locale.US, hy, hm)
        return hijriMonthDayOffsets[key] ?: 0
    }

    fun preprocessJsonWithComments(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        val n = raw.length
        var inString = false
        while (i < n) {
            val c = raw[i]
            if (inString) {
                if (c == '\\' && i + 1 < n) {
                    sb.append(c).append(raw[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') inString = false
                sb.append(c)
                i++
            } else {
                if (c == '"') {
                    inString = true
                    sb.append(c)
                    i++
                } else if (c == '/' && i + 1 < n && raw[i + 1] == '/') {
                    i += 2
                    while (i < n && raw[i] != '\n') i++
                } else if (c == '/' && i + 1 < n && raw[i + 1] == '*') {
                    i += 2
                    while (i + 1 < n && !(raw[i] == '*' && raw[i + 1] == '/')) i++
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
        }
        val withoutComments = sb.toString()
        return withoutComments.replace(Regex(",\\s*([}\\]])"), "$1")
    }

    fun applyJsonString(rawJson: String): Boolean {
        return runCatching {
            val clean = preprocessJsonWithComments(rawJson)
            val root = jsonParser.parseToJsonElement(clean).jsonObject

            val ver = root["syncVersion"]?.jsonPrimitive?.intOrNull ?: 1
            val upd = root["updatedAt"]?.jsonPrimitive?.content?.ifBlank { "پیش‌فرض برنامه" } ?: "پیش‌فرض برنامه"
            val globalOff = (root["globalHijriDayOffset"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(-5, 5)

            val newMonthOffsets = HashMap<String, Int>()
            (root["hijriMonthDayOffset"] as? JsonObject)?.forEach { (k, vEl) ->
                if (!k.startsWith("_")) {
                    val v = (vEl.jsonPrimitive.intOrNull ?: 0).coerceIn(-5, 5)
                    if (v != 0) newMonthOffsets[normYm(k)] = v
                }
            }

            val newMonthLengths = HashMap<String, Int>()
            (root["hijriMonthLengthOverrides"] as? JsonObject)?.forEach { (k, vEl) ->
                if (!k.startsWith("_")) {
                    val v = vEl.jsonPrimitive.intOrNull ?: 0
                    if (v == 29 || v == 30) newMonthLengths[normYm(k)] = v
                }
            }

            val newYearMonths = HashMap<Int, List<Int>>()
            (root["hijriYearMonths"] as? JsonObject)?.forEach { (k, vEl) ->
                if (!k.startsWith("_")) {
                    val hy = k.trim().toIntOrNull()
                    val arr = vEl as? JsonArray
                    if (hy != null && arr != null && arr.size == 12) {
                        val months = arr.map { el ->
                            if ((el.jsonPrimitive.intOrNull ?: 0) == 30) 30 else 29
                        }
                        newYearMonths[hy] = months
                    }
                }
            }

            val newExtras = mutableListOf<RemoteExtraEvent>()
            (root["extraEvents"] as? JsonArray)?.forEach { itemEl ->
                val item = itemEl as? JsonObject ?: return@forEach
                val d = item["date"]?.jsonPrimitive?.content?.trim().orEmpty()
                val t = item["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                val h = item["isHoliday"]?.jsonPrimitive?.booleanOrNull ?: false
                if (d.isNotEmpty() && t.isNotEmpty()) {
                    newExtras.add(RemoteExtraEvent(normYmd(d), t, h))
                }
            }

            val newRemoveHol = HashSet<String>()
            (root["removeHolidayDates"] as? JsonArray)?.forEach { el ->
                val d = el.jsonPrimitive.content.trim()
                if (d.isNotEmpty()) newRemoveHol.add(normYmd(d))
            }

            val newRemoveEv = HashSet<String>()
            (root["removeEventsOnDates"] as? JsonArray)?.forEach { el ->
                val d = el.jsonPrimitive.content.trim()
                if (d.isNotEmpty()) newRemoveEv.add(normYmd(d))
            }

            syncVersion = ver
            updatedAt = upd
            globalHijriDayOffset = globalOff

            hijriMonthDayOffsets.clear()
            hijriMonthDayOffsets.putAll(newMonthOffsets)

            hijriMonthLengths.clear()
            hijriMonthLengths.putAll(newMonthLengths)

            hijriYearMonths.clear()
            hijriYearMonths.putAll(newYearMonths)

            extraEvents = newExtras
            removeHolidayDates = newRemoveHol
            removeEventsOnDates = newRemoveEv

            IslamicCalendar.rebuildWithOverrides(newYearMonths, newMonthLengths)
            Holidays.clearCache()
            _revision.value = _revision.value + 1
            true
        }.getOrDefault(false)
    }

    fun loadCached() {
        runCatching {
            val cached = readAppStorageText(CACHED_FILE_NAME)
            if (!cached.isNullOrBlank()) {
                applyJsonString(cached)
            }
        }
    }

    fun syncInBackground() {
        scope.launch {
            runCatching { syncNow() }
        }
    }

    enum class SyncOutcome { UPDATED, UP_TO_DATE }

    private fun canonicalizeJson(rawJson: String): String = runCatching {
        jsonParser.parseToJsonElement(preprocessJsonWithComments(rawJson)).toString()
    }.getOrDefault("")

    private fun isDefaultNoOpState(): Boolean =
        syncVersion == 1 &&
        globalHijriDayOffset == 0 &&
        hijriMonthDayOffsets.isEmpty() &&
        hijriMonthLengths.isEmpty() &&
        hijriYearMonths.isEmpty() &&
        extraEvents.isEmpty() &&
        removeHolidayDates.isEmpty() &&
        removeEventsOnDates.isEmpty()

    suspend fun syncNow(customUrl: String? = null): Result<SyncOutcome> = withContext(Dispatchers.Default) {
        _isSyncing.value = true
        try {
            val baseUrl = AppUpdateConfig.toRawGitHubUrl(
                if (!customUrl.isNullOrBlank()) customUrl else DEFAULT_CALENDAR_SYNC_URL
            )
            val targetUrl = if (baseUrl.contains("?")) "$baseUrl&t=${System.currentTimeMillis()}"
            else "$baseUrl?t=${System.currentTimeMillis()}"

            val body = httpGetText(targetUrl)
            if (body.isBlank()) {
                return@withContext Result.failure(IllegalStateException("فایل خالی است"))
            }
            val prevCached = readAppStorageText(CACHED_FILE_NAME)
            val prevCanon = prevCached?.let { canonicalizeJson(it) } ?: ""
            val newCanon = canonicalizeJson(body)

            val ok = applyJsonString(body)
            if (!ok) {
                return@withContext Result.failure(IllegalStateException("ساختار JSON معتبر نیست"))
            }

            val wasAlreadySame = if (prevCanon.isNotEmpty()) {
                prevCanon == newCanon
            } else {
                isDefaultNoOpState()
            }

            writeAppStorageText(CACHED_FILE_NAME, body)
            Result.success(if (wasAlreadySame) SyncOutcome.UP_TO_DATE else SyncOutcome.UPDATED)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
