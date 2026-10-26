package ir.shiftkar.neo

import ir.shiftkar.neo.engine.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineMultiplatformTest {

    @Test
    fun jalaliGregorianRoundTrip_andWeekdays() {
        // Nowruz 1405 = 2026-03-21 (Saturday = 0 in Persian week)
        val g = toGregorian(1405, 1, 1)
        assertEquals(Gregorian(2026, 3, 21), g)
        assertEquals(JalaliDate(1405, 1, 1), toJalaali(g.gy, g.gm, g.gd))
        assertEquals(0, jalaaliWeekday(1405, 1, 1))
    }

    @Test
    fun shiftEngine_baseDateAndFloorModBeforeBase() {
        val baseKey = "1405-05-04"
        assertEquals(0L, ShiftEngine.daysBetweenFromBase(baseKey))
        // Offsets: A=7 (R4), B=1 (M2), C=5 (R2), D=3 (N2)
        assertEquals(ShiftCode.R4, ShiftEngine.getShiftCode(baseKey, ShiftGroup.A))
        assertEquals(ShiftCode.M2, ShiftEngine.getShiftCode(baseKey, ShiftGroup.B))
        assertEquals(ShiftCode.R2, ShiftEngine.getShiftCode(baseKey, ShiftGroup.C))
        assertEquals(ShiftCode.N2, ShiftEngine.getShiftCode(baseKey, ShiftGroup.D))

        // 8 days before base must produce the exact same shifts
        val eightBefore = jdnToJalali(jalaliToJdn(1405, 5, 4) - 8)
        val keyBefore = makeDateKey(eightBefore.jy, eightBefore.jm, eightBefore.jd)
        assertEquals(ShiftCode.R4, ShiftEngine.getShiftCode(keyBefore, ShiftGroup.A))
        assertEquals(ShiftCode.M2, ShiftEngine.getShiftCode(keyBefore, ShiftGroup.B))
    }

    @Test
    fun islamicTableEndpoint_isExact() {
        // 1 Muharram 1264 AH = JDN 2396005
        assertEquals(2396005L, islamicToJdn(1264, 1, 1))
        assertEquals(HijriDate(1264, 1, 1), jdnToIslamic(2396005L))
    }

    @Test
    fun shiftCodeIndex_numbers() {
        assertEquals(1, shiftCodeIndex(ShiftCode.M1))
        assertEquals(2, shiftCodeIndex(ShiftCode.M2))
        assertEquals(1, shiftCodeIndex(ShiftCode.N1))
        assertEquals(2, shiftCodeIndex(ShiftCode.N2))
        assertEquals(1, shiftCodeIndex(ShiftCode.R1))
        assertEquals(2, shiftCodeIndex(ShiftCode.R2))
        assertEquals(3, shiftCodeIndex(ShiftCode.R3))
        assertEquals(4, shiftCodeIndex(ShiftCode.R4))
        assertTrue(GROUPS.size == 4)
    }
}
