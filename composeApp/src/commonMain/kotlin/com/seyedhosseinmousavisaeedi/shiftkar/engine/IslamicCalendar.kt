package com.seyedhosseinmousavisaeedi.shiftkar.engine

import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/**
 * Islamic (Hijri) calendar — faithful Kotlin port of the PWA's
 * `src/lib/islamic-calendar.ts`, itself a port of the persian-calendar
 * calendar library.
 *
 * Two converters:
 *
 * 1. The OFFICIAL Iranian qamari calendar — a lookup table of month lengths
 *    for Hijri years 1264–1449, sourced from roozbehp/qamari and used
 *    verbatim by the library for Iran. It matches the printed Iranian
 *    calendars exactly (no ±1 day drift).
 *
 * 2. An astronomical new-moon visibility fallback (Makkah parameters) for
 *    Hijri years outside the table.
 *
 * All JDN values here are standard Julian Day Numbers (JDN of 2000-01-01 is
 * 2451545).
 */

data class HijriDate(val hy: Int, val hm: Int, val hd: Int)

object IslamicCalendar {

    /* ---------------- official Iranian qamari table (1264–1449 AH) ---------------- */

    val HIJRI_MONTH_BITS = intArrayOf(
        0b101010010111, // 1264
        0b101010101010, // 1265
        0b101011010101, // 1266
        0b010110010100, // 1267
        0b101110101010, // 1268
        0b010110110101, // 1269
        0b010010110110, // 1270
        0b101001010111, // 1271
        0b010100101011, // 1272
        0b011010100011, // 1273
        0b011011010001, // 1274
        0b101011101001, // 1275
        0b010101101010, // 1276
        0b101001101101, // 1277
        0b010100101101, // 1278
        0b110010010101, // 1279
        0b111001001010, // 1280
        0b111010100101, // 1281
        0b011010110100, // 1282
        0b100110111010, // 1283
        0b010100111011, // 1284
        0b001001011011, // 1285
        0b010100101011, // 1286
        0b101001010101, // 1287
        0b101010101010, // 1288
        0b101101011001, // 1289
        0b010101110100, // 1290
        0b100101111010, // 1291
        0b010010111010, // 1292
        0b101001011010, // 1293
        0b110100110100, // 1294
        0b111010110001, // 1295
        0b011011011000, // 1296
        0b101011101100, // 1297
        0b010101011100, // 1298
        0b101001101110, // 1299
        0b010100110110, // 1300
        0b101010100110, // 1301
        0b101101010010, // 1302
        0b101110101001, // 1303
        0b001110110100, // 1304
        0b100111011010, // 1305
        0b010101011010, // 1306
        0b101010101010, // 1307
        0b110101001010, // 1308
        0b111010100101, // 1309
        0b011101010010, // 1310
        0b101101101001, // 1311
        0b010110110100, // 1312
        0b101010101101, // 1313
        0b011001010110, // 1314
        0b110100100110, // 1315
        0b111010010010, // 1316
        0b111101001001, // 1317
        0b011101010100, // 1318
        0b101101011010, // 1319
        0b100110011011, // 1320
        0b010010011011, // 1321
        0b100101001011, // 1322
        0b101100100101, // 1323
        0b110101010010, // 1324
        0b110101101010, // 1325
        0b010101101101, // 1326
        0b001010110110, // 1327
        0b101000110111, // 1328
        0b010010011011, // 1329
        0b011001001101, // 1330
        0b011010101010, // 1331
        0b101101010101, // 1332
        0b001101011100, // 1333
        0b100101101110, // 1334
        0b010010101111, // 1335
        0b001001010111, // 1336
        0b001100101011, // 1337
        0b010110010101, // 1338
        0b001110101010, // 1339
        0b010111011001, // 1340
        0b001011011010, // 1341
        0b100101011101, // 1342
        0b001010101011, // 1343
        0b010101010101, // 1344
        0b011011001001, // 1345
        0b011011100100, // 1346
        0b101101101010, // 1347
        0b010110110101, // 1348
        0b001010110110, // 1349
        0b100110010110, // 1350
        0b110101001010, // 1351
        0b110111000101, // 1352
        0b011101010010, // 1353
        0b011110100101, // 1354
        0b001101101010, // 1355
        0b100110101101, // 1356
        0b010101001101, // 1357
        0b101010010101, // 1358
        0b110101001001, // 1359
        0b110110100101, // 1360
        0b010110110010, // 1361
        0b101011010101, // 1362
        0b010101010110, // 1363
        0b101001010111, // 1364
        0b010100101011, // 1365
        0b011010010101, // 1366
        0b101101001010, // 1367
        0b101101100101, // 1368
        0b010101101011, // 1369
        0b001010101101, // 1370
        0b010101001110, // 1371
        0b110010010111, // 1372
        0b010101001011, // 1373
        0b011010100101, // 1374
        0b011011010010, // 1375
        0b101011011001, // 1376
        0b010011011101, // 1377
        0b001001010111, // 1378
        0b100100101101, // 1379
        0b101010010101, // 1380
        0b101101010010, // 1381
        0b101101101001, // 1382
        0b001101110100, // 1383
        0b100101110110, // 1384
        0b010010110111, // 1385
        0b001001010111, // 1386
        0b010101001011, // 1387
        0b011010100101, // 1388
        0b011011010010, // 1389
        0b101011101010, // 1390
        0b010011101101, // 1391
        0b001001101101, // 1392
        0b100100110101, // 1393
        0b110100100101, // 1394
        0b110101010001, // 1395
        0b101110101001, // 1396
        0b010111010100, // 1397
        0b101010110101, // 1398
        0b010100110110, // 1399
        0b101010010111, // 1400
        0b011001001010, // 1401
        0b111010100101, // 1402
        0b011101010010, // 1403
        0b101110101001, // 1404
        0b010110110101, // 1405
        0b001010110101, // 1406
        0b101001010110, // 1407
        0b110100100110, // 1408
        0b111001010011, // 1409
        0b011010101001, // 1410
        0b110101010100, // 1411
        0b110101010110, // 1412
        0b101001010111, // 1413
        0b010010100111, // 1414
        0b110001000111, // 1415
        0b110100100110, // 1416
        0b111001010100, // 1417
        0b110110100110, // 1418
        0b010101100111, // 1419
        0b001010110110, // 1420
        0b100100110111, // 1421
        0b010010010111, // 1422
        0b011001010101, // 1423
        0b101010101010, // 1424
        0b101101100101, // 1425
        0b001011101100, // 1426
        0b100101110101, // 1427
        0b010001101110, // 1428
        0b101000110110, // 1429
        0b110010100110, // 1430
        0b110101010010, // 1431
        0b110111010010, // 1432
        0b010111010101, // 1433
        0b001011011010, // 1434
        0b010101011101, // 1435
        0b010010101011, // 1436
        0b011010010011, // 1437
        0b011101001001, // 1438
        0b011110100100, // 1439
        0b101110110010, // 1440
        0b010110110101, // 1441
        0b001010110110, // 1442
        0b011001011010, // 1443
        0b110100101010, // 1444
        0b111010010100, // 1445
        0b111011010001, // 1446
        0b011011101000, // 1447
        0b101011101010, // 1448
        0b100101011100  // 1449
    )

