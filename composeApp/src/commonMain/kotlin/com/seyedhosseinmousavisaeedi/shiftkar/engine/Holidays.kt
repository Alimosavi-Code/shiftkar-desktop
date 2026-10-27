package com.seyedhosseinmousavisaeedi.shiftkar.engine

import com.seyedhosseinmousavisaeedi.shiftkar.platform.readBundledEventsJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * Official Iranian events dataset — loaded verbatim from
 * `assets/events.json`, which is the PWA's generated `events-data.ts`
 * (persian-calendar/events, University of Tehran Calendar Center).
 *
 * The holidays engine (`Holidays.kt`) consumes these exactly like the
 * original: recurring Persian/Hijri/Gregorian events + irregular rules
 * (end-of-month, nth/last weekday, single events).
 */

@Serializable
data class EventDto(
    val calendar: String,          // "Persian" | "Hijri" | "Gregorian"
    val title: String,
    val holiday: Boolean,
    val begin: Int? = null,
    val end: Int? = null,
    val month: Int = 1,
    val day: Int = 1
)

@Serializable
data class IrregularDto(
    val calendar: String,
    val title: String,
    val holiday: Boolean,
    val begin: Int? = null,
    val end: Int? = null,
    val rule: String,              // "single event" | "end of month" | "last weekday of month" | "nth weekday of month" | "nth day from"
    val month: Int? = null,
    val day: Int? = null,
    val weekday: Int? = null,
    val nth: Int? = null,
    val offset: Int? = null,
    val year: Int? = null
)

@Serializable
private data class EventsFile(val recurring: List<EventDto>, val irregular: List<IrregularDto>)

object EventsRepo {
    val recurring: List<EventDto> get() = holder.recurring
    val irregular: List<IrregularDto> get() = holder.irregular

    private val holder: EventsFile get() = init ?: EventsFile(emptyList(), emptyList())

    @Volatile
    private var init: EventsFile? = null

    /** v6.1.0: fast-path guard for Holidays.initialize() */
    val isLoaded: Boolean get() = init != null

    val isInitialized: Boolean get() = init != null

    /** Init with the raw events.json content (read from assets/composeResources by the app). */
    fun initWithJson(json: String) {
        if (init == null) {
            init = Json { ignoreUnknownKeys = true }.decodeFromString(EventsFile.serializer(), json)
        }
    }
}

/** Per-day event entry (holidays + occasions). */
data class DayEvent(val title: String, val isHoliday: Boolean)

/**
 * Holiday engine — faithful port of the PWA's `src/lib/holidays.ts`:
 * for a Jalali year, every Persian/Hijri/Gregorian event is mapped to its
 * Jalali date(s) (a Jalali year spans two Hijri years, so e.g. عید فطر can
 * occur twice), filtered by the event's Persian-year range. Results are
 * memoized per Jalali year.
 */
object Holidays {

    const val HOLIDAY_SOURCE_LABEL = "تعطیلات رسمی ایران"

    /* ---------------- calendar helpers ---------------- */

    private fun isGregorianLeap(gy: Int): Boolean =
        (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)

