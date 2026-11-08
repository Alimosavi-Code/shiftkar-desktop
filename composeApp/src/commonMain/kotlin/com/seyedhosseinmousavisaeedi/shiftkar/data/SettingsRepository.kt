package com.seyedhosseinmousavisaeedi.shiftkar.data

import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.recoverAppStorageBackup
import com.seyedhosseinmousavisaeedi.shiftkar.platform.writeAppStorageText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Global app settings & persisted domain models (v6.7.3 Desktop & Multiplatform):
 *  - Persists `dayRecords`, `activePatternId`, `customPatterns` (up to 8 groups `A..H`),
 *    `employees` (with `dayLogs`), `desktopWidgetEnabled`, `desktopWidgetAlwaysOnTop`, and `widgetOpacity`.
 *  - Bidirectional JSON parsers (`parseDayRecords`, `parseCustomPatterns`, `parseEmployees`)
 *    accept BOTH Android (`ot`, `sw`, `st`, `offsets`) and Web (`overtimeHours`, `swapKind`, `status`, `groupOffsets`)
 *    backup schemas seamlessly!
 */

data class DayRecord(
    val overtimeHours: Float = 0f,
    val swapKind: ShiftKind? = null
) {
    val isEmpty: Boolean get() = overtimeHours <= 0.01f && swapKind == null
}

enum class EmployeeDayStatus(val fa: String) {
    SCHEDULED("طبق برنامه"),
    LEAVE("مرخصی"),
    SWAP_DAY("جابه‌جایی: صبح‌کار"),
    SWAP_EVENING("جابه‌جایی: عصرکار"),
    SWAP_NIGHT("جابه‌جایی: شب‌کار"),
    SWAP_REST("جابه‌جایی: استراحت")
}

data class EmployeeDayLog(
    val status: EmployeeDayStatus = EmployeeDayStatus.SCHEDULED,
    val overtimeHours: Float = 0f
) {
    val isEmpty: Boolean get() = status == EmployeeDayStatus.SCHEDULED && overtimeHours <= 0.01f
}

data class EmployeeProfile(
    val id: String,
    val name: String,
    val role: String = "",
    val group: ShiftGroup = ShiftGroup.A,
    val patternId: String = "ACTIVE",
    val dayLogs: Map<String, EmployeeDayLog> = emptyMap()
)

data class AppSettings(
    val theme: ThemeId = ThemeId.BLUE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val myGroup: GroupFilter = GroupFilter.A,
    val filterGroup: GroupFilter = GroupFilter.A,
    val viewType: ViewType = ViewType.GRID,
    val calendarSystem: CalendarSystem = CalendarSystem.JALALI,
    val showEvents: Boolean = true,
    val onboardingDone: Boolean = false,
    val tourDone: Boolean = false,
    val hapticOn: Boolean = true,
    val viewYear: Int = todayJalaali().jy,
    val viewMonth: Int = todayJalaali().jm,
    val lastUpdateCheck: Long = 0L,
    val shiftReminderOn: Boolean = false,
    val dayReminderHour: Int = 21,
    val nightReminderHour: Int = 15,
    val noteReminderOn: Boolean = false,
    val widgetOpacity: Int = 100,
    val desktopWidgetEnabled: Boolean = false,
    val desktopWidgetAlwaysOnTop: Boolean = false,
    val desktopWidgetX: Int = -1,
    val desktopWidgetY: Int = -1,
    val dayRecords: Map<String, DayRecord> = emptyMap(),
    val activePatternId: String = ShiftEngine.DEFAULT_PATTERN_ID,
    val customPatterns: List<ShiftPatternSpec> = emptyList(),
    val employees: List<EmployeeProfile> = emptyList(),
    val showEmployeesOnCalendar: Boolean = true
)