    private const val SUPPORTED_START_JDN = 2396005L // 1 Muharram 1264 AH
    private const val SUPPORTED_START_YEAR = 1264

    /** monthOffsets[m] = day offset (from 1 Muharram 1264) of month m (0-based). */
    private val monthOffsets: IntArray = run {
        val out = IntArray(HIJRI_MONTH_BITS.size * 12)
        var acc = 0
        for (m in out.indices) {
            out[m] = acc
            val bits = HIJRI_MONTH_BITS[m / 12]
            acc += if ((bits shr (11 - (m % 12))) and 1 == 1) 30 else 29
        }
        out
    }
    private val JDN_SUPPORT_END = monthOffsets.last() +
        (if ((HIJRI_MONTH_BITS.last() shr 0) and 1 == 1) 30 else 29) + SUPPORTED_START_JDN

    /** Official-table lookup. Returns -1 outside 1264–1449 AH. */
    fun toIranianIslamicJdn(hy: Int, hm: Int, hd: Int): Long {
        val yearIndex = hy - SUPPORTED_START_YEAR
        if (yearIndex < 0 || yearIndex >= HIJRI_MONTH_BITS.size) return -1
        return monthOffsets[yearIndex * 12 + hm - 1].toLong() + hd + SUPPORTED_START_JDN - 1
    }