    private fun gregorianMonthLength(gy: Int, gm: Int): Int = when (gm) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isGregorianLeap(gy)) 29 else 28
        else -> 30
    }

    /* Weekday ordinal for a date, in the persian-calendar convention where the
     * data's weekday numbers are 1 = Sunday … 7 = Saturday. weekDayOrdinal()
     * gives 0 = Saturday … 6 = Friday, so a data weekday's ordinal is wd % 7. */
    private fun dataWeekDayOrdinal(wd: Int): Int = wd % 7

    /* Port of Calendar.getNthWeekDayOfMonth — day of month of the nth `weekDay`
     * (1=Sunday…7=Saturday) of the month whose first day has JDN jdnOfDay1. */
    private fun nthWeekDayOfMonth(jdnOfDay1: Long, weekDay: Int, nth: Int): Int {
        val appWd = dataWeekDayOrdinal(weekDay)
        val monthStartWd = weekDayOrdinal(jdnOfDay1)
        return (appWd + 1 - monthStartWd + nth * 7 - (if (monthStartWd <= appWd) 7 else 0)).toInt()
    }

    /* Port of Calendar.getLastWeekDayOfMonth — day of month of the LAST `weekDay`
     * of the month (jdnOfDay1 = first day's JDN, monthLength = days in month). */
    private fun lastWeekDayOfMonth(jdnOfDay1: Long, monthLength: Int, weekDay: Int): Int {
        val appWd = dataWeekDayOrdinal(weekDay)
        return (monthLength - weekDayOrdinal(jdnOfDay1 + monthLength - 1 - appWd)).toInt()
    }

    /* ---------------- irregular rule resolution ---------------- */

    private fun monthLengthOf(calendar: String, year: Int, month: Int): Int = when (calendar) {
        "Hijri" -> islamicMonthLength(year, month)
        "Gregorian" -> gregorianMonthLength(year, month)
        else -> jalaaliMonthLength(year, month)
    }

    private fun calendarJdn(calendar: String, year: Int, month: Int, day: Int): Long = when (calendar) {
        "Hijri" -> islamicToJdn(year, month, day)
        "Gregorian" -> gregorianToJdn(year, month, day)
        else -> jalaliToJdn(year, month, day)
    }

    /** Resolve one irregular event against a year of its own calendar; returns
     *  the JDN of the created date, or null when the rule doesn't apply. */
    private fun resolveRule(e: IrregularDto, year: Int): Long? {
        val month = e.month ?: 1
        val day = e.day ?: 1
        val jdnOf = { m: Int, d: Int -> calendarJdn(e.calendar, year, m, d) }
        val monthLen = { m: Int -> monthLengthOf(e.calendar, year, m) }
        return when (e.rule) {
            "single event" -> if (e.year != year) null else jdnOf(month, day)
            "end of month" -> jdnOf(month, monthLen(month))
            "last weekday of month" -> {
                val len = monthLen(month)
                val d = lastWeekDayOfMonth(jdnOf(month, 1), len, e.weekday ?: 0) + (e.offset ?: 0)
                jdnOf(month, d)
            }
            "nth weekday of month" -> {
                val d = nthWeekDayOfMonth(jdnOf(month, 1), e.weekday ?: 0, e.nth ?: 1)
                jdnOf(month, d)
            }
            "nth day from" -> jdnOf(month, day) + (e.nth ?: 1) - 1
            else -> null
        }
    }

    /* ---------------- per-year resolution ---------------- */

    private val yearCache = mutableMapOf<Int, Map<String, List<DayEvent>>>()

    private fun inRange(jy: Int, e: EventDto): Boolean =
        (e.begin == null || jy >= e.begin) && (e.end == null || jy <= e.end)

    private fun inRange(jy: Int, e: IrregularDto): Boolean =
        (e.begin == null || jy >= e.begin) && (e.end == null || jy <= e.end)

    /** Map of "jm-jd" -> events for one Jalali year (holidays first). */
    @Synchronized
    private fun resolveYear(jy: Int): Map<String, List<DayEvent>> {
        if (!EventsRepo.isInitialized) return emptyMap()
        yearCache[jy]?.let { return it }
        val map = HashMap<String, MutableList<DayEvent>>()

        fun add(jm: Int, jd: Int, title: String, isHoliday: Boolean) {
            if (jm !in 1..12 || jd !in 1..31) return
            val key = "$jm-$jd"
            map.getOrPut(key) { mutableListOf() }.add(DayEvent(title, isHoliday))
        }

        val recurring = EventsRepo.recurring
        val irregular = EventsRepo.irregular

        // Persian (solar) recurring events — fixed Jalali dates.
        for (e in recurring) {
            if (e.calendar != "Persian" || !inRange(jy, e)) continue
            add(e.month, e.day, e.title, e.holiday)
        }

        // Hijri range of this Jalali year (spans two Hijri years).
        val jdnStart = jalaliToJdn(jy, 1, 1)
        val jdnEnd = jalaliToJdn(jy, 12, jalaaliMonthLength(jy, 12))
        val hyStart = jdnToIslamic(jdnStart).hy
        val hyEnd = jdnToIslamic(jdnEnd).hy

        for (hy in hyStart..hyEnd) {
            for (e in recurring) {
                if (e.calendar != "Hijri") continue
                val j = jdnToJalali(islamicToJdn(hy, e.month, e.day))
                if (j.jy != jy || !inRange(jy, e)) continue
                add(j.jm, j.jd, e.title, e.holiday)
            }
        }

        // Gregorian recurring events — every Gregorian year overlapping the Jalali year.
        val gy1 = toGregorian(jy, 1, 1).gy
        val gy2 = toGregorian(jy, 12, jalaaliMonthLength(jy, 12)).gy
        for (gy in gy1..gy2) {
            for (e in recurring) {
                if (e.calendar != "Gregorian") continue
                val j = toJalaali(gy, e.month, e.day)
                if (j.jy != jy || !inRange(jy, e)) continue
                add(j.jm, j.jd, e.title, e.holiday)
            }
        }

        // Irregular events — resolved per year of their own calendar.
        for (e in irregular) {
            if (!inRange(jy, e)) continue
            when (e.calendar) {
                "Hijri" -> for (hy in hyStart..hyEnd) {
                    val r = resolveRule(e, hy) ?: continue
                    val j = jdnToJalali(r)
                    if (j.jy == jy) add(j.jm, j.jd, e.title, e.holiday)
                }
                "Gregorian" -> for (gy in gy1..gy2) {
                    val r = resolveRule(e, gy) ?: continue
                    val j = jdnToJalali(r)
                    if (j.jy == jy) add(j.jm, j.jd, e.title, e.holiday)
                }
                else -> {
                    val r = resolveRule(e, jy) ?: continue
                    val j = jdnToJalali(r)
                    if (j.jy == jy) add(j.jm, j.jd, e.title, e.holiday)
                }
            }
        }

        // Stable display order per day: official holidays first, then by title.
        // Persian alphabet primary order — matches ICU/V8 `localeCompare(…, "fa")`
        // (the JRE Collator(fa) does NOT reorder پ/چ/ژ/گ, so we do it explicitly).
        val result = map.mapValues { (_, list) ->
            list.sortedWith(
                compareByDescending<DayEvent> { it.isHoliday }.thenComparator { a, b -> persianCompare(a.title, b.title) }
            )
        }
        yearCache[jy] = result
        return result
    }

    /**
     * Persian primary-order comparator. Persian alphabet:
     * آ < ا/أ/إ/ء < ب < پ < ت < ث < ج < چ < ح < خ < د < ذ < ر < ز < ژ <
     * س < ش < ص < ض < ط < ظ < ع < غ < ف < ق < ک < گ < ل < م < ن < و/ؤ < ه/ة < ی/ي/ئ/ى
     * ZWNJ is ignorable; space sorts before letters; anything else falls back
     * to code-point order after Persian letters.
     */
    private val FA_RANK: Map<Char, Int> = buildMap {
        val order = "آاأإءبپتثجچحخدذرزژسشصضطظعغفقکگلمنوؤهةیيئى"
        order.forEachIndexed { i, c -> if (!containsKey(c)) put(c, i + 2) }
    }
    private const val RANK_SPACE = 1
    private const val RANK_OTHER_BASE = 10000

    private fun persianCompare(a: String, b: String): Int {
        val xa = a.replace("\u200C", "")
        val xb = b.replace("\u200C", "")
        var i = 0
        var j = 0
        while (i < xa.length || j < xb.length) {
            val ca = if (i < xa.length) xa[i] else ' '
            val cb = if (j < xb.length) xb[j] else ' '
            if (ca == cb) { i++; j++; continue }
            val ra = when {
                ca == ' ' -> RANK_SPACE
                FA_RANK.containsKey(ca) -> FA_RANK.getValue(ca)
                else -> RANK_OTHER_BASE + ca.code
            }
            val rb = when {
                cb == ' ' -> RANK_SPACE
                FA_RANK.containsKey(cb) -> FA_RANK.getValue(cb)
                else -> RANK_OTHER_BASE + cb.code
            }
            if (ra != rb) return ra.compareTo(rb)
            i++; j++
        }
        return 0
    }

    /* ---------------- public API ---------------- */

    /** All events (holidays + occasions) on a Jalali date, holidays first. */
    fun getDayEvents(jy: Int, jm: Int, jd: Int): List<DayEvent> =
        resolveYear(jy)["$jm-$jd"] ?: emptyList()

    /** Merged holiday titles, or null when the date is not an official holiday. */
    fun getHoliday(jy: Int, jm: Int, jd: Int): String? {
        val holidays = getDayEvents(jy, jm, jd).filter { it.isHoliday }
        return if (holidays.isEmpty()) null else holidays.joinToString("، ") { it.title }
    }

    fun isHoliday(jy: Int, jm: Int, jd: Int): Boolean =
        getDayEvents(jy, jm, jd).any { it.isHoliday }
}

/**
 * v6.1.0 — Background bootstrapper for events.json so cold start never blocks the main UI thread.
 */
object HolidaysLoader {
    @Volatile
    var ready: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun kick() {
        if (ready || EventsRepo.isLoaded) {
            ready = true
            return
        }
        scope.launch {
            try {
                if (!EventsRepo.isLoaded) {
                    EventsRepo.initWithJson(readBundledEventsJson())
                }
            } catch (_: Throwable) {
                // Broken dataset falls back gracefully to no official events without crashing
            } finally {
                ready = true
            }
        }
    }
}
