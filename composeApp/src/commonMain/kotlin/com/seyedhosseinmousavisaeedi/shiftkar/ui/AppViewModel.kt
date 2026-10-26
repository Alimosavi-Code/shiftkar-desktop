package com.seyedhosseinmousavisaeedi.shiftkar.ui

import com.seyedhosseinmousavisaeedi.shiftkar.data.AppSettings
import com.seyedhosseinmousavisaeedi.shiftkar.data.NotesRepository
import com.seyedhosseinmousavisaeedi.shiftkar.data.SettingsRepository
import com.seyedhosseinmousavisaeedi.shiftkar.engine.EventsRepo
import com.seyedhosseinmousavisaeedi.shiftkar.engine.GroupFilter
import com.seyedhosseinmousavisaeedi.shiftkar.engine.TabId
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ThemeId
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ThemeMode
import com.seyedhosseinmousavisaeedi.shiftkar.engine.ViewType
import com.seyedhosseinmousavisaeedi.shiftkar.platform.currentTimeMillis
import com.seyedhosseinmousavisaeedi.shiftkar.platform.readBundledEventsJson
import com.seyedhosseinmousavisaeedi.shiftkar.updater.UpdateState
import com.seyedhosseinmousavisaeedi.shiftkar.updater.Updater
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
