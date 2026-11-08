package com.seyedhosseinmousavisaeedi.shiftkar.data

import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.platform.currentTimeMillis
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readAppStorageText
import com.seyedhosseinmousavisaeedi.shiftkar.platform.recoverAppStorageBackup
import com.seyedhosseinmousavisaeedi.shiftkar.platform.writeAppStorageText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Notes & Unified Cross-Platform Backup persistence (v6.7.3 Desktop & Multiplatform).
 * Exports and imports `.json` backups 100% compatible with BOTH:
 *  - Android ShiftKar (`v6.7.x`)
 *  - ShiftKar Web PWA (`v6.7.x`)
 *  - ShiftKar Desktop (`v6.2+`)
 */

@Serializable
data class NoteEntity(
    val dateKey: String,   // "1405-05-04"
    val text: String,
    val updatedAt: Long
)

data class ImportedSettingsPatch(
    val myGroup: GroupFilter? = null,
    val filterGroup: GroupFilter? = null,
    val theme: ThemeId? = null,
    val themeMode: ThemeMode? = null,
    val viewType: ViewType? = null,
    val showEvents: Boolean? = null,
    val hapticOn: Boolean? = null,
    val showEmployeesOnCalendar: Boolean? = null
)

data class ImportedBackupBundle(
    val totalRestoredCount: Int,
    val dayRecords: Map<String, DayRecord>,
    val customPatterns: List<ShiftPatternSpec>,
    val employees: List<EmployeeProfile>,
    val activePatternId: String?,
    val settingsPatch: ImportedSettingsPatch? = null
)

object NotesStore {
    private const val FILE_NAME = "shiftkar_notes_v1.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val mutex = Mutex()