    fun fromIranianIslamicJdn(jdn: Long): HijriDate? {
        if (jdn < SUPPORTED_START_JDN || jdn >= JDN_SUPPORT_END) return null
        val days = jdn - SUPPORTED_START_JDN
        var index = (days / 30).toInt()
        while (index + 1 < monthOffsets.size && monthOffsets[index + 1] <= days) index += 1
        val yearIndex = index / 12
        val month = index % 12
        val day = days - monthOffsets[index]
        return HijriDate(yearIndex + SUPPORTED_START_YEAR, month + 1, day.toInt() + 1)
    }

    /* ---------------- astronomical fallback (out of table range) ---------------- */

    private const val NMONTHS = 1405 * 12 + 1
    private const val D2R = PI / 180.0

    private fun sinOfDegree(deg: Double): Double = sin(deg * D2R)

    /** Conjunction (nph = 0..3) instant as a fractional Julian date (ET→UT). */
    private fun tmoonphase(n: Double, nph: Int): Double {
        val k = n + nph / 4.0
        val T = k / 1236.85
        val t2 = T * T
        val t3 = t2 * T
        val jd = 2415020.75933 + 29.53058868 * k - 0.0001178 * t2 - 0.000000155 * t3 +
            0.00033 * sinOfDegree(166.56 + 132.87 * T - 0.009173 * t2)
        val sa = (359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3) * D2R
        val ma = (306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3) * D2R
        val tf = 2 * (21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3) * D2R
        val sinSA = sin(sa)
        val sinMA = sin(ma)
        val sinTF = sin(tf)
        val xtra: Double
        if (nph == 0 || nph == 2) {
            xtra = (0.1734 - 0.000393 * T) * sinSA +
                0.0021 * sin(sa * 2) -
                0.4068 * sinMA +
                0.0161 * sin(2 * ma) -
                0.0004 * sin(3 * ma) +
                0.0104 * sinTF -
                0.0051 * sin(sa + ma) -
                0.0074 * sin(sa - ma) +
                0.0004 * sin(tf + sa) -
                0.0004 * sin(tf - sa) -
                0.0006 * sin(tf + ma) +
                0.001 * sin(tf - ma) +
                0.0005 * sin(sa + 2 * ma)
        } else {
            xtra = (0.1721 - 0.0004 * T) * sinSA +
                0.0021 * sin(sa * 2) -
                0.628 * sinMA +
                0.0089 * sin(2 * ma) -
                0.0004 * sin(3 * ma) +
                0.0079 * sinTF -
                0.0119 * sin(sa + ma) -
                0.0047 * sin(sa - ma) +
                0.0003 * sin(tf + sa) -
                0.0004 * sin(tf - sa) -
                0.0006 * sin(tf + ma) +
                0.0021 * sin(tf - ma) +
                0.0003 * sin(sa + 2 * ma) +
                0.0004 * sin(sa - 2 * ma) -
                0.0003 * sin(2 * sa + ma) +
                (if (nph == 1)
                    0.0028 - 0.0004 * cos(sa) + 0.0003 * cos(ma)
                else
                    -0.0028 + 0.0004 * cos(sa) - 0.0003 * cos(ma))
        }
        return jd + xtra - (0.41 + 1.2053 * T + 0.4992 * t2) / 1440
    }

    /** First day (fractional JD) of the lunar month containing conjunction n. */
    private fun visibility(n: Double): Double {
        val TIMZ = 3.0
        val MINAGE = 13.5
        val SUNSET = 19.5
        val TIMDIF = SUNSET - MINAGE
        val jd = tmoonphase(n, 0)
        val d = floor(jd)
        var tf = jd - d
        if (tf <= 0.5) return jd + 1
        tf = (tf - 0.5) * 24 + TIMZ
        return if (tf > TIMDIF) jd + 1 else jd
    }

