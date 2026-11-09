package com.seyedhosseinmousavisaeedi.shiftkar.engine

import kotlin.concurrent.Volatile

/**
 * Shift calculation engine (v6.5.4):
 *  - Supports Morning ( صبح‌کار / M1..M24 ), Evening ( عصرکار / E1..E24 ),
 *    Night ( شب‌کار / N1..N24 ), and Rest ( استراحت / R1..R24 ).
 *  - Supports dynamic number of shift groups (`groupCount` 1..8, e.g. removing C/D for 2 groups,
 *    or adding E/F/G/H for 5..8 groups, or `hasGroups = false` for individual shifts).
 */

enum class ShiftCode(val kind: ShiftKind, val ordinalIndex: Int) {
    M1(ShiftKind.DAY, 1), M2(ShiftKind.DAY, 2), M3(ShiftKind.DAY, 3), M4(ShiftKind.DAY, 4),
    M5(ShiftKind.DAY, 5), M6(ShiftKind.DAY, 6), M7(ShiftKind.DAY, 7), M8(ShiftKind.DAY, 8),
    M9(ShiftKind.DAY, 9), M10(ShiftKind.DAY, 10), M11(ShiftKind.DAY, 11), M12(ShiftKind.DAY, 12),
    M13(ShiftKind.DAY, 13), M14(ShiftKind.DAY, 14), M15(ShiftKind.DAY, 15), M16(ShiftKind.DAY, 16),
    M17(ShiftKind.DAY, 17), M18(ShiftKind.DAY, 18), M19(ShiftKind.DAY, 19), M20(ShiftKind.DAY, 20),
    M21(ShiftKind.DAY, 21), M22(ShiftKind.DAY, 22), M23(ShiftKind.DAY, 23), M24(ShiftKind.DAY, 24),

    E1(ShiftKind.EVENING, 1), E2(ShiftKind.EVENING, 2), E3(ShiftKind.EVENING, 3), E4(ShiftKind.EVENING, 4),
    E5(ShiftKind.EVENING, 5), E6(ShiftKind.EVENING, 6), E7(ShiftKind.EVENING, 7), E8(ShiftKind.EVENING, 8),
    E9(ShiftKind.EVENING, 9), E10(ShiftKind.EVENING, 10), E11(ShiftKind.EVENING, 11), E12(ShiftKind.EVENING, 12),
    E13(ShiftKind.EVENING, 13), E14(ShiftKind.EVENING, 14), E15(ShiftKind.EVENING, 15), E16(ShiftKind.EVENING, 16),
    E17(ShiftKind.EVENING, 17), E18(ShiftKind.EVENING, 18), E19(ShiftKind.EVENING, 19), E20(ShiftKind.EVENING, 20),
    E21(ShiftKind.EVENING, 21), E22(ShiftKind.EVENING, 22), E23(ShiftKind.EVENING, 23), E24(ShiftKind.EVENING, 24),

    N1(ShiftKind.NIGHT, 1), N2(ShiftKind.NIGHT, 2), N3(ShiftKind.NIGHT, 3), N4(ShiftKind.NIGHT, 4),
    N5(ShiftKind.NIGHT, 5), N6(ShiftKind.NIGHT, 6), N7(ShiftKind.NIGHT, 7), N8(ShiftKind.NIGHT, 8),
    N9(ShiftKind.NIGHT, 9), N10(ShiftKind.NIGHT, 10), N11(ShiftKind.NIGHT, 11), N12(ShiftKind.NIGHT, 12),
    N13(ShiftKind.NIGHT, 13), N14(ShiftKind.NIGHT, 14), N15(ShiftKind.NIGHT, 15), N16(ShiftKind.NIGHT, 16),
    N17(ShiftKind.NIGHT, 17), N18(ShiftKind.NIGHT, 18), N19(ShiftKind.NIGHT, 19), N20(ShiftKind.NIGHT, 20),
    N21(ShiftKind.NIGHT, 21), N22(ShiftKind.NIGHT, 22), N23(ShiftKind.NIGHT, 23), N24(ShiftKind.NIGHT, 24),

