package com.seyedhosseinmousavisaeedi.shiftkar.engine

/**
 * Jalali (Persian) calendar — faithful Kotlin port of the PWA's
 * `src/lib/jalali.ts`, itself a port of the standard jalaali-js algorithm.
 * Pure conversion & formatting logic, no Android dependencies.
 *
 * Original ShiftKar engine notes preserved:
 *  - BREAKS: 33-year cycle table
 *  - Persian week: 0 = شنبه (Saturday) … 6 = جمعه (Friday)
 */

/* ---------------- low-level helpers ---------------- */

private fun div(a: Long, b: Long): Long = a / b          // JS Math.trunc semantics
private fun mod(a: Long, b: Long): Long = a - b * Math.floorDiv(a, b)

private val BREAKS = longArrayOf(
    -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060,
    2097, 2192, 2262, 2324, 2394, 2456, 3178
)

private class JalCalResult(val leap: Int, val gy: Long, val march: Int)

private fun jalCal(jy: Int): JalCalResult {
    val bl = BREAKS.size
    val gy = jy + 621L
    var leapJ = -14L
    var jp = BREAKS[0]
    var jump = 0L
    var leap = 0
    var march = 0
    var n: Long

    require(jy >= jp && jy < BREAKS[bl - 1]) { "Invalid Jalaali year $jy" }

    var i = 1
    var jm = 0L
    while (i < bl) {
        jm = BREAKS[i]
        jump = jm - jp
        if (jy < jm) break
        leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
        jp = jm
        i++
    }
    n = jy - jp

    leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
    if (mod(jump, 33) == 4L && jump - n == 4L) leapJ += 1

    val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
    march = (20 + leapJ - leapG).toInt()

    if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
    leap = mod(mod(n + 1, 33) - 1, 4).toInt()
    if (leap == -1) leap = 4

    return JalCalResult(leap, gy, march)
}

/** Gregorian (gy, gm, gd) -> Julian Day Number (standard JDN, 2000-01-01 = 2451545). */
private fun g2d(gy: Long, gm: Long, gd: Long): Long {
    var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
        div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
    /* NOTE: must be `d - X + 752` (NOT `d -= X + 752`) — the TS source is
       `d = d - div(...) + 752`. */
    d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
    return d
}

/** JDN -> Gregorian. */
private fun d2g(jdn: Long): Gregorian {
    var j = 4 * jdn + 139361631
    j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
    val i = div(mod(j, 1461), 4) * 5 + 308
    val gd = div(mod(i, 153), 5) + 1
    val gm = mod(div(i, 153), 12) + 1
    val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
    return Gregorian(gy.toInt(), gm.toInt(), gd.toInt())
}

private fun j2d(jy: Int, jm: Int, jd: Int): Long {
    val r = jalCal(jy)
    return g2d(r.gy, 3, r.march.toLong()) + (jm - 1) * 31 - div(jm.toLong(), 7) * (jm - 7) + jd - 1
}

private fun d2j(jdn: Long): JalaliDate {
    val gy = d2g(jdn).gy
    var jy = gy - 621
    val r = jalCal(jy)
    val jdn1f = g2d(r.gy, 3, r.march.toLong())
    var k = jdn - jdn1f
    var jm: Int
    var jd: Int

    if (k >= 0) {
        if (k <= 185) {
            jm = (1 + div(k, 31)).toInt()
            jd = (mod(k, 31) + 1).toInt()
            return JalaliDate(jy.toInt(), jm, jd)
        }
        k -= 186
    } else {
        jy -= 1
        k += 179
        if (r.leap == 1) k += 1
    }
    jm = (7 + div(k, 30)).toInt()
    jd = (mod(k, 30) + 1).toInt()
    return JalaliDate(jy.toInt(), jm, jd)
}

/* ---------------- public API ---------------- */