@Serializable
private data class PersistedSettingsDto(
    val theme: String = "BLUE",
    val themeMode: String = "SYSTEM",
    val myGroup: String = "A",
    val filterGroup: String = "A",
    val viewType: String = "GRID",
    val calendarSystem: String = "JALALI",
    val showEvents: Boolean = true,
    val onboardingDone: Boolean = false,
    val tourDone: Boolean = false,
    val hapticOn: Boolean = true,
    val viewYear: Int? = null,
    val viewMonth: Int? = null,
    val lastUpdateCheck: Long = 0L,
    val shiftReminderOn: Boolean = false,
    val dayReminderHour: Int = 21,
    val nightReminderHour: Int = 15,
    val noteReminderOn: Boolean = false,
    val widgetOpacity: Int = 100,
    val desktopWidgetEnabled: Boolean = false,
    val desktopWidgetAlwaysOnTop: Boolean = false,
    val desktopWidgetX: Int = -1,
    val desktopWidgetY: Int = -1,
    val dayRecordsJson: String = "{}",
    val activePatternId: String = ShiftEngine.DEFAULT_PATTERN_ID,
    val customPatternsJson: String = "[]",
    val employeesJson: String = "[]",
    val showEmployeesOnCalendar: Boolean = true
)

private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.content
private fun JsonElement?.asIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull
private fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull
private fun JsonElement?.asFloatOrNull(): Float? = (this as? JsonPrimitive)?.floatOrNull
private fun JsonElement?.asBooleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

class SettingsRepository {