    R1(ShiftKind.REST, 1), R2(ShiftKind.REST, 2), R3(ShiftKind.REST, 3), R4(ShiftKind.REST, 4),
    R5(ShiftKind.REST, 5), R6(ShiftKind.REST, 6), R7(ShiftKind.REST, 7), R8(ShiftKind.REST, 8),
    R9(ShiftKind.REST, 9), R10(ShiftKind.REST, 10), R11(ShiftKind.REST, 11), R12(ShiftKind.REST, 12),
    R13(ShiftKind.REST, 13), R14(ShiftKind.REST, 14), R15(ShiftKind.REST, 15), R16(ShiftKind.REST, 16),
    R17(ShiftKind.REST, 17), R18(ShiftKind.REST, 18), R19(ShiftKind.REST, 19), R20(ShiftKind.REST, 20),
    R21(ShiftKind.REST, 21), R22(ShiftKind.REST, 22), R23(ShiftKind.REST, 23), R24(ShiftKind.REST, 24);

    companion object {
        private val M_CODES = arrayOf(
            M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12,
            M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23, M24
        )
        private val E_CODES = arrayOf(
            E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, E11, E12,
            E13, E14, E15, E16, E17, E18, E19, E20, E21, E22, E23, E24
        )
        private val N_CODES = arrayOf(
            N1, N2, N3, N4, N5, N6, N7, N8, N9, N10, N11, N12,
            N13, N14, N15, N16, N17, N18, N19, N20, N21, N22, N23, N24
        )
        private val R_CODES = arrayOf(
            R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12,
            R13, R14, R15, R16, R17, R18, R19, R20, R21, R22, R23, R24
        )

        fun startsWithM(c: ShiftCode): Boolean = c.kind == ShiftKind.DAY
        fun startsWithE(c: ShiftCode): Boolean = c.kind == ShiftKind.EVENING
        fun startsWithN(c: ShiftCode): Boolean = c.kind == ShiftKind.NIGHT

        fun forKindAndOrdinal(kind: ShiftKind, ord1Based: Int): ShiftCode {
            val idx = (ord1Based - 1).coerceIn(0, 23)
            return when (kind) {
                ShiftKind.DAY -> M_CODES[idx]
                ShiftKind.EVENING -> E_CODES[idx]
                ShiftKind.NIGHT -> N_CODES[idx]
                ShiftKind.REST -> R_CODES[idx]
            }
        }
    }
}

val SHIFT_CYCLE = listOf(
    ShiftCode.M1, ShiftCode.M2, ShiftCode.N1, ShiftCode.N2,
    ShiftCode.R1, ShiftCode.R2, ShiftCode.R3, ShiftCode.R4
)

val GROUP_OFFSETS = mapOf(
    ShiftGroup.A to 7,
    ShiftGroup.B to 1,
    ShiftGroup.C to 5,
    ShiftGroup.D to 3
)

data class ShiftPhase(
    val kind: ShiftKind,
    val count: Int
)

data class ShiftPatternSpec(
    val id: String,
    val name: String,
    val subtitle: String,
    val dayCount: Int,
    val eveningCount: Int = 0,
    val nightCount: Int,
    val restCount: Int,
    val anchorJdn: Long = ShiftEngine.DEFAULT_BASE_JDN,
    val groupOffsets: Map<ShiftGroup, Int> = GROUP_OFFSETS,
    val isWeeklyAdmin: Boolean = false,
    val isCustom: Boolean = false,
    val hasGroups: Boolean = true,
    val groupCount: Int = 4,
    val groupLabels: Map<ShiftGroup, String> = DEFAULT_GROUP_LETTERS,
    val phases: List<ShiftPhase> = emptyList()
) {
    val effectivePhases: List<ShiftPhase>
        get() = if (phases.isNotEmpty()) {
            phases.filter { it.count > 0 }
        } else {
            buildList {
                if (dayCount > 0) add(ShiftPhase(ShiftKind.DAY, dayCount))
                if (eveningCount > 0) add(ShiftPhase(ShiftKind.EVENING, eveningCount))
                if (nightCount > 0) add(ShiftPhase(ShiftKind.NIGHT, nightCount))
                if (restCount > 0) add(ShiftPhase(ShiftKind.REST, restCount))
            }
        }

    val totalCycleDays: Int
        get() = effectivePhases.sumOf { it.count }.coerceAtLeast(1)

    fun buildCycleList(): List<ShiftCode> {
        val list = ArrayList<ShiftCode>(totalCycleDays)
        for (phase in effectivePhases) {
            for (i in 1..phase.count) {
                list.add(ShiftCode.forKindAndOrdinal(phase.kind, i))
            }
        }
        if (list.isEmpty()) list.add(ShiftCode.R1)
        return list
    }
}