data class JalaliDate(val jy: Int, val jm: Int, val jd: Int)
data class Gregorian(val gy: Int, val gm: Int, val gd: Int)

/** Gregorian -> Jalali. */
fun toJalaali(gy: Int, gm: Int, gd: Int): JalaliDate = d2j(g2d(gy.toLong(), gm.toLong(), gd.toLong()))

/** Jalali -> Gregorian. */
fun toGregorian(jy: Int, jm: Int, jd: Int): Gregorian = d2g(j2d(jy, jm, jd))

/** JDN -> Jalali (used by the holidays engine). */
fun jdnToJalali(jdn: Long): JalaliDate = d2j(jdn)

/** Jalali -> JDN (used by the holidays engine). */
fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Long = j2d(jy, jm, jd)

fun isLeapJalaaliYear(jy: Int): Boolean = jalCal(jy).leap == 0

fun jalaaliMonthLength(jy: Int, jm: Int): Int {
    if (jm <= 6) return 31
    if (jm <= 11) return 30
    return if (isLeapJalaaliYear(jy)) 30 else 29
}

/**
 * Persian week index: 0 = شنبه (Saturday) … 6 = جمعه (Friday).
 * Ported exactly: JS getUTCDay (0=Sun..6=Sat) mapped by (jsDay + 1) % 7.
 */
fun jalaaliWeekday(jy: Int, jm: Int, jd: Int): Int =
    Math.floorMod(j2d(jy, jm, jd) + 2, 7).toInt()

/* ---------------- names & formatting ---------------- */

val JALALI_MONTHS = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

val WEEKDAYS = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

val GREGORIAN_MONTHS = arrayOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
    "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
)

val HIJRI_MONTHS = arrayOf(
    "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
    "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
)

private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

fun toPersianDigits(value: Any?): String {
    val s = value.toString()
    val sb = StringBuilder(s.length)
    for (i in 0 until s.length) {
        val c = s[i]
        sb.append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
    }
    return sb.toString()
}

fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** ASCII date key, e.g. "1405-05-04" (lexicographically sortable). */
fun makeDateKey(jy: Int, jm: Int, jd: Int): String = "$jy-${pad2(jm)}-${pad2(jd)}"

fun parseDateKey(key: String): JalaliDate {
    val d1 = key.indexOf('-')
    val d2 = if (d1 >= 0) key.indexOf('-', d1 + 1) else -1
    if (d1 > 0 && d2 > d1) {
        return JalaliDate(
            key.substring(0, d1).toInt(),
            key.substring(d1 + 1, d2).toInt(),
            key.substring(d2 + 1).toInt()
        )
    }
    val p = key.split("-").map { it.toInt() }
    return JalaliDate(p[0], p[1], p[2])
}

fun formatJalali(jy: Int, jm: Int, jd: Int): String =
    "${toPersianDigits(jd)} ${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}"

fun formatGregorian(gy: Int, gm: Int, gd: Int): String =
    "${toPersianDigits(gd)} ${GREGORIAN_MONTHS[gm - 1]} ${toPersianDigits(gy)}"

fun formatWeekday(jy: Int, jm: Int, jd: Int): String = WEEKDAYS[jalaaliWeekday(jy, jm, jd)]

fun todayJalaali(): JalaliDate {
    val now = java.time.LocalDate.now()
    return toJalaali(now.year, now.monthValue, now.dayOfMonth)
}


/**
 * Compatibility helper converting a Jalali date directly to HijriDate.
 */
fun toHijri(jy: Int, jm: Int, jd: Int): HijriDate = jdnToIslamic(jalaliToJdn(jy, jm, jd))

/**
 * Formats a Hijri date (hy/hm/hd) with Persian digits and month name.
 */
fun formatHijri(hy: Int, hm: Int, hd: Int): String =
    "${toPersianDigits(hd)} ${HIJRI_MONTHS[(hm - 1).coerceIn(0, 11)]} ${toPersianDigits(hy)}"