    /** Astronomical fallback conversions (used only outside the table range). */
    fun fallbackIslamicToJdn(hy: Int, hm: Int, hd: Int): Long {
        var year = hy.toDouble()
        if (year < 0) year += 1
        val k = hm + year * 12 - NMONTHS // months since 1/1/1405
        return floor(visibility(k + 1048) + hd + 0.5).toLong()
    }

    fun fallbackIslamicFromJdn(jdn: Long): HijriDate {
        val g = jdnToGregorian(jdn)
        var year = g.gy
        var month = g.gm
        val day = g.gd
        var k = floor(0.6 + (year + (if (month % 2 == 0) month else month - 1) / 12.0 + day / 365.0 - 1900) * 12.3685)
        var mjd: Double
        do {
            mjd = visibility(k)
            k -= 1
        } while (mjd > jdn - 0.5)
        k += 1
        val hm = k - 1048
        year = 1405 + floor(hm / 12).toInt()
        month = (hm % 12).toInt() + 1
        if (hm != 0.0 && month <= 0) {
            month += 12
            year -= 1
        }
        if (year <= 0) year -= 1
        val hd = floor(jdn - mjd + 0.5).toInt()
        return HijriDate(year, month, hd)
    }

    /* ---------------- unified API (table first, fallback second) ---------------- */

    /** Hijri (hy, hm, hd) -> standard JDN. */
    fun islamicToJdn(hy: Int, hm: Int, hd: Int): Long {
        val table = toIranianIslamicJdn(hy, hm, hd)
        return if (table != -1L) table else fallbackIslamicToJdn(hy, hm, hd)
    }

    /** Standard JDN -> Hijri. */
    fun jdnToIslamic(jdn: Long): HijriDate =
        fromIranianIslamicJdn(jdn) ?: fallbackIslamicFromJdn(jdn)

    /** Days in a Hijri month (table when available, else fallback). */
    fun islamicMonthLength(hy: Int, hm: Int): Int {
        val yearIndex = hy - SUPPORTED_START_YEAR
        if (yearIndex >= 0 && yearIndex < HIJRI_MONTH_BITS.size) {
            return if ((HIJRI_MONTH_BITS[yearIndex] shr (11 - (hm - 1))) and 1 == 1) 30 else 29
        }
        val next = if (hm == 12) islamicToJdn(hy + 1, 1, 1) else islamicToJdn(hy, hm + 1, 1)
        return (next - islamicToJdn(hy, hm, 1)).toInt()
    }

    /* ---------------- Gregorian JDN helpers (standard JDN) ---------------- */

    private const val UNIX_EPOCH_JDN = 2440588L // JDN of 1970-01-01

    fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Long =
        g2d(gy.toLong(), gm.toLong(), gd.toLong())

    fun jdnToGregorian(jdn: Long): Gregorian = d2g(jdn)

    /** Weekday ordinal matching the persian-calendar library: 0 = Saturday …
     *  6 = Friday (JDN 2451545, a Saturday, → 0). */
    fun weekDayOrdinal(jdn: Long): Long = floorModLong(jdn + 2L, 7L)
}

/* Top-level convenience aliases (match the TS export names). */
fun islamicToJdn(hy: Int, hm: Int, hd: Int): Long = IslamicCalendar.islamicToJdn(hy, hm, hd)
fun jdnToIslamic(jdn: Long): HijriDate = IslamicCalendar.jdnToIslamic(jdn)
fun islamicMonthLength(hy: Int, hm: Int): Int = IslamicCalendar.islamicMonthLength(hy, hm)
fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Long = IslamicCalendar.gregorianToJdn(gy, gm, gd)
fun jdnToGregorian(jdn: Long): Gregorian = IslamicCalendar.jdnToGregorian(jdn)
fun weekDayOrdinal(jdn: Long): Long = IslamicCalendar.weekDayOrdinal(jdn)