private fun presetFromPhases(
    id: String,
    phases: List<ShiftPhase>
): ShiftPatternSpec {
    val total = phases.sumOf { it.count }.coerceAtLeast(1)
    val dayTotal = phases.filter { it.kind == ShiftKind.DAY }.sumOf { it.count }
    val eveTotal = phases.filter { it.kind == ShiftKind.EVENING }.sumOf { it.count }
    val nightTotal = phases.filter { it.kind == ShiftKind.NIGHT }.sumOf { it.count }
    val restTotal = phases.filter { it.kind == ShiftKind.REST }.sumOf { it.count }

    val title = phases.joinToString(" ") { p ->
        "${toPersianDigits(p.count)} ${SHIFT_KIND_FA.getValue(p.kind)}"
    }
    val sub = "${phases.joinToString(" · ") { p -> "${toPersianDigits(p.count)} روز ${SHIFT_KIND_FA.getValue(p.kind)}" }} (چرخه ${toPersianDigits(total)} روزه)"
    val step = (total / 4).coerceAtLeast(1)
    val offsets = mapOf(
        ShiftGroup.A to 0,
        ShiftGroup.B to step % total,
        ShiftGroup.C to (step * 2) % total,
        ShiftGroup.D to (step * 3) % total
    )
    return ShiftPatternSpec(
        id = id,
        name = title,
        subtitle = sub,
        dayCount = dayTotal,
        eveningCount = eveTotal,
        nightCount = nightTotal,
        restCount = restTotal,
        anchorJdn = ShiftEngine.DEFAULT_BASE_JDN,
        groupOffsets = offsets,
        groupCount = 4,
        phases = phases
    )
}

private fun m(c: Int) = ShiftPhase(ShiftKind.DAY, c)
private fun e(c: Int) = ShiftPhase(ShiftKind.EVENING, c)
private fun n(c: Int) = ShiftPhase(ShiftKind.NIGHT, c)
private fun r(c: Int) = ShiftPhase(ShiftKind.REST, c)

object ShiftEngine {
    const val BASE_JY = 1405
    const val BASE_JM = 5
    const val BASE_JD = 4
    const val DEFAULT_PATTERN_ID = "bipc_2_2_4"

    val DEFAULT_BASE_JDN: Long = jalaliToJdn(BASE_JY, BASE_JM, BASE_JD)

    val DEFAULT_PATTERN = ShiftPatternSpec(
        id = DEFAULT_PATTERN_ID,
        name = "پتروشیمی بندر امام (۲-۲-۴ — پیش‌فرض)",
        subtitle = "۲ روز صبح‌کار · ۲ روز شب‌کار · ۴ روز استراحت (۱۲ ساعته — پیش‌فرض)",
        dayCount = 2,
        eveningCount = 0,
        nightCount = 2,
        restCount = 4,
        anchorJdn = DEFAULT_BASE_JDN,
        groupOffsets = GROUP_OFFSETS,
        isWeeklyAdmin = false,
        isCustom = false,
        hasGroups = true,
        groupCount = 4,
        groupLabels = DEFAULT_GROUP_LETTERS,
        phases = listOf(m(2), n(2), r(4))
    )

