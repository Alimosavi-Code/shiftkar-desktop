package ir.shiftkar.neo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * ShiftKar Neo — unified icon set.
 * VERBATIM port of the original ShiftKar / PWA `icons.tsx` PATH DATA
 * (stroke-based, 24px grid, strokeWidth 1.8, round caps/joins) so every
 * icon matches the source app exactly.
 */

private fun strokeIcon(name: String, vararg d: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    )
    for (pathData in d) {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        builder.addPath(
            pathData = nodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero
        )
    }
    return builder.build()
}

object SkIcons {
    /* ---------- shift kinds ---------- */
    val Sun = strokeIcon(
        "Sun", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
        "M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z"
    )
    val Moon = strokeIcon("Moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")
    val Rest = strokeIcon(
        "Rest",
        "M3 18v-5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5",
        "M3 18h18",
        "M6 11V8a2 2 0 0 1 2-2h2"
    )

    /* ---------- navigation / chrome ---------- */
    val Calendar = strokeIcon("Calendar", "M8 2v4M16 2v4M3 10h18", "M6 4h12a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z")
    val Table = strokeIcon("Table", "M8 6h13M8 12h13M8 18h13", "M3 6h.01M3 12h.01M3 18h.01")
    val Grid = strokeIcon(
        "Grid",
        "M4.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 8.5 10h-4A1.5 1.5 0 0 1 3 8.5v-4A1.5 1.5 0 0 1 4.5 3Z",
        "M15.5 3h4A1.5 1.5 0 0 1 21 4.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 14 8.5v-4A1.5 1.5 0 0 1 15.5 3Z",
        "M4.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 8.5 21h-4A1.5 1.5 0 0 1 3 19.5v-4A1.5 1.5 0 0 1 4.5 14Z",
        "M15.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5Z"
    )
    val Notes = strokeIcon(
        "Notes",
        "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z",
        "M14 2v6h6",
        "M8 13h8M8 17h5"
    )
    val Systems = strokeIcon(
        "Systems",
        "M5 3h3.5A2 2 0 0 1 10.5 5v3.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
        "M15.5 3H19a2 2 0 0 1 2 2v3.5a2 2 0 0 1-2 2h-3.5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
        "M5 13.5h3.5a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3.5a2 2 0 0 1 2-2Z",
        "M15.5 13.5H19a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2h-3.5a2 2 0 0 1-2-2v-3.5a2 2 0 0 1 2-2Z"
    )
    val Roster = strokeIcon("Roster", "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3Z", "M11 9a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z", "M21 15l-4-4L6 21")
    val Settings = strokeIcon(
        "Settings",
        "M4 7h9M17 7h3M4 17h3M11 17h9",
        "M17 5a2 2 0 1 1 0 4 2 2 0 0 1 0-4Z",
        "M9 15a2 2 0 1 1 0 4 2 2 0 0 1 0-4Z"
    )
    val ChevronRight = strokeIcon("ChevronRight", "M9 6l6 6-6 6")
    val ChevronLeft = strokeIcon("ChevronLeft", "M15 6l-6 6 6 6")
    val ChevronUp = strokeIcon("ChevronUp", "M18 15l-6-6-6 6")
    val ChevronDown = strokeIcon("ChevronDown", "M6 9l6 6 6-6")

