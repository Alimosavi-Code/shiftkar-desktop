package com.seyedhosseinmousavisaeedi.shiftkar.engine

/**
 * Shared domain constants and Persian labels (v6.5.4):
 *  - Expanded ShiftGroup & GroupFilter up to 8 groups (A..H) so users can dynamically
 *    add groups (e.g., E, F, G, H) or remove groups (e.g., remove C, D to have 2 or 3 groups)
 *    or use individual group-less mode.
 *  - GROUPS and GROUP_FILTERS dynamically reflect the active pattern's group count.
 */

enum class ShiftGroup { A, B, C, D, E, F, G, H }

val ALL_SHIFT_GROUPS = ShiftGroup.entries.toList()

val GROUPS: List<ShiftGroup>
    get() = ShiftEngine.activeGroups

enum class GroupFilter { ALL, A, B, C, D, E, F, G, H }

val GROUP_FILTERS: List<GroupFilter>
    get() = ShiftEngine.activeGroupFilters

fun GroupFilter.toShiftGroup(): ShiftGroup = when (this) {
    GroupFilter.ALL -> ShiftGroup.A
    GroupFilter.A -> ShiftGroup.A
    GroupFilter.B -> ShiftGroup.B
    GroupFilter.C -> ShiftGroup.C
    GroupFilter.D -> ShiftGroup.D
    GroupFilter.E -> ShiftGroup.E
    GroupFilter.F -> ShiftGroup.F
    GroupFilter.G -> ShiftGroup.G
    GroupFilter.H -> ShiftGroup.H
}

fun ShiftGroup.toGroupFilter(): GroupFilter = when (this) {
    ShiftGroup.A -> GroupFilter.A
    ShiftGroup.B -> GroupFilter.B
    ShiftGroup.C -> GroupFilter.C
    ShiftGroup.D -> GroupFilter.D
    ShiftGroup.E -> GroupFilter.E
    ShiftGroup.F -> GroupFilter.F
    ShiftGroup.G -> GroupFilter.G
    ShiftGroup.H -> GroupFilter.H
}

val DEFAULT_GROUP_LETTERS = mapOf(
    ShiftGroup.A to "A",
    ShiftGroup.B to "B",
    ShiftGroup.C to "C",
    ShiftGroup.D to "D",
    ShiftGroup.E to "E",
    ShiftGroup.F to "F",
    ShiftGroup.G to "G",
    ShiftGroup.H to "H"
)

val GROUP_FA: Map<ShiftGroup, String>
    get() = ALL_SHIFT_GROUPS.associateWith { ShiftEngine.getGroupTitle(it) }

val GROUP_LETTER_FA: Map<ShiftGroup, String>
    get() = ALL_SHIFT_GROUPS.associateWith { ShiftEngine.getGroupLetter(it) }

fun ShiftGroup.faLetter(): String = ShiftEngine.getGroupLetter(this)

enum class ShiftKind { DAY, EVENING, NIGHT, REST }

val SHIFT_KIND_FA = mapOf(
    ShiftKind.DAY to "صبح‌کار",
    ShiftKind.EVENING to "عصرکار",
    ShiftKind.NIGHT to "شب‌کار",
    ShiftKind.REST to "استراحت"
)

private val ORDINAL_FA = arrayOf(
    "اول", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم",
    "هشتم", "نهم", "دهم", "یازدهم", "دوازدهم", "سیزدهم", "چهاردهم",
    "پانزدهم", "شانزدهم", "هفدهم", "هجدهم", "نوزدهم", "بیستم",
    "بیست‌ویکم", "بیست‌ودوم", "بیست‌وسوم", "بیست‌وچهارم"
)

val CODE_FA: Map<ShiftCode, String> = ShiftCode.entries.associateWith { code ->
    val idx = shiftCodeIndex(code)
    val ord = ORDINAL_FA.getOrElse(idx - 1) { toPersianDigits(idx) }
    when {
        ShiftCode.startsWithM(code) -> "صبح‌کار $ord"
        ShiftCode.startsWithE(code) -> "عصرکار $ord"
        ShiftCode.startsWithN(code) -> "شب‌کار $ord"
        else -> "استراحت $ord"
    }
}

fun codeToKind(code: ShiftCode): ShiftKind = code.kind

val CODE_NUM_FA: Map<ShiftCode, String> = ShiftCode.entries.associateWith { code ->
    val idx = toPersianDigits(shiftCodeIndex(code))
    when {
        ShiftCode.startsWithM(code) -> "صبح‌کار $idx"
        ShiftCode.startsWithE(code) -> "عصرکار $idx"
        ShiftCode.startsWithN(code) -> "شب‌کار $idx"
        else -> "استراحت $idx"
    }
}

fun ShiftCode.compactLabel(): String = name

fun shiftCodeIndex(code: ShiftCode): Int = code.ordinalIndex

/* ---- Themes — Native Dynamic Color (Material You) first as default + six signature palettes ---- */

enum class ThemeId(val hex: Long) {
    DYNAMIC(0xFF0EA5A0),
    BLUE(0xFF3D6BF5),
    EMERALD(0xFF0F9D72),
    PURPLE(0xFF8B5CF6),
    ORANGE(0xFFF46B16),
    ROSE(0xFFF43F5E),
    TEAL(0xFF0EA5A0);

    val fa: String get() = when (this) {
        DYNAMIC -> "پویا (سیستم)"
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

enum class TabId { CALENDAR, SYSTEMS, ROSTER, MANAGER, SETTINGS }

object APP {
    const val NAME = "شیفت‌کار"
    const val NAME_EN = "Shiftkar"
    const val VERSION = "6.7.3"
    const val TAGLINE = "تقویم هوشمند شیفت کاری"
}