    val PRESET_PATTERNS: List<ShiftPatternSpec> = listOf(
        DEFAULT_PATTERN,
        presetFromPhases("ref_1m_3r", listOf(m(1), r(3))),
        presetFromPhases("ref_1m_2r", listOf(m(1), r(2))),
        presetFromPhases("ref_1m_1r", listOf(m(1), r(1))),
        presetFromPhases("ref_1m_1e_1n_2r", listOf(m(1), e(1), n(1), r(2))),
        presetFromPhases("ref_1m_1n_2r", listOf(m(1), n(1), r(2))),
        presetFromPhases("ref_1m_1n_1r", listOf(m(1), n(1), r(1))),
        presetFromPhases("ref_2m_2r_2n_2r", listOf(m(2), r(2), n(2), r(2))),
        presetFromPhases("ref_2m_2r", listOf(m(2), r(2))),
        presetFromPhases("ref_2m_1r_2e_1r_2n_3r", listOf(m(2), r(1), e(2), r(1), n(2), r(3))),
        presetFromPhases("ref_2m_1r_2e_1r_2n_1r", listOf(m(2), r(1), e(2), r(1), n(2), r(1))),
        presetFromPhases("ref_2m_1r_2n_3r", listOf(m(2), r(1), n(2), r(3))),
        presetFromPhases("ref_2m_1r", listOf(m(2), r(1))),
        presetFromPhases("ref_2m_2e_2r", listOf(m(2), e(2), r(2))),
        presetFromPhases("ref_2m_2e_2n_2r", listOf(m(2), e(2), n(2), r(2))),
        presetFromPhases("ref_2m_2e_2n_6r", listOf(m(2), e(2), n(2), r(6))),
        presetFromPhases("ref_2m_2n_2r", listOf(m(2), n(2), r(2))),
        presetFromPhases("ref_2m_2e_1r_2n_1r", listOf(m(2), e(2), r(1), n(2), r(1))),
        presetFromPhases("ref_3m_3r_3n_3r", listOf(m(3), r(3), n(3), r(3))),
        presetFromPhases("ref_3m_3r", listOf(m(3), r(3))),
        presetFromPhases("ref_3m_2r_3e_1r", listOf(m(3), r(2), e(3), r(1))),
        presetFromPhases("ref_3m_1r_3e_1r_3n_1r", listOf(m(3), r(1), e(3), r(1), n(3), r(1))),
        presetFromPhases("ref_3m_3e_3r", listOf(m(3), e(3), r(3))),
        presetFromPhases("ref_3m_3e_3n_3r", listOf(m(3), e(3), n(3), r(3))),
        presetFromPhases("ref_4m_4r", listOf(m(4), r(4))),
        presetFromPhases("ref_4m_3r", listOf(m(4), r(3))),
        presetFromPhases("ref_4m_2r", listOf(m(4), r(2))),
        presetFromPhases("ref_4m_4n_4r", listOf(m(4), n(4), r(4))),
        presetFromPhases("ref_4m_1r_4e_1r_4n_2r", listOf(m(4), r(1), e(4), r(1), n(4), r(2))),
        presetFromPhases("ref_4m_1r_4e_1r", listOf(m(4), r(1), e(4), r(1))),
        presetFromPhases("ref_4m_4e_4n_4r", listOf(m(4), e(4), n(4), r(4))),
        presetFromPhases("ref_5m_2r_5e_2r", listOf(m(5), r(2), e(5), r(2))),
        presetFromPhases("ref_5m_2r", listOf(m(5), r(2))),
        presetFromPhases("ref_6m_1r_6e_1r", listOf(m(6), r(1), e(6), r(1))),
        presetFromPhases("ref_7m_7r", listOf(m(7), r(7))),
        presetFromPhases("ref_7m_7n_7r", listOf(m(7), n(7), r(7))),
        presetFromPhases("ref_12m_4r", listOf(m(12), r(4))),
        presetFromPhases("ref_22m_8r", listOf(m(22), r(8))),
        presetFromPhases("ref_14m_14r", listOf(m(14), r(14))),
        presetFromPhases("ref_14m_7r", listOf(m(14), r(7))),
        presetFromPhases("ref_3e_3m_3n_3r", listOf(e(3), m(3), n(3), r(3))),
        presetFromPhases("ref_1n_1r", listOf(n(1), r(1))),
        presetFromPhases("ref_2n_2r", listOf(n(2), r(2))),
        presetFromPhases("ref_3n_2r_3m_2r_3e_2r", listOf(n(3), r(2), m(3), r(2), e(3), r(2))),
        presetFromPhases("ref_4n_1r_4e_1r_4m_2r", listOf(n(4), r(1), e(4), r(1), m(4), r(2)))
    )

    @Volatile
    var activePattern: ShiftPatternSpec = DEFAULT_PATTERN
        private set

    @Volatile
    private var activeCycleList: List<ShiftCode> = SHIFT_CYCLE

    val activeGroups: List<ShiftGroup>
        get() {
            val pat = activePattern
            if (!pat.hasGroups) return listOf(ShiftGroup.A)
            val cnt = pat.groupCount.coerceIn(1, ALL_SHIFT_GROUPS.size)
            return ALL_SHIFT_GROUPS.take(cnt)
        }

    val activeGroupFilters: List<GroupFilter>
        get() {
            val grps = activeGroups
            return if (!activePattern.hasGroups || grps.size <= 1) {
                listOf(GroupFilter.A)
            } else {
                listOf(GroupFilter.ALL) + grps.map { it.toGroupFilter() }
            }
        }

    fun setActivePattern(pattern: ShiftPatternSpec) {
        activePattern = pattern
        activeCycleList = if (pattern.id == DEFAULT_PATTERN_ID && !pattern.isCustom) {
            SHIFT_CYCLE
        } else {
            pattern.buildCycleList()
        }
    }