    /* ---------- actions ---------- */
    val External = strokeIcon(
        "External",
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"
    )
    val Download = strokeIcon("Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3")
    val Search = strokeIcon("Search", "M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z", "M21 21l-4.3-4.3")
    val ZoomIn = strokeIcon("ZoomIn", "M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z", "M21 21l-4.3-4.3", "M11 8v6M8 11h6")
    val Close = strokeIcon("Close", "M18 6 6 18M6 6l12 12")
    val Check = strokeIcon("Check", "M20 6 9 17l-5-5")
    val Spark = strokeIcon("Spark", "M12 2.8 14 9l6.2 2L14 13l-2 6.2L10 13l-6.2-2L10 9l2-6.2Z")
    val Palette = strokeIcon(
        "Palette",
        "M14.1 6.5a.6.6 0 1 1-1.2 0 .6.6 0 0 1 1.2 0Z",
        "M18.1 10.5a.6.6 0 1 1-1.2 0 .6.6 0 0 1 1.2 0Z",
        "M9.1 7.5a.6.6 0 1 1-1.2 0 .6.6 0 0 1 1.2 0Z",
        "M7.1 12.5a.6.6 0 1 1-1.2 0 .6.6 0 0 1 1.2 0Z",
        "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2Z"
    )
    val Group = strokeIcon(
        "Group",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z",
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M16 3.13a4 4 0 0 1 0 7.75"
    )
    val Info = strokeIcon("Info", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z", "M12 16v-4M12 8h.01")
    val Trash = strokeIcon("Trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6M14 11v6")
    val Phone = strokeIcon(
        "Phone",
        "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92Z"
    )
    val Help = strokeIcon("Help", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z", "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01")
    val Refresh = strokeIcon(
        "Refresh",
        "M3 12a9 9 0 0 1 15-6.7L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-15 6.7L3 16",
        "M3 21v-5h5"
    )
    val Expand = strokeIcon("Expand", "M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7")
    val Collapse = strokeIcon(
        "Collapse",
        "M8 3v3a2 2 0 0 1-2 2H3",
        "M21 8h-3a2 2 0 0 1-2-2V3",
        "M3 16h3a2 2 0 0 1 2 2v3",
        "M16 21v-3a2 2 0 0 1 2-2h3"
    )
    val Monitor = strokeIcon("Monitor", "M5.2 4.5h13.6a2.2 2.2 0 0 1 2.2 2.2v8.1a2.2 2.2 0 0 1-2.2 2.2H5.2A2.2 2.2 0 0 1 3 14.8V6.7a2.2 2.2 0 0 1 2.2-2.2Z", "M9 21h6M12 17v4")
    val Holiday = strokeIcon("Holiday", "M4 22V3", "M4 3h14l-2.4 3L18 9H4")

    /* ---------- systems ---------- */
    val File = strokeIcon("File", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z", "M14 2v6h6", "M9 13h6M9 17h4")
    val Briefcase = strokeIcon("Briefcase", "M4 7h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z", "M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16")
    val Clipboard = strokeIcon("Clipboard", "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Z", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M9 14l2 2 4-4")
    val Plane = strokeIcon(
        "Plane",
        "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2Z"
    )
    val Food = strokeIcon(
        "Food",
        "M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2",
        "M7 2v20",
        "M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7"
    )
    val Chart = strokeIcon("Chart", "M3 3v18h18", "M7 15l4-4 3 3 6-6")
    val Globe = strokeIcon(
        "Globe",
        "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z",
        "M2 12h20",
        "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10Z"
    )

    /* ---------- systems (v2.2.7 — goods-matched icons for the 4 new portals,
     * same stroke grid/style as the rest of the set) ---------- */
    /** صلیب پزشکی — درمانگاه نفت (سامانهٔ نوبت‌دهی درمانگاه — NOT oil!) */
    val Clinic = strokeIcon(
        "Clinic",
        "M11 2a2 2 0 0 0-2 2v5H4a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h5v5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-5h5a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2h-5V4a2 2 0 0 0-2-2h-2Z"
    )
    /** کارت (پتروکارت) */
    val Card = strokeIcon(
        "Card",
        "M2 7a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2Z",
        "M2 10h20"
    )
    /** موبایل/اپلیکیشن — پترو اپ */
    val Smartphone = strokeIcon(
        "Smartphone",
        "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z",
        "M12 18h.01"
    )
    /** سپر محافظ — بیمه البرز */
    val Shield = strokeIcon(
        "Shield",
        "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z",
        "M9 12l2 2 4-4"
    )

    /* ---------- contacts ---------- */
    val Whatsapp = strokeIcon(
        "Whatsapp",
        "M20.5 12a8.5 8.5 0 0 1-12.7 7.3L3.5 20.5l1.2-4.3A8.5 8.5 0 1 1 20.5 12Z",
        "M9 10.3c0-.5.3-.8.7-1l.5-.3c.3-.1.6 0 .8.2l.7.8c.2.2.1.5-.1.7l-.2.2c.4.8 1.1 1.5 1.9 1.9l.2-.2c.2-.2.5-.3.7-.1l.8.7c.2.2.3.5.2.8l-.3.5c-.2.4-.6.7-1 .7-2.6 0-4.9-2.3-4.9-5.2Z"
    )
    val Telegram = strokeIcon("Telegram", "M22 2l-7 20-4-9-9-4Z", "M22 2 11 13")
    val Eitaa = strokeIcon("Eitaa", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Z", "M8 9h8M8 13h5")
}
