package com.seyedhosseinmousavisaeedi.shiftkar.ui

import com.seyedhosseinmousavisaeedi.shiftkar.data.*
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.platform.currentTimeMillis
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
 * Global desktop/multiplatform app state (v6.7.3) — zero AndroidX Lifecycle dependency required.
 * Synchronizes settings, notes, custom shift patterns, overtime/swap dayRecords,
 * personnel profiles, and Desktop Mini-Widget state.
 */
class AppViewModel {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settingsRepo = SettingsRepository()
    val notesRepo = NotesRepository()

    init {
        HolidaysLoader.kick()
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /* ---------------- settings mutations ---------------- */

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
    fun setShowEmployeesOnCalendar(on: Boolean) = set { it.copy(showEmployeesOnCalendar = on) }
    fun setViewMonth(year: Int, month: Int) = set { it.copy(viewYear = year, viewMonth = month) }
    fun markUpdateChecked() = set { it.copy(lastUpdateCheck = currentTimeMillis()) }

    /* ---------------- desktop widget & reminder settings ---------------- */

    fun setShiftReminderOn(on: Boolean) = set { it.copy(shiftReminderOn = on) }
    fun setDayReminderHour(hour: Int) = set { it.copy(dayReminderHour = hour.coerceIn(0, 23)) }
    fun setNightReminderHour(hour: Int) = set { it.copy(nightReminderHour = hour.coerceIn(0, 23)) }
    fun setNoteReminderOn(on: Boolean) = set { it.copy(noteReminderOn = on) }
    fun setWidgetOpacity(opacity: Int) = set { it.copy(widgetOpacity = opacity) }
    fun setDesktopWidgetEnabled(enabled: Boolean) = set { it.copy(desktopWidgetEnabled = enabled) }
    fun setDesktopWidgetAlwaysOnTop(pin: Boolean) = set { it.copy(desktopWidgetAlwaysOnTop = pin) }
    fun setDesktopWidgetPosition(x: Int, y: Int) = set { it.copy(desktopWidgetX = x, desktopWidgetY = y) }

    /* ---------------- overtime & shift swap records ---------------- */

    fun saveDayRecord(dateKey: String, overtimeHours: Float, swapKind: ShiftKind?) {
        set { cur ->
            val nextMap = cur.dayRecords.toMutableMap()
            val rec = DayRecord(overtimeHours = overtimeHours.coerceIn(0f, 24f), swapKind = swapKind)
            if (rec.isEmpty) {
                nextMap.remove(dateKey)
            } else {
                nextMap[dateKey] = rec
            }
            cur.copy(dayRecords = nextMap)
        }
    }

    fun mergeImportedDayRecords(imported: Map<String, DayRecord>) {
        if (imported.isEmpty()) return
        set { cur ->
            val nextMap = cur.dayRecords.toMutableMap()
            for ((k, v) in imported) {
                if (!v.isEmpty) nextMap[k] = v
            }
            cur.copy(dayRecords = nextMap)
        }
    }

    fun mergeImportedBundle(bundle: ImportedBackupBundle) {
        set { cur ->
            val nextRecs = cur.dayRecords.toMutableMap()
            for ((k, v) in bundle.dayRecords) {
                if (!v.isEmpty) nextRecs[k] = v
            }
            val nextPats = (cur.customPatterns.associateBy { it.id } + bundle.customPatterns.associateBy { it.id }).values.toList()
            val nextEmps = (cur.employees.associateBy { it.id } + bundle.employees.associateBy { it.id }).values.toList()
            val nextAct = bundle.activePatternId ?: cur.activePatternId
            ShiftEngine.setActivePattern(ShiftEngine.resolvePattern(nextAct, nextPats))
            val patch = bundle.settingsPatch
            cur.copy(
                dayRecords = nextRecs,
                customPatterns = nextPats,
                employees = nextEmps,
                activePatternId = nextAct,
                myGroup = patch?.myGroup?.let { if (it == GroupFilter.ALL) GroupFilter.A else it } ?: cur.myGroup,
                filterGroup = patch?.filterGroup ?: cur.filterGroup,
                theme = patch?.theme ?: cur.theme,
                themeMode = patch?.themeMode ?: cur.themeMode,
                viewType = patch?.viewType ?: cur.viewType,
                showEvents = patch?.showEvents ?: cur.showEvents,
                hapticOn = patch?.hapticOn ?: cur.hapticOn,
                showEmployeesOnCalendar = patch?.showEmployeesOnCalendar ?: cur.showEmployeesOnCalendar
            )
        }
    }

    /* ---------------- shift pattern management ---------------- */

    fun setActivePattern(patternId: String) {
        set { cur ->
            val pat = ShiftEngine.resolvePattern(patternId, cur.customPatterns)
            ShiftEngine.setActivePattern(pat)
            cur.copy(activePatternId = patternId)
        }
    }

    fun saveCustomPattern(spec: ShiftPatternSpec, activateImmediately: Boolean = true) {
        set { cur ->
            val updatedList = cur.customPatterns.filterNot { it.id == spec.id } + spec
            val nextAct = if (activateImmediately) spec.id else cur.activePatternId
            ShiftEngine.setActivePattern(ShiftEngine.resolvePattern(nextAct, updatedList))
            cur.copy(customPatterns = updatedList, activePatternId = nextAct)
        }
    }

    fun deleteCustomPattern(patternId: String) {
        set { cur ->
            val updatedList = cur.customPatterns.filterNot { it.id == patternId }
            val nextAct = if (cur.activePatternId == patternId && !ShiftEngine.isBuiltInPreset(patternId)) {
                ShiftEngine.DEFAULT_PATTERN_ID
            } else {
                cur.activePatternId
            }
            ShiftEngine.setActivePattern(ShiftEngine.resolvePattern(nextAct, updatedList))
            cur.copy(customPatterns = updatedList, activePatternId = nextAct)
        }
    }

    fun restorePresetToDefault(patternId: String) {
        set { cur ->
            val updatedList = cur.customPatterns.filterNot { it.id == patternId }
            ShiftEngine.setActivePattern(ShiftEngine.resolvePattern(cur.activePatternId, updatedList))
            cur.copy(customPatterns = updatedList)
        }
    }

    fun restoreAllPresetsToDefault() {
        set { cur ->
            val presetIds = ShiftEngine.PRESET_PATTERNS.map { it.id }.toSet()
            val updatedList = cur.customPatterns.filterNot { it.id in presetIds }
            ShiftEngine.setActivePattern(ShiftEngine.resolvePattern(cur.activePatternId, updatedList))
            cur.copy(customPatterns = updatedList)
        }
    }

    /* ---------------- employee profiles management ---------------- */

    fun saveEmployee(profile: EmployeeProfile) {
        set { cur ->
            val idx = cur.employees.indexOfFirst { it.id == profile.id }
            val nextList = if (idx >= 0) {
                cur.employees.toMutableList().apply { this[idx] = profile }
            } else {
                cur.employees + profile
            }
            cur.copy(employees = nextList)
        }
    }

    fun deleteEmployee(employeeId: String) {
        set { cur ->
            cur.copy(employees = cur.employees.filterNot { it.id == employeeId })
        }
    }

    fun saveEmployeeDayLog(employeeId: String, dateKey: String, status: EmployeeDayStatus, overtimeHours: Float) {
        set { cur ->
            val nextEmps = cur.employees.map { emp ->
                if (emp.id != employeeId) emp else {
                    val nextLogs = emp.dayLogs.toMutableMap()
                    val log = EmployeeDayLog(status = status, overtimeHours = overtimeHours.coerceIn(0f, 24f))
                    if (log.isEmpty) nextLogs.remove(dateKey) else nextLogs[dateKey] = log
                    emp.copy(dayLogs = nextLogs)
                }
            }
            cur.copy(employees = nextEmps)
        }
    }

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
