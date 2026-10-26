package ir.shiftkar.neo.data

import ir.shiftkar.neo.engine.*
import ir.shiftkar.neo.platform.readAppStorageText
import ir.shiftkar.neo.platform.writeAppStorageText
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

/**
 * Global app settings — multiplatform port persisted via JSON in the platform's
 * native app storage directory (Android filesDir, iOS NSDocumentDirectory, Desktop user data dir).
 * Includes the THEME_MIGRATION fix in both initial load and update().
 */

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
    val lastUpdateCheck: Long = 0L
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
    val lastUpdateCheck: Long = 0L
)

object SettingsStore {
    private const val FILE_NAME = "shiftkar_settings_v1.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    /* Older builds (PWA) used different theme ids — map them onto the loyal palette everywhere. */
    private val THEME_MIGRATION = mapOf(
        "nebula" to ThemeId.PURPLE,
        "ocean" to ThemeId.TEAL,
        "emerald" to ThemeId.EMERALD,
        "sunset" to ThemeId.ORANGE,
        "rose" to ThemeId.ROSE,
        "gold" to ThemeId.ORANGE
    )

    private fun parseTheme(raw: String): ThemeId =
        THEME_MIGRATION[raw.lowercase()]
            ?: runCatching { ThemeId.valueOf(raw.uppercase()) }.getOrNull()
            ?: ThemeId.BLUE

    private fun loadFromDisk(): AppSettings {
        val today = todayJalaali()
        val raw = runCatching { readAppStorageText(FILE_NAME) }.getOrNull() ?: return AppSettings(
            viewYear = today.jy,
            viewMonth = today.jm
        )
        val dto = runCatching { json.decodeFromString(PersistedSettingsDto.serializer(), raw) }.getOrNull()
            ?: return AppSettings(viewYear = today.jy, viewMonth = today.jm)
        val parsedMyGroup = runCatching { GroupFilter.valueOf(dto.myGroup) }.getOrNull()
            ?.let { if (it == GroupFilter.ALL) GroupFilter.A else it } ?: GroupFilter.A
        return AppSettings(
            theme = parseTheme(dto.theme),
            themeMode = runCatching { ThemeMode.valueOf(dto.themeMode.uppercase()) }.getOrNull() ?: ThemeMode.SYSTEM,
            myGroup = parsedMyGroup,
            filterGroup = runCatching { GroupFilter.valueOf(dto.filterGroup) }.getOrNull() ?: parsedMyGroup,
            viewType = runCatching { ViewType.valueOf(dto.viewType.uppercase()) }.getOrNull() ?: ViewType.GRID,
            calendarSystem = runCatching { CalendarSystem.valueOf(dto.calendarSystem.uppercase()) }.getOrNull() ?: CalendarSystem.JALALI,
            showEvents = dto.showEvents,
            onboardingDone = dto.onboardingDone,
            tourDone = dto.tourDone,
            hapticOn = dto.hapticOn,
            viewYear = dto.viewYear ?: today.jy,
            viewMonth = dto.viewMonth ?: today.jm,
            lastUpdateCheck = dto.lastUpdateCheck
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
            lastUpdateCheck = s.lastUpdateCheck
        )
        runCatching {
            writeAppStorageText(FILE_NAME, json.encodeToString(PersistedSettingsDto.serializer(), dto))
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
            _state.value = next
            withContext(Dispatchers.Default) {
                saveToDisk(next)
            }
        }
    }
}

class SettingsRepository {
    val settings: Flow<AppSettings> get() = SettingsStore.settings
    suspend fun current(): AppSettings = SettingsStore.settings.value
    suspend fun update(transform: (AppSettings) -> AppSettings) = SettingsStore.update(transform)
}