    companion object {
        fun parseDayRecords(raw: String?): Map<String, DayRecord> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val root = jsonCodec.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
                parseDayRecordsElement(root)
            }.getOrDefault(emptyMap())
        }

        fun parseDayRecordsElement(root: JsonObject?): Map<String, DayRecord> {
            if (root == null) return emptyMap()
            val out = mutableMapOf<String, DayRecord>()
            for ((k, vEl) in root) {
                val obj = vEl as? JsonObject ?: continue
                // Accept both Android ("ot", "sw") and Web ("overtimeHours", "swapKind") keys
                val ot = (obj["ot"]?.asFloatOrNull() ?: obj["overtimeHours"]?.asFloatOrNull() ?: 0f).coerceIn(0f, 24f)
                val swRaw = (obj["sw"]?.asStringOrNull() ?: obj["swapKind"]?.asStringOrNull()).orEmpty().trim()
                val sw = swRaw.takeIf { it.isNotEmpty() && it != "null" }?.let {
                    runCatching { ShiftKind.valueOf(it.uppercase()) }.getOrNull()
                }
                val rec = DayRecord(overtimeHours = ot, swapKind = sw)
                if (!rec.isEmpty) out[k] = rec
            }
            return out
        }

        fun encodeDayRecordsElement(map: Map<String, DayRecord>): JsonObject = buildJsonObject {
            for ((k, rec) in map) {
                if (rec.isEmpty) continue
                put(
                    k,
                    buildJsonObject {
                        if (rec.overtimeHours > 0.01f) {
                            put("ot", rec.overtimeHours)
                            put("overtimeHours", rec.overtimeHours)
                        }
                        if (rec.swapKind != null) {
                            put("sw", rec.swapKind.name)
                            put("swapKind", rec.swapKind.name)
                        }
                    }
                )
            }
        }

        fun encodeDayRecords(map: Map<String, DayRecord>): String =
            encodeDayRecordsElement(map).toString()

        fun parseCustomPatterns(raw: String?): List<ShiftPatternSpec> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = jsonCodec.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
                parseCustomPatternsElement(arr)
            }.getOrDefault(emptyList())
        }

        fun parseCustomPatternsElement(arr: JsonArray?): List<ShiftPatternSpec> {
            if (arr == null) return emptyList()
            val out = mutableListOf<ShiftPatternSpec>()
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                val id = obj["id"]?.asStringOrNull()?.trim().orEmpty()
                val name = obj["name"]?.asStringOrNull()?.trim().orEmpty()
                if (id.isEmpty() || name.isEmpty()) continue
                val dayC = (obj["dayCount"]?.asIntOrNull() ?: 2).coerceIn(0, 24)
                val eveC = (obj["eveningCount"]?.asIntOrNull() ?: 0).coerceIn(0, 24)
                val nightC = (obj["nightCount"]?.asIntOrNull() ?: 2).coerceIn(0, 24)
                val restC = (obj["restCount"]?.asIntOrNull() ?: 4).coerceIn(1, 24)
                val anchor = obj["anchorJdn"]?.asLongOrNull() ?: ShiftEngine.DEFAULT_BASE_JDN
                val hasGroups = obj["hasGroups"]?.asBooleanOrNull() ?: true
                val groupCount = (obj["groupCount"]?.asIntOrNull() ?: 4).coerceIn(1, ALL_SHIFT_GROUPS.size)

                val lblObj = obj["groupLabels"] as? JsonObject
                val groupLabels = ALL_SHIFT_GROUPS.associateWith { g ->
                    lblObj?.get(g.name)?.asStringOrNull()?.takeIf { it.isNotBlank() } ?: g.name
                }

                val phasesArr = obj["phases"] as? JsonArray
                val parsedPhases = mutableListOf<ShiftPhase>()
                phasesArr?.forEach { pEl ->
                    val pObj = pEl as? JsonObject ?: return@forEach
                    val k = runCatching {
                        ShiftKind.valueOf((pObj["kind"]?.asStringOrNull() ?: "DAY").uppercase())
                    }.getOrDefault(ShiftKind.DAY)
                    val c = (pObj["count"]?.asIntOrNull() ?: 0).coerceIn(0, 24)
                    if (c > 0) parsedPhases.add(ShiftPhase(k, c))
                }

                val total = (if (parsedPhases.isNotEmpty()) parsedPhases.sumOf { it.count } else (dayC + eveC + nightC + restC)).coerceAtLeast(1)
                val step = (total / groupCount.coerceAtLeast(1)).coerceAtLeast(1)
                val offObj = (obj["offsets"] as? JsonObject) ?: (obj["groupOffsets"] as? JsonObject)
                val offsets = ALL_SHIFT_GROUPS.mapIndexed { idx, g ->
                    val defOff = (step * idx) % total
                    g to (offObj?.get(g.name)?.asIntOrNull() ?: defOff)
                }.toMap()

                val sub = obj["subtitle"]?.asStringOrNull()?.takeIf { it.isNotBlank() } ?: buildString {
                    if (parsedPhases.isNotEmpty()) {
                        append(parsedPhases.joinToString(" · ") { "${toPersianDigits(it.count)} روز ${SHIFT_KIND_FA.getValue(it.kind)}" })
                    } else {
                        if (dayC > 0) append("${toPersianDigits(dayC)} روز صبح‌کار · ")
                        if (eveC > 0) append("${toPersianDigits(eveC)} روز عصرکار · ")
                        if (nightC > 0) append("${toPersianDigits(nightC)} روز شب‌کار · ")
                        append("${toPersianDigits(restC)} روز استراحت")
                    }
                    if (!hasGroups) append(" (شیفت فردی)") else append(" (سفارشی)")
                }
                out.add(
                    ShiftPatternSpec(
                        id = id,
                        name = name,
                        subtitle = sub,
                        dayCount = dayC,
                        eveningCount = eveC,
                        nightCount = nightC,
                        restCount = restC,
                        anchorJdn = anchor,
                        groupOffsets = offsets,
                        isWeeklyAdmin = false,
                        isCustom = true,
                        hasGroups = hasGroups,
                        groupCount = groupCount,
                        groupLabels = groupLabels,
                        phases = parsedPhases
                    )
                )
            }
            return out
        }

        fun encodeCustomPatternsElement(list: List<ShiftPatternSpec>): JsonArray = buildJsonArray {
            for (p in list) {
                val offObj = buildJsonObject {
                    ALL_SHIFT_GROUPS.forEach { g -> put(g.name, p.groupOffsets[g] ?: 0) }
                }
                val lblObj = buildJsonObject {
                    ALL_SHIFT_GROUPS.forEach { g -> put(g.name, p.groupLabels[g] ?: g.name) }
                }
                val phasesArr = buildJsonArray {
                    p.effectivePhases.forEach { ph ->
                        add(buildJsonObject {
                            put("kind", ph.kind.name)
                            put("count", ph.count)
                        })
                    }
                }
                add(
                    buildJsonObject {
                        put("id", p.id)
                        put("name", p.name)
                        put("subtitle", p.subtitle)
                        put("dayCount", p.dayCount)
                        put("eveningCount", p.eveningCount)
                        put("nightCount", p.nightCount)
                        put("restCount", p.restCount)
                        put("anchorJdn", p.anchorJdn)
                        put("hasGroups", p.hasGroups)
                        put("groupCount", p.groupCount)
                        put("groupLabels", lblObj)
                        put("phases", phasesArr)
                        put("offsets", offObj)
                        put("groupOffsets", offObj)
                    }
                )
            }
        }

        fun encodeCustomPatterns(list: List<ShiftPatternSpec>): String =
            encodeCustomPatternsElement(list).toString()

        private fun parseEmployeeDayStatus(raw: String): EmployeeDayStatus = when (raw.uppercase()) {
            "LEAVE" -> EmployeeDayStatus.LEAVE
            "SWAP_DAY", "DAY" -> EmployeeDayStatus.SWAP_DAY
            "SWAP_EVENING", "EVENING" -> EmployeeDayStatus.SWAP_EVENING
            "SWAP_NIGHT", "NIGHT" -> EmployeeDayStatus.SWAP_NIGHT
            "SWAP_REST", "REST" -> EmployeeDayStatus.SWAP_REST
            else -> EmployeeDayStatus.SCHEDULED
        }

        private fun employeeStatusToWebString(st: EmployeeDayStatus): String = when (st) {
            EmployeeDayStatus.SCHEDULED -> "AS_SCHEDULED"
            EmployeeDayStatus.LEAVE -> "LEAVE"
            EmployeeDayStatus.SWAP_DAY -> "DAY"
            EmployeeDayStatus.SWAP_EVENING -> "EVENING"
            EmployeeDayStatus.SWAP_NIGHT -> "NIGHT"
            EmployeeDayStatus.SWAP_REST -> "REST"
        }

        fun parseEmployees(raw: String?): List<EmployeeProfile> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = jsonCodec.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
                parseEmployeesElement(arr)
            }.getOrDefault(emptyList())
        }

        fun parseEmployeesElement(arr: JsonArray?): List<EmployeeProfile> {
            if (arr == null) return emptyList()
            val out = mutableListOf<EmployeeProfile>()
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                val id = obj["id"]?.asStringOrNull()?.trim().orEmpty()
                val name = obj["name"]?.asStringOrNull()?.trim().orEmpty()
                if (id.isEmpty() || name.isEmpty()) continue
                val role = obj["role"]?.asStringOrNull()?.trim().orEmpty()
                val grp = runCatching {
                    ShiftGroup.valueOf((obj["group"]?.asStringOrNull() ?: "A").uppercase())
                }.getOrDefault(ShiftGroup.A)
                val patId = obj["patternId"]?.asStringOrNull()?.ifBlank { "ACTIVE" } ?: "ACTIVE"
                val logsObj = obj["dayLogs"] as? JsonObject
                val logs = mutableMapOf<String, EmployeeDayLog>()
                logsObj?.forEach { (k, lEl) ->
                    val lObj = lEl as? JsonObject ?: return@forEach
                    val stRaw = lObj["st"]?.asStringOrNull() ?: lObj["status"]?.asStringOrNull() ?: "SCHEDULED"
                    val st = parseEmployeeDayStatus(stRaw)
                    val ot = (lObj["ot"]?.asFloatOrNull() ?: lObj["overtimeHours"]?.asFloatOrNull() ?: 0f).coerceIn(0f, 24f)
                    val log = EmployeeDayLog(status = st, overtimeHours = ot)
                    if (!log.isEmpty) logs[k] = log
                }
                out.add(EmployeeProfile(id = id, name = name, role = role, group = grp, patternId = patId, dayLogs = logs))
            }
            return out
        }

        fun encodeEmployeesElement(list: List<EmployeeProfile>): JsonArray = buildJsonArray {
            for (emp in list) {
                val logsObj = buildJsonObject {
                    for ((k, l) in emp.dayLogs) {
                        if (l.isEmpty) continue
                        put(
                            k,
                            buildJsonObject {
                                if (l.status != EmployeeDayStatus.SCHEDULED) {
                                    put("st", l.status.name)
                                    put("status", employeeStatusToWebString(l.status))
                                }
                                if (l.overtimeHours > 0.01f) {
                                    put("ot", l.overtimeHours)
                                    put("overtimeHours", l.overtimeHours)
                                }
                            }
                        )
                    }
                }
                add(
                    buildJsonObject {
                        put("id", emp.id)
                        put("name", emp.name)
                        put("role", emp.role)
                        put("group", emp.group.name)
                        put("patternId", emp.patternId)
                        put("dayLogs", logsObj)
                    }
                )
            }
        }

        fun encodeEmployees(list: List<EmployeeProfile>): String =
            encodeEmployeesElement(list).toString()
    }

    val settings: Flow<AppSettings> get() = SettingsStore.settings
    suspend fun current(): AppSettings = SettingsStore.settings.value
    suspend fun update(transform: (AppSettings) -> AppSettings) = SettingsStore.update(transform)
}

