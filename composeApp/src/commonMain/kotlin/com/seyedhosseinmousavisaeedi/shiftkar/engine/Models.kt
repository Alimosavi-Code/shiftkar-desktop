package com.seyedhosseinmousavisaeedi.shiftkar.engine

/**
 * Shared domain constants and Persian labels — port of the PWA's
 * `src/lib/models.ts`. Keeps the original ShiftKar naming and palette.
 */

enum class ShiftGroup { A, B, C, D }

val GROUPS = listOf(ShiftGroup.A, ShiftGroup.B, ShiftGroup.C, ShiftGroup.D)

enum class GroupFilter { ALL, A, B, C, D }

val GROUP_FILTERS = GroupFilter.entries.toList()

/** Engine groups accept only concrete groups — «همه» folds onto A (PWA parity). */
fun GroupFilter.toShiftGroup(): ShiftGroup = when (this) {
    GroupFilter.ALL -> ShiftGroup.A
    GroupFilter.A -> ShiftGroup.A
    GroupFilter.B -> ShiftGroup.B
    GroupFilter.C -> ShiftGroup.C
    GroupFilter.D -> ShiftGroup.D
}

/* v2.2.5 (درخواست مالک: «Keep it shift a b c d in all places! Not ا or ب!»):
 * حروف گروه همه‌جا لاتین است — «شیفت A/B/C/D» (برچسب کامل) و «A/B/C/D»
 * (نشان‌ها و چیپ‌های تک‌حرفی). هرگز آ/ب/ج/د فارسی نیست. */
val GROUP_FA = mapOf(
    ShiftGroup.A to "شیفت A",
    ShiftGroup.B to "شیفت B",
    ShiftGroup.C to "شیفت C",
    ShiftGroup.D to "شیفت D"
)

/** حرف لاتین هر گروه — برای چیپ‌ها و نشان‌های تک‌حرفی. */
val GROUP_LETTER_FA = mapOf(
    ShiftGroup.A to "A",
    ShiftGroup.B to "B",
    ShiftGroup.C to "C",
    ShiftGroup.D to "D"
)

fun ShiftGroup.faLetter(): String = GROUP_LETTER_FA.getValue(this)

enum class ShiftKind { DAY, NIGHT, REST }

/* v2.2.9 (مالک: «Instead of روزکار for shifts write صبح‌کار!») — نام نوع
 * شیفت‌ها با نیم‌فاصلهٔ درست: صبح‌کار / شب‌کار / استراحت. */
val SHIFT_KIND_FA = mapOf(
    ShiftKind.DAY to "صبح‌کار",
    ShiftKind.NIGHT to "شب‌کار",
    ShiftKind.REST to "استراحت"
)

/** Human label per cycle code (M1…R4) — «صبح‌کار دوم» style (v2.2.9 naming). */
val CODE_FA = mapOf(
    ShiftCode.M1 to "صبح‌کار اول",
    ShiftCode.M2 to "صبح‌کار دوم",
    ShiftCode.N1 to "شب‌کار اول",
    ShiftCode.N2 to "شب‌کار دوم",
    ShiftCode.R1 to "استراحت اول",
    ShiftCode.R2 to "استراحت دوم",
    ShiftCode.R3 to "استراحت سوم",
    ShiftCode.R4 to "استراحت چهارم"
)

fun codeToKind(code: ShiftCode): ShiftKind = when {
    ShiftCode.startsWithM(code) -> ShiftKind.DAY
    ShiftCode.startsWithN(code) -> ShiftKind.NIGHT
    else -> ShiftKind.REST
}

/**
 * v2.2.9 (مالک: «in 4 shift view Instead of شب or روز write صبح‌کار or
 * شب‌کار! Also make it that it says a number after that! Like صبح‌کار 1!») —
 * برچسب کوتاهِ هر کد شیفت برای بنر «شیفت امروز گروه‌ها»: نام نوع + شمارهٔ
 * ترتیب در چرخه. اگر جا نبود، حالت فشرده همان کد لاتین است (M1، R2…) —
 * تابع کمکیِ زیر انتخاب هوشمند را انجام می‌دهد.
 */
val CODE_NUM_FA = mapOf(
    ShiftCode.M1 to "صبح‌کار ۱",
    ShiftCode.M2 to "صبح‌کار ۲",
    ShiftCode.N1 to "شب‌کار ۱",
    ShiftCode.N2 to "شب‌کار ۲",
    ShiftCode.R1 to "استراحت ۱",
    ShiftCode.R2 to "استراحت ۲",
    ShiftCode.R3 to "استراحت ۳",
    ShiftCode.R4 to "استراحت ۴"
)

/** برچسب فشردهٔ کد برای فضای تنگ — همان کد لاتین چرخه (M1، N2، R4…). */
fun ShiftCode.compactLabel(): String = name

/**
 * شمارهٔ ترتیبی شیفت در چرخه (صبح‌کار اول=۱، دوم=۲، شب‌کار اول=۱، دوم=۲،
 * استراحت اول تا چهارم=۱ تا ۴) — برای نشان‌های عددی تقویم «همهٔ گروه‌ها».
 * نوع شیفت (صبح/شب/استراحت) از رنگ و آیکون مشخص می‌شود، پس شمارهٔ به‌تنهایی
 * گویاست.
 */
fun shiftCodeIndex(code: ShiftCode): Int = when (code) {
    ShiftCode.M1 -> 1
    ShiftCode.M2 -> 2
    ShiftCode.N1 -> 1
    ShiftCode.N2 -> 2
    ShiftCode.R1 -> 1
    ShiftCode.R2 -> 2
    ShiftCode.R3 -> 3
    ShiftCode.R4 -> 4
}

/* ---- Themes — the same six palettes and names as the original ---- */

enum class ThemeId(val hex: Long) {
    BLUE(0xFF3D6BF5),
    EMERALD(0xFF0F9D72),
    PURPLE(0xFF8B5CF6),
    ORANGE(0xFFF46B16),
    ROSE(0xFFF43F5E),
    TEAL(0xFF0EA5A0);

    val fa: String get() = when (this) {
        BLUE -> "آبی"
        EMERALD -> "زمردی"
        PURPLE -> "بنفش"
        ORANGE -> "نارنجی"
        ROSE -> "رز"
        TEAL -> "فیروزه‌ای"
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM;
    val fa: String get() = when (this) {
        LIGHT -> "روشن"
        DARK -> "تیره"
        SYSTEM -> "هماهنگ با سیستم"
    }
}

enum class CalendarSystem { JALALI, HIJRI, GREGORIAN;
    val fa: String get() = when (this) {
        JALALI -> "هجری شمسی"
        HIJRI -> "هجری قمری"
        GREGORIAN -> "میلادی"
    }
}

enum class ViewType { GRID, TABLE }

enum class TabId { CALENDAR, SYSTEMS, ROSTER, SETTINGS }

object APP {
    /* v2.2.5 (مالک: «Make the app name plain Shiftkar») — دیگر «نئو» ندارد. */
    const val NAME = "شیفت‌کار"
    const val NAME_EN = "Shiftkar"
    /* v6.0.4 (مالک: «splash screen … looks ugly especially in dark mode» +
     * «bottom bar a bit more glassy»): اسپلش جدید — کارت گردگوشه با هالهٔ ملایم
     * روی پس‌زمینهٔ تیره؛ نوار پایین کمی شیشه‌ای‌تر (آلفای ۰٫۹۰) — 6.0.4 / versionCode 64 */
    const val VERSION = "6.1.0"
    const val TAGLINE = "تقویم هوشمند شیفت کاری"
}
