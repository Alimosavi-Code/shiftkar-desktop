package ir.shiftkar.neo.ui

import ir.shiftkar.neo.data.AppSettings
import ir.shiftkar.neo.data.NotesRepository
import ir.shiftkar.neo.data.SettingsRepository
import ir.shiftkar.neo.engine.EventsRepo
import ir.shiftkar.neo.engine.GroupFilter
import ir.shiftkar.neo.engine.TabId
import ir.shiftkar.neo.engine.ThemeId
import ir.shiftkar.neo.engine.ThemeMode
import ir.shiftkar.neo.engine.ViewType
import ir.shiftkar.neo.platform.currentTimeMillis
import ir.shiftkar.neo.platform.readBundledEventsJson
import ir.shiftkar.neo.updater.UpdateState
import ir.shiftkar.neo.updater.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Global desktop/multiplatform app state — zero AndroidX Lifecycle dependency required.
 */
class AppViewModel {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settingsRepo = SettingsRepository()
    val notesRepo = NotesRepository()

    init {
        if (!EventsRepo.isInitialized) {
            runCatching {
                EventsRepo.initWithJson(readBundledEventsJson())
            }
        }
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun set(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            runCatching { settingsRepo.update(transform) }
        }
    }

    fun setTab(tab: TabId) = set { it.copy() }
    fun setTheme(theme: ThemeId) = set { it.copy(theme = theme) }
    fun setThemeMode(mode: ThemeMode) = set { it.copy(themeMode = mode) }
    fun setMyGroup(group: GroupFilter) = set { it.copy(myGroup = group) }
    fun setFilterGroup(group: GroupFilter) = set { it.copy(filterGroup = group) }
    fun setViewType(type: ViewType) = set { it.copy(viewType = type) }
    fun setOnboardingDone() = set { it.copy(onboardingDone = true) }
    fun setTourDone() = set { it.copy(tourDone = true) }
    fun restartOnboarding() = set { it.copy(onboardingDone = false, tourDone = true) }
    fun setHaptic(on: Boolean) = set { it.copy(hapticOn = on) }
    fun setShowEvents(on: Boolean) = set { it.copy(showEvents = on) }
    fun setViewMonth(year: Int, month: Int) = set { it.copy(viewYear = year, viewMonth = month) }
    fun markUpdateChecked() = set { it.copy(lastUpdateCheck = currentTimeMillis()) }

    /* ---------------- notes ---------------- */

    val notesFlow: StateFlow<Map<String, String>> = notesRepo.observeAll()
        .map { list -> list.associate { it.dateKey to it.text } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun saveNote(dateKey: String, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                notesRepo.save(dateKey, text)
                onDone()
            }
        }
    }

    /* ---------------- updater ---------------- */

    val updateState: StateFlow<UpdateState> = Updater.state

    fun checkForUpdates() {
        viewModelScope.launch {
            runCatching {
                Updater.check()
                markUpdateChecked()
            }
        }
    }

    fun downloadUpdateInBackground() {
        runCatching { Updater.startBackgroundDownload() }
    }

    fun installUpdate() {
        runCatching { Updater.install() }
    }
}