    private fun loadFromDisk(): List<NoteEntity> {
        val raw = runCatching { readAppStorageText(FILE_NAME) }.getOrNull() ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(NoteEntity.serializer()), raw)
                .sortedByDescending { it.updatedAt }
        }.getOrElse {
            val backupRaw = runCatching { recoverAppStorageBackup(FILE_NAME) }.getOrNull() ?: return emptyList()
            runCatching {
                json.decodeFromString(ListSerializer(NoteEntity.serializer()), backupRaw)
                    .sortedByDescending { it.updatedAt }
            }.getOrDefault(emptyList())
        }
    }

    private fun saveToDisk(notes: List<NoteEntity>) {
        runCatching {
            writeAppStorageText(
                FILE_NAME,
                json.encodeToString(ListSerializer(NoteEntity.serializer()), notes)
            )
        }
    }

    private val _notes = MutableStateFlow(loadFromDisk())
    val notesFlow: Flow<List<NoteEntity>> = _notes.asStateFlow()

    fun reload() {
        _notes.value = loadFromDisk()
    }

    suspend fun listAll(): List<NoteEntity> = _notes.value

    suspend fun listMonth(jy: Int, jm: Int): List<NoteEntity> {
        val mm = jm.toString().padStart(2, '0')
        val from = "$jy-$mm-01"
        val to = "$jy-$mm-31"
        return _notes.value.filter { it.dateKey in from..to }.sortedByDescending { it.dateKey }
    }

    suspend fun get(dateKey: String): NoteEntity? =
        _notes.value.firstOrNull { it.dateKey == dateKey }

    suspend fun save(dateKey: String, text: String) {
        mutex.withLock {
            val trimmed = text.trim()
            val updated = if (trimmed.isEmpty()) {
                _notes.value.filterNot { it.dateKey == dateKey }
            } else {
                val entry = NoteEntity(dateKey = dateKey, text = text, updatedAt = currentTimeMillis())
                (_notes.value.filterNot { it.dateKey == dateKey } + entry).sortedByDescending { it.updatedAt }
            }
            _notes.value = updated
            withContext(Dispatchers.Default) {
                saveToDisk(updated)
            }
        }
    }

    suspend fun exportJson(
        dayRecords: Map<String, DayRecord> = emptyMap(),
        customPatterns: List<ShiftPatternSpec> = emptyList(),
        employees: List<EmployeeProfile> = emptyList(),
        activePatternId: String? = null,
        settings: AppSettings? = null
    ): String {
        val all = _notes.value
        val notesArr = buildJsonArray {
            for (n in all) {
                add(
                    buildJsonObject {
                        put("dateKey", n.dateKey)
                        put("text", n.text)
                        put("updatedAt", n.updatedAt)
                    }
                )
            }
        }
        val recordsObj = SettingsRepository.encodeDayRecordsElement(dayRecords)
        val patternsArr = SettingsRepository.encodeCustomPatternsElement(customPatterns)
        val employeesArr = SettingsRepository.encodeEmployeesElement(employees)

        val root = buildJsonObject {
            put("app", "ShiftKar")
            put("version", 4)
            put("exportedAt", currentTimeMillis())
            put("notes", notesArr)
            put("dayRecords", recordsObj)
            put("customPatterns", patternsArr)
            put("employees", employeesArr)
            if (!activePatternId.isNullOrBlank()) {
                put("activePatternId", activePatternId)
            }
            if (settings != null) {
                put(
                    "settings",
                    buildJsonObject {
                        put("myGroup", settings.myGroup.name)
                        put("filterGroup", settings.filterGroup.name)
                        put("theme", settings.theme.name.lowercase())
                        put("themeMode", settings.themeMode.name.lowercase())
                        put("viewType", settings.viewType.name.lowercase())
                        put("showEvents", settings.showEvents)
                        put("hapticOn", settings.hapticOn)
                        put("activePatternId", settings.activePatternId)
                        put("showEmployeesOnCalendar", settings.showEmployeesOnCalendar)
                    }
                )
            }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    suspend fun importFullBackup(rawJson: String): ImportedBackupBundle {
        val trimmed = rawJson.trim().removePrefix("\uFEFF")
        val elem = json.parseToJsonElement(trimmed)
        val rootObj = elem as? JsonObject
        val notesArr: JsonArray = when {
            elem is JsonArray -> elem
            rootObj != null -> (rootObj["notes"] as? JsonArray) ?: (rootObj["items"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        val now = currentTimeMillis()
        val parsedNotes = mutableListOf<NoteEntity>()
        for (itemEl in notesArr) {
            val obj = itemEl as? JsonObject ?: continue
            val dateKey = (obj["dateKey"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val text = (obj["text"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val updatedAt = (obj["updatedAt"] as? JsonPrimitive)?.longOrNull ?: now
            if (dateKey.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) && text.isNotEmpty()) {
                parsedNotes.add(NoteEntity(dateKey = dateKey, text = text.take(500), updatedAt = updatedAt))
            }
        }

        if (parsedNotes.isNotEmpty()) {
            mutex.withLock {
                val mergedMap = _notes.value.associateBy { it.dateKey }.toMutableMap()
                for (item in parsedNotes) {
                    val existing = mergedMap[item.dateKey]
                    if (existing == null || item.updatedAt >= existing.updatedAt) {
                        mergedMap[item.dateKey] = item
                    }
                }
                val mergedList = mergedMap.values.sortedByDescending { it.updatedAt }
                _notes.value = mergedList
                withContext(Dispatchers.Default) {
                    saveToDisk(mergedList)
                }
            }
        }

        val settingsObj = rootObj?.get("settings") as? JsonObject
        val recordsObj = (rootObj?.get("dayRecords") as? JsonObject) ?: (settingsObj?.get("dayRecords") as? JsonObject)
        val patternsArr = (rootObj?.get("customPatterns") as? JsonArray) ?: (settingsObj?.get("customPatterns") as? JsonArray)
        val employeesArr = (rootObj?.get("employees") as? JsonArray) ?: (settingsObj?.get("employees") as? JsonArray)

        val importedRecords = SettingsRepository.parseDayRecordsElement(recordsObj)
        val importedPatterns = SettingsRepository.parseCustomPatternsElement(patternsArr)
        val importedEmployees = SettingsRepository.parseEmployeesElement(employeesArr)

        val actPat = ((rootObj?.get("activePatternId") as? JsonPrimitive)?.content
            ?: (settingsObj?.get("activePatternId") as? JsonPrimitive)?.content)
            ?.trim()?.takeIf { it.isNotEmpty() }

        val patch = settingsObj?.let { sObj ->
            ImportedSettingsPatch(
                myGroup = (sObj["myGroup"] as? JsonPrimitive)?.content?.let {
                    runCatching { GroupFilter.valueOf(it.uppercase()) }.getOrNull()
                },
                filterGroup = (sObj["filterGroup"] as? JsonPrimitive)?.content?.let {
                    runCatching { GroupFilter.valueOf(it.uppercase()) }.getOrNull()
                },
                theme = (sObj["theme"] as? JsonPrimitive)?.content?.let {
                    SettingsStore.parseTheme(it)
                },
                themeMode = (sObj["themeMode"] as? JsonPrimitive)?.content?.let {
                    runCatching { ThemeMode.valueOf(it.uppercase()) }.getOrNull()
                },
                viewType = (sObj["viewType"] as? JsonPrimitive)?.content?.let {
                    runCatching { ViewType.valueOf(it.uppercase()) }.getOrNull()
                },
                showEvents = (sObj["showEvents"] as? JsonPrimitive)?.booleanOrNull,
                hapticOn = (sObj["hapticOn"] as? JsonPrimitive)?.booleanOrNull,
                showEmployeesOnCalendar = (sObj["showEmployeesOnCalendar"] as? JsonPrimitive)?.booleanOrNull
            )
        }

        val settingsRestored = if (patch != null) 1 else 0
        val patActRestored = if (!actPat.isNullOrBlank()) 1 else 0

        return ImportedBackupBundle(
            totalRestoredCount = parsedNotes.size + importedRecords.size + importedEmployees.size + importedPatterns.size + settingsRestored + patActRestored,
            dayRecords = importedRecords,
            customPatterns = importedPatterns,
            employees = importedEmployees,
            activePatternId = actPat,
            settingsPatch = patch
        )
    }
}

class NotesRepository {
    fun observeAll(): Flow<List<NoteEntity>> = NotesStore.notesFlow
    suspend fun listAll(): List<NoteEntity> = NotesStore.listAll()
    suspend fun listMonth(jy: Int, jm: Int): List<NoteEntity> = NotesStore.listMonth(jy, jm)
    suspend fun get(dateKey: String): NoteEntity? = NotesStore.get(dateKey)
    suspend fun save(dateKey: String, text: String) = NotesStore.save(dateKey, text)

    suspend fun exportJson(
        dayRecords: Map<String, DayRecord> = emptyMap(),
        customPatterns: List<ShiftPatternSpec> = emptyList(),
        employees: List<EmployeeProfile> = emptyList(),
        activePatternId: String? = null,
        settings: AppSettings? = null
    ): String = NotesStore.exportJson(dayRecords, customPatterns, employees, activePatternId, settings)

    suspend fun importJson(raw: String): Int = NotesStore.importFullBackup(raw).totalRestoredCount
    suspend fun importFullBackup(raw: String): ImportedBackupBundle = NotesStore.importFullBackup(raw)
}