    fun getGroupLetter(group: ShiftGroup): String {
        val pat = activePattern
        if (!pat.hasGroups) return "—"
        return pat.groupLabels[group]?.takeIf { it.isNotBlank() } ?: group.name
    }

    fun getGroupTitle(group: ShiftGroup): String {
        val pat = activePattern
        if (!pat.hasGroups) return "شیفت فردی"
        val letter = pat.groupLabels[group]?.takeIf { it.isNotBlank() } ?: group.name
        return if (letter.startsWith("شیفت")) letter else "شیفت $letter"
    }

    fun isBuiltInPreset(patternId: String): Boolean =
        PRESET_PATTERNS.any { it.id == patternId }

    fun isPresetModified(patternId: String, customPatterns: List<ShiftPatternSpec>): Boolean =
        isBuiltInPreset(patternId) && customPatterns.any { it.id == patternId }

    fun getDefaultPreset(patternId: String): ShiftPatternSpec? =
        PRESET_PATTERNS.firstOrNull { it.id == patternId }

    fun resolvePattern(patternId: String, customPatterns: List<ShiftPatternSpec>): ShiftPatternSpec {
        return customPatterns.firstOrNull { it.id == patternId }
            ?: PRESET_PATTERNS.firstOrNull { it.id == patternId }
            ?: DEFAULT_PATTERN
    }

    fun floorMod(a: Long, n: Int): Long = ((a % n) + n) % n

    fun getGroupOffset(group: ShiftGroup): Int {
        val pat = activePattern
        if (!pat.hasGroups) return 0
        return pat.groupOffsets[group] ?: 0
    }

    fun daysBetweenFromBase(dateKey: String): Long {
        val d = parseDateKey(dateKey)
        return jalaliToJdn(d.jy, d.jm, d.jd) - activePattern.anchorJdn
    }

    fun getCycleIndex(dateKey: String, group: ShiftGroup): Int {
        val pat = activePattern
        if (pat.isWeeklyAdmin) {
            val d = parseDateKey(dateKey)
            return jalaaliWeekday(d.jy, d.jm, d.jd)
        }
        val cycle = activeCycleList
        return floorMod(daysBetweenFromBase(dateKey) + getGroupOffset(group), cycle.size).toInt()
    }

    fun getShiftCodeForPattern(dateKey: String, group: ShiftGroup, pattern: ShiftPatternSpec): ShiftCode {
        if (pattern.id == DEFAULT_PATTERN_ID && !pattern.isCustom) {
            val d = parseDateKey(dateKey)
            val days = jalaliToJdn(d.jy, d.jm, d.jd) - DEFAULT_BASE_JDN
            val off = GROUP_OFFSETS[group] ?: 0
            val idx = floorMod(days + off, 8).toInt()
            return SHIFT_CYCLE[idx]
        }
        if (pattern.isWeeklyAdmin) {
            val d = parseDateKey(dateKey)
            val wd = jalaaliWeekday(d.jy, d.jm, d.jd)
            return if (wd <= 4) ShiftCode.forKindAndOrdinal(ShiftKind.DAY, wd + 1)
            else ShiftCode.forKindAndOrdinal(ShiftKind.REST, wd - 4)
        }
        val cycle = pattern.buildCycleList()
        val d = parseDateKey(dateKey)
        val days = jalaliToJdn(d.jy, d.jm, d.jd) - pattern.anchorJdn
        val off = if (!pattern.hasGroups) 0 else (pattern.groupOffsets[group] ?: 0)
        val idx = floorMod(days + off, cycle.size).toInt()
        return cycle[idx]
    }

    fun getShiftCode(dateKey: String, group: ShiftGroup): ShiftCode =
        getShiftCodeForPattern(dateKey, group, activePattern)

    fun calculateShift(dateKey: String, group: ShiftGroup): ShiftResult {
        val code = getShiftCode(dateKey, group)
        return ShiftResult(code, codeToKind(code), group)
    }

    fun calculateAllShifts(dateKey: String): List<ShiftResult> =
        ALL_SHIFT_GROUPS.map { calculateShift(dateKey, it) }

    fun getAllGroupsForDate(dateKey: String): Map<ShiftGroup, ShiftCode> =
        ALL_SHIFT_GROUPS.associateWith { getShiftCode(dateKey, it) }
}

data class ShiftResult(val code: ShiftCode, val type: ShiftKind, val group: ShiftGroup)