object SettingsStore {
    private const val FILE_NAME = "shiftkar_settings_v1.json"
    private val mutex = Mutex()

    private val THEME_MIGRATION = mapOf(
        "nebula" to ThemeId.PURPLE,
        "ocean" to ThemeId.TEAL,
        "emerald" to ThemeId.EMERALD,
        "sunset" to ThemeId.ORANGE,
        "rose" to ThemeId.ROSE,
        "gold" to ThemeId.ORANGE
    )

    fun parseTheme(raw: String): ThemeId =
        THEME_MIGRATION[raw.lowercase()]
            ?: runCatching { ThemeId.valueOf(raw.uppercase()) }.getOrNull()
            ?: ThemeId.BLUE

    private fun loadFromDisk(): AppSettings {
        val today = todayJalaali()
        val raw = runCatching { readAppStorageText(FILE_NAME) }.getOrNull() ?: return AppSettings(
            viewYear = today.jy,
            viewMonth = today.jm
        )
        val dto = runCatching { jsonCodec.decodeFromString(PersistedSettingsDto.serializer(), raw) }.getOrElse {
            val backupRaw = runCatching { recoverAppStorageBackup(FILE_NAME) }.getOrNull()
            backupRaw?.let { runCatching { jsonCodec.decodeFromString(PersistedSettingsDto.serializer(), it) }.getOrNull() }
        } ?: return AppSettings(viewYear = today.jy, viewMonth = today.jm)

        val actPatId = dto.activePatternId.ifBlank { ShiftEngine.DEFAULT_PATTERN_ID }
        val custPats = SettingsRepository.parseCustomPatterns(dto.customPatternsJson)
        val resolvedPat = ShiftEngine.resolvePattern(actPatId, custPats)
        ShiftEngine.setActivePattern(resolvedPat)
        val validGroups = ShiftEngine.activeGroups

        val rawMyGrp = runCatching { GroupFilter.valueOf(dto.myGroup.uppercase()) }.getOrNull()
            ?.let { if (it == GroupFilter.ALL) GroupFilter.A else it } ?: GroupFilter.A
        val safeMyGrp = if (rawMyGrp.toShiftGroup() in validGroups) rawMyGrp else GroupFilter.A

        val rawFilterGrp = runCatching { GroupFilter.valueOf(dto.filterGroup.uppercase()) }.getOrNull() ?: safeMyGrp
        val safeFilterGrp = if (rawFilterGrp == GroupFilter.ALL || rawFilterGrp.toShiftGroup() in validGroups) rawFilterGrp else safeMyGrp

        return AppSettings(
            theme = parseTheme(dto.theme),
            themeMode = runCatching { ThemeMode.valueOf(dto.themeMode.uppercase()) }.getOrNull() ?: ThemeMode.SYSTEM,
            myGroup = safeMyGrp,
            filterGroup = safeFilterGrp,
            viewType = runCatching { ViewType.valueOf(dto.viewType.uppercase()) }.getOrNull() ?: ViewType.GRID,
            calendarSystem = runCatching { CalendarSystem.valueOf(dto.calendarSystem.uppercase()) }.getOrNull() ?: CalendarSystem.JALALI,
            showEvents = dto.showEvents,
            onboardingDone = dto.onboardingDone,
            tourDone = dto.tourDone,
            hapticOn = dto.hapticOn,
            viewYear = today.jy,
            viewMonth = today.jm,
            lastUpdateCheck = dto.lastUpdateCheck,
            shiftReminderOn = dto.shiftReminderOn,
            dayReminderHour = dto.dayReminderHour.coerceIn(0, 23),
            nightReminderHour = dto.nightReminderHour.coerceIn(0, 23),
            noteReminderOn = dto.noteReminderOn,
            widgetOpacity = dto.widgetOpacity.let { if (it in listOf(50, 75, 100)) it else 100 },
            desktopWidgetEnabled = dto.desktopWidgetEnabled,
            desktopWidgetAlwaysOnTop = dto.desktopWidgetAlwaysOnTop,
            desktopWidgetX = dto.desktopWidgetX,
            desktopWidgetY = dto.desktopWidgetY,
            dayRecords = SettingsRepository.parseDayRecords(dto.dayRecordsJson),
            activePatternId = actPatId,
            customPatterns = custPats,
            employees = SettingsRepository.parseEmployees(dto.employeesJson),
            showEmployeesOnCalendar = dto.showEmployeesOnCalendar
        )
    }

