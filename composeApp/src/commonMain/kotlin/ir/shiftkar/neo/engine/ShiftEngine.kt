package ir.shiftkar.neo.engine

/**
 * Shift calculation engine — faithful Kotlin port of the PWA's
 * `src/lib/shift-engine.ts`, which itself matches the original Android
 * implementation exactly:
 *
 *  - 8-day cycle: M1 M2 N1 N2 R1 R2 R3 R4
 *  - Base date: 1405/05/04
 *  - Group offsets: A=7, B=1, C=5, D=3
 *  - cycleIndex = floorMod(daysBetween + groupOffset, 8)
 *
 * floorMod is used so dates BEFORE the base date produce correct
 * non-negative indexes (a plain % remainder would produce negatives).
 */

enum class ShiftCode { M1, M2, N1, N2, R1, R2, R3, R4;
    companion object {
        fun startsWithM(c: ShiftCode) = c == ShiftCode.M1 || c == ShiftCode.M2
        fun startsWithN(c: ShiftCode) = c == ShiftCode.N1 || c == ShiftCode.N2
    }
}

val SHIFT_CYCLE = listOf(
    ShiftCode.M1, ShiftCode.M2, ShiftCode.N1, ShiftCode.N2,
    ShiftCode.R1, ShiftCode.R2, ShiftCode.R3, ShiftCode.R4
)

/** Group offsets — the original engine constants: A=7, B=1, C=5, D=3. */
val GROUP_OFFSETS = mapOf(
    ShiftGroup.A to 7,
    ShiftGroup.B to 1,
    ShiftGroup.C to 5,
    ShiftGroup.D to 3
)

object ShiftEngine {
    const val BASE_JY = 1405
    const val BASE_JM = 5
    const val BASE_JD = 4

    /** Mathematical floor modulo: always returns a value in [0, n). */
    fun floorMod(a: Long, n: Int): Long = ((a % n) + n) % n

    fun getGroupOffset(group: ShiftGroup): Int = GROUP_OFFSETS[group] ?: 0

    private val BASE_JDN = jalaliToJdn(BASE_JY, BASE_JM, BASE_JD)

    /** Whole days between the target date and the base date (negative before base). */
    fun daysBetweenFromBase(dateKey: String): Long {
        val d = parseDateKey(dateKey)
        return jalaliToJdn(d.jy, d.jm, d.jd) - BASE_JDN
    }

    /** Cycle index in [0, 7] for a date + group. */
    fun getCycleIndex(dateKey: String, group: ShiftGroup): Int =
        floorMod(daysBetweenFromBase(dateKey) + getGroupOffset(group), SHIFT_CYCLE.size).toInt()

    /** Shift code for a date + group: M1/M2/N1/N2/R1/R2/R3/R4. */
    fun getShiftCode(dateKey: String, group: ShiftGroup): ShiftCode =
        SHIFT_CYCLE[getCycleIndex(dateKey, group)]

    /** Full result. */
    fun calculateShift(dateKey: String, group: ShiftGroup): ShiftResult {
        val code = getShiftCode(dateKey, group)
        return ShiftResult(code, codeToKind(code), group)
    }

    /** Shifts for all four groups on a date. */
    fun calculateAllShifts(dateKey: String): List<ShiftResult> =
        GROUP_OFFSETS.keys.map { calculateShift(dateKey, it) }
}

data class ShiftResult(val code: ShiftCode, val type: ShiftKind, val group: ShiftGroup)