    private fun saveToDisk(s: AppSettings) {
        val dto = PersistedSettingsDto(
            theme = s.theme.name,
            themeMode = s.themeMode.name,
            myGroup = s.myGroup.name,
            filterGroup = s.filterGroup.name,
            viewType = s.viewType.name,
            calendarSystem = s.calendarSystem.name,
            showEvents = s.showEvents,
            onboardingDone = s.onboardingDone,
            tourDone = s.tourDone,
            hapticOn = s.hapticOn,
            viewYear = s.viewYear,
            viewMonth = s.viewMonth,
            lastUpdateCheck = s.lastUpdateCheck,
            shiftReminderOn = s.shiftReminderOn,
            dayReminderHour = s.dayReminderHour,
            nightReminderHour = s.nightReminderHour,
            noteReminderOn = s.noteReminderOn,
            widgetOpacity = s.widgetOpacity,
            desktopWidgetEnabled = s.desktopWidgetEnabled,
            desktopWidgetAlwaysOnTop = s.desktopWidgetAlwaysOnTop,
            desktopWidgetX = s.desktopWidgetX,
            desktopWidgetY = s.desktopWidgetY,
            dayRecordsJson = SettingsRepository.encodeDayRecords(s.dayRecords),
            activePatternId = s.activePatternId,
            customPatternsJson = SettingsRepository.encodeCustomPatterns(s.customPatterns),
            employeesJson = SettingsRepository.encodeEmployees(s.employees),
            showEmployeesOnCalendar = s.showEmployeesOnCalendar
        )
        runCatching {
            writeAppStorageText(FILE_NAME, jsonCodec.encodeToString(PersistedSettingsDto.serializer(), dto))
        }
    }

    private val _state = MutableStateFlow(loadFromDisk())
    val settings: StateFlow<AppSettings> = _state.asStateFlow()

    fun reload() {
        _state.value = loadFromDisk()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        mutex.withLock {
            val next = transform(_state.value)
            ShiftEngine.setActivePattern(ShiftEngine.resolvePattern(next.activePatternId, next.customPatterns))
            _state.value = next
            withContext(Dispatchers.Default) {
                saveToDisk(next)
            }
        }
    }
}
