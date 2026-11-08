package com.seyedhosseinmousavisaeedi.shiftkar.util

import androidx.compose.ui.graphics.toArgb
import com.seyedhosseinmousavisaeedi.shiftkar.platform.copyPngBytesToClipboard
import com.seyedhosseinmousavisaeedi.shiftkar.platform.savePngBytesWithDialog
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.calendar.DayInfo
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.SkPalette
import java.io.File
import java.util.Calendar
import kotlin.math.max

/**
 * High-resolution Frost image renderer & exporter (v6.5.3 — API 23 ↔ API 37):
 *  - Fridays (`جمعه`) are no longer colored red in normal Roster/Calendar exports;
 *    only official holidays (`تعطیل رسمی`) are red.
 *  - Legend color reference swatches in exported images use the EXACT same rounded
 *    2-stop gradient chip (`drawShiftGradientChip`) as the cells themselves.
 *  - Added `CLASSIC_1405` mode (`renderClassic1405RosterBitmap`) matching the exact
 *    layout and color scheme of the 1404/1405 annual shift chart (`81361.png`) with
 *    a reworked, high-definition modern finish.
 */
enum class RosterAllShiftsMode { CLASSIC_1405, BY_SHIFT_ROLE, BY_GROUP_ROW }

data class WorkReportLogEntry(
    val dateLabel: String,
    val detailText: String,
    val badgeText: String,
    val kind: ShiftKind
)

data class WorkReportSummaryData(
    val periodTitle: String,
    val groupTitle: String,
    val patternName: String,
    val totalDays: Int,
    val dayShiftDays: Int,
    val eveningShiftDays: Int,
    val nightShiftDays: Int,
    val restDays: Int,
    val totalOvertimeHours: Float,
    val overtimeDaysCount: Int,
    val swapCount: Int,
    val holidayWorkDays: Int,
    val fridayWorkDays: Int,
    val logEntries: List<WorkReportLogEntry>
)


data class ClassicLoheCellData(
    val jy: Int,
    val jm: Int,
    val day: Int,
    val isOfficialHoliday: Boolean,
    val isFriday: Boolean
)

data class ClassicLoheRowData(
    val rowIndex: Int,
    val codeA: ShiftCode,
    val codeB: ShiftCode,
    val codeC: ShiftCode,
    val codeD: ShiftCode,
    val monthCells: List<ClassicLoheCellData?> // 13 columns: months 1..6, then months 7..12 + Farvardin(jy+1)
)

private fun jdnToDateKey(jdn: Long, approxYear: Int): String {
    val gBase = toGregorian(approxYear, 1, 1)
    val baseJdn = jalaliToJdn(approxYear, 1, 1)
    val delta = (jdn - baseJdn).toInt()
    val cal = Calendar.getInstance().apply {
        set(gBase.gy, gBase.gm - 1, gBase.gd, 12, 0, 0)
        add(Calendar.DAY_OF_YEAR, delta)
    }
    val j = toJalaali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    return makeDateKey(j.jy, j.jm, j.jd)
}

/**
 * Builds the 32-row Classic 1404/1405 Annual Shift Chart table (`81361.png`):
 *  - Row 1 (`r = 1`) is anchored to `1 Farvardin` of year `jy`.
 *  - Left 6 months (`m = 1..6`): `m = 1` starts at `row = 1`, each subsequent month starts at
 *    `(prevStartRow + prevMonthLength) % 32`.
 *  - Right 7 months (`m = 7..12` of `jy` + `m = 1` of `jy + 1`): `m = 7` (`مهر`) starts at
 *    `row = 3` (since `1 + 186 ≡ 3 (mod 8)`), each subsequent month starts at
 *    `(prevStartRow + prevMonthLength) % 32`.
 */
fun buildClassic1405LoheData(jy: Int, showEvents: Boolean): List<ClassicLoheRowData> {
    val totalRows = 32
    val jdnFarvardin1 = jalaliToJdn(jy, 1, 1)

    val monthColumns = Array(12) { arrayOfNulls<ClassicLoheCellData>(totalRows) }

    /* First half: months 1..6 (Row 0 starts at 1 Farvardin: M1/N1/R3/R1 in 1405) */
    var startRowLeft = 0
    for (m in 1..6) {
        val len = jalaaliMonthLength(jy, m)
        val firstWd = jalaaliWeekday(jy, m, 1)
        for (d in 1..len) {
            val r = (startRowLeft + d - 1) % totalRows
            val isHol = showEvents && Holidays.isHoliday(jy, m, d)
            val isFri = ((firstWd + d - 1) % 7) == 6
            monthColumns[m - 1][r] = ClassicLoheCellData(jy, m, d, isHol, isFri)
        }
        startRowLeft = (startRowLeft + len) % totalRows
    }

    /* Second half: months 7..12 (1 Mehr is +186 days after 1 Farvardin -> 186 mod 8 = 2 -> row index 2) */
    var startRowRight = 2
    for (idx in 0 until 6) {
        val mMonth = idx + 7
        val len = jalaaliMonthLength(jy, mMonth)
        val firstWd = jalaaliWeekday(jy, mMonth, 1)
        for (d in 1..len) {
            val r = (startRowRight + d - 1) % totalRows
            val isHol = showEvents && Holidays.isHoliday(jy, mMonth, d)
            val isFri = ((firstWd + d - 1) % 7) == 6
            monthColumns[6 + idx][r] = ClassicLoheCellData(jy, mMonth, d, isHol, isFri)
        }
        startRowRight = (startRowRight + len) % totalRows
    }

    return (0 until totalRows).map { r ->
        val refKey = jdnToDateKey(jdnFarvardin1 + r, jy)
        val all = ShiftEngine.getAllGroupsForDate(refKey)
        ClassicLoheRowData(
            rowIndex = r,
            codeA = all.getValue(ShiftGroup.A),
            codeB = all.getValue(ShiftGroup.B),
            codeC = all.getValue(ShiftGroup.C),
            codeD = all.getValue(ShiftGroup.D),
            monthCells = (0 until 12).map { col -> monthColumns[col][r] }
        )
    }
}

object ImageExporter {

    private val WEEKDAY_SHORT = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    private class Fonts(context: Any? = null) {
        val regular: Typeface = Typeface.REGULAR
        val bold: Typeface = Typeface.BOLD
        val extraBold: Typeface = Typeface.EXTRABOLD
        val black: Typeface = Typeface.BLACK
    }

    private fun textPaint(tf: Typeface, sizePx: Float, color: Int, align: Paint.Align = Paint.Align.CENTER): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = tf
            textSize = sizePx
            this.color = color
            textAlign = align
        }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun strokePaint(color: Int, widthPx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        this.color = color
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, baseline, paint)
    }

    private fun drawRightText(canvas: Canvas, text: String, rightX: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, rightX, baseline, paint)
    }

    private fun drawAuroraBackdrop(canvas: Canvas, w: Float, h: Float, sk: SkPalette) {
        canvas.drawColor(sk.background.toArgb())
        val vmax = max(w, h)
        val op = sk.auroraOpacity

        fun pool(cxFactor: Float, cyFactor: Float, rFactor: Float, c: androidx.compose.ui.graphics.Color) {
            val radius = vmax * rFactor
            val cx = cxFactor * w
            val cy = cyFactor * h
            val centerColor = c.copy(alpha = c.alpha * op).toArgb()
            val edgeColor = c.copy(alpha = 0f).toArgb()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(centerColor, edgeColor, edgeColor),
                    floatArrayOf(0f, 0.74f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, radius, p)
        }

        pool(0.88f, -0.05f, 0.72f, sk.glowA)
        pool(-0.12f, 0.24f, 0.60f, sk.glowB)
        pool(1.10f, 0.62f, 0.66f, sk.glowC)
        pool(0.14f, 1.05f, 0.64f, sk.glowA)
        pool(0.55f, 0.42f, 0.48f, sk.glowB)
    }

    private fun drawGlassCard(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        sk: SkPalette,
        tintColor: Int? = null,
        borderColor: Int? = null,
        borderWidth: Float = 2f
    ) {
        canvas.drawRoundRect(
            rect, radius, radius,
            fillPaint(sk.sheetSolid.copy(alpha = 0.78f).toArgb())
        )
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                sk.glassHi.toArgb(), sk.glassLo.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, gradPaint)
        if (tintColor != null) {
            canvas.drawRoundRect(rect, radius, radius, fillPaint(tintColor))
        }
        canvas.drawRoundRect(
            rect, radius, radius,
            strokePaint(borderColor ?: sk.border.toArgb(), borderWidth)
        )
    }

    private fun kindSoftAndInk(sk: SkPalette, kind: ShiftKind): Pair<Int, Int> = when (kind) {
        ShiftKind.DAY -> sk.daySoft.toArgb() to sk.dayInk.toArgb()
        ShiftKind.EVENING -> sk.eveningSoft.toArgb() to sk.eveningInk.toArgb()
        ShiftKind.NIGHT -> sk.nightSoft.toArgb() to sk.nightInk.toArgb()
        ShiftKind.REST -> sk.restSoft.toArgb() to sk.restInk.toArgb()
    }

    private fun kindGradientColors(sk: SkPalette, kind: ShiftKind): Triple<Int, Int, Int> = when (kind) {
        ShiftKind.DAY -> Triple(sk.daySoft.toArgb(), sk.daySoftEnd.toArgb(), sk.dayInk.toArgb())
        ShiftKind.EVENING -> Triple(sk.eveningSoft.toArgb(), sk.eveningSoftEnd.toArgb(), sk.eveningInk.toArgb())
        ShiftKind.NIGHT -> Triple(sk.nightSoft.toArgb(), sk.nightSoftEnd.toArgb(), sk.nightInk.toArgb())
        ShiftKind.REST -> Triple(sk.restSoft.toArgb(), sk.restSoftEnd.toArgb(), sk.restInk.toArgb())
    }

    /**
     * Draws a shift cell OR legend swatch using the exact same 2-stop linear gradient and border
     * so exported legend color references are 100% identical to the cells.
     */
    private fun drawShiftGradientChip(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        sk: SkPalette,
        kind: ShiftKind,
        drawBorder: Boolean = true
    ): Int {
        val (cStart, cEnd, ink) = kindGradientColors(sk, kind)
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                cStart, cEnd,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, gradPaint)
        if (drawBorder) {
            val borderCol = (ink and 0x00FFFFFF) or (0x55 shl 24)
            canvas.drawRoundRect(rect, radius, radius, strokePaint(borderCol, 1.3f))
        }
        return ink
    }

    private fun drawHolidayChip(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        sk: SkPalette,
        drawBorder: Boolean = true
    ): Int {
        val holStart = sk.holiday.copy(alpha = 0.25f).toArgb()
        val holEnd = sk.holiday.copy(alpha = 0.14f).toArgb()
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                holStart, holEnd,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, gradPaint)
        if (drawBorder) {
            val borderCol = sk.holiday.copy(alpha = 0.45f).toArgb()
            canvas.drawRoundRect(rect, radius, radius, strokePaint(borderCol, 1.3f))
        }
        return sk.holiday.toArgb()
    }

    /**
     * Draws the footer legend bar where every guide color swatch uses the EXACT same
     * `drawShiftGradientChip` / `drawHolidayChip` gradient as the table cells.
     */
    private fun drawIdenticalGradientLegendFooter(
        canvas: Canvas,
        footerRect: RectF,
        fonts: Fonts,
        sk: SkPalette,
        includeEvening: Boolean,
        holidayLabel: String = "تعطیل رسمی"
    ) {
        drawGlassCard(canvas, footerRect, 24f, sk)
        val items = buildList<Pair<ShiftKind?, String>> {
            add(ShiftKind.DAY to "صبح‌کار")
            if (includeEvening) add(ShiftKind.EVENING to "عصرکار")
            add(ShiftKind.NIGHT to "شب‌کار")
            add(ShiftKind.REST to "استراحت")
            add(null to holidayLabel)
        }
        val segW = footerRect.width() / items.size.toFloat()
        items.forEachIndexed { i, (kind, label) ->
            val segRight = footerRect.right - i * segW
            val segCenter = segRight - segW / 2f
            val chipW = if (items.size >= 5) 188f else 216f
            val chipH = 44f
            val chipRect = RectF(
                segCenter - chipW / 2f,
                footerRect.centerY() - chipH / 2f,
                segCenter + chipW / 2f,
                footerRect.centerY() + chipH / 2f
            )
            val ink = if (kind != null) {
                drawShiftGradientChip(canvas, chipRect, 12f, sk, kind, drawBorder = true)
            } else {
                drawHolidayChip(canvas, chipRect, 12f, sk, drawBorder = true)
            }
            drawCenteredText(
                canvas,
                label,
                chipRect.centerX(),
                chipRect.centerY(),
                textPaint(fonts.black, if (items.size >= 5) 17.5f else 19f, ink)
            )
        }
    }

    private fun drawMonthlySummaryRow(
        canvas: Canvas,
        fonts: Fonts,
        sk: SkPalette,
        dayInfo: List<DayInfo>,
        effGroup: ShiftGroup,
        pad: Float,
        w: Int,
        sumTop: Float,
        sumH: Float
    ) {
        var dayCount = 0
        var eveCount = 0
        var nightCount = 0
        var officialHolCount = 0
        var restCount = 0
        dayInfo.forEach { info ->
            if (info.holiday) officialHolCount++
            val effK = info.swapKind ?: codeToKind(info.all.getValue(effGroup))
            when (effK) {
                ShiftKind.DAY -> dayCount++
                ShiftKind.EVENING -> eveCount++
                ShiftKind.NIGHT -> nightCount++
                ShiftKind.REST -> restCount++
            }
        }

        val summaryItems = buildList<Triple<String, Int, ShiftKind?>> {
            add(Triple("صبح‌کار", dayCount, ShiftKind.DAY))
            if (eveCount > 0) add(Triple("عصرکار", eveCount, ShiftKind.EVENING))
            add(Triple("شب‌کار", nightCount, ShiftKind.NIGHT))
            add(Triple("تعطیل رسمی", officialHolCount, null))
            add(Triple("استراحت", restCount, ShiftKind.REST))
        }
        val sumGap = 14f
        val pillW = (w - pad * 2 - sumGap * (summaryItems.size - 1)) / summaryItems.size.toFloat()

        summaryItems.forEachIndexed { idx, (label, count, kind) ->
            val pRight = (w - pad) - idx * (pillW + sumGap)
            val pRect = RectF(pRight - pillW, sumTop, pRight, sumTop + sumH)
            drawGlassCard(canvas, pRect, 24f, sk)
            val ink = if (kind != null) {
                drawShiftGradientChip(canvas, pRect, 24f, sk, kind, drawBorder = true)
            } else {
                drawHolidayChip(canvas, pRect, 24f, sk, drawBorder = true)
            }
            drawCenteredText(
                canvas, label, pRect.centerX(), pRect.top + sumH * 0.36f,
                textPaint(fonts.bold, 20f, ink)
            )
            drawCenteredText(
                canvas, "${toPersianDigits(count)} روز", pRect.centerX(), pRect.top + sumH * 0.72f,
                textPaint(fonts.black, 26f, ink)
            )
        }
    }

    /* ================= 1. Annual Roster Bitmap (لوحه سالانه — تک‌گروهی) ================= */

    fun renderRosterBitmap(
        context: Any? = null,
        sk: SkPalette,
        jy: Int,
        group: ShiftGroup,
        showEvents: Boolean
    ): Bitmap {
        val w = 1240
        val h = 1980
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)
        val today = todayJalaali()
        val hasEvening = ShiftEngine.activePattern.eveningCount > 0

        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        val pad = 40f
        val headerRect = RectF(pad, 36f, w - pad, 152f)
        drawGlassCard(canvas, headerRect, 32f, sk)

        val titlePaint = textPaint(fonts.black, 38f, sk.text.toArgb(), Paint.Align.RIGHT)
        val subPaint = textPaint(fonts.bold, 21f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        canvas.drawText("لوحه سالانه ${toPersianDigits(jy)}", headerRect.right - 32f, headerRect.top + 60f, titlePaint)
        canvas.drawText("${APP.NAME} — تقویم هوشمند شیفت کاری", headerRect.right - 32f, headerRect.top + 98f, subPaint)

        val badgeRect = RectF(headerRect.left + 28f, headerRect.top + 28f, headerRect.left + 230f, headerRect.bottom - 28f)
        drawGlassCard(
            canvas, badgeRect, 22f, sk,
            tintColor = sk.primarySoft.toArgb(),
            borderColor = sk.primary.toArgb(),
            borderWidth = 2.5f
        )
        drawCenteredText(
            canvas,
            GROUP_FA.getValue(group),
            badgeRect.centerX(),
            badgeRect.centerY(),
            textPaint(fonts.black, 25f, sk.primary.toArgb())
        )

        val gridTop = 176f
        val gridBottom = h - 132f
        val gapX = 24f
        val gapY = 20f
        val colW = (w - pad * 2 - gapX) / 2f
        val rowH = (gridBottom - gridTop - gapY * 5) / 6f

        val mTitlePaint = textPaint(fonts.extraBold, 23f, sk.text.toArgb())
        val wdPaint = textPaint(fonts.bold, 15f, sk.textFaint.toArgb())
        val wdFridayPaint = textPaint(fonts.bold, 15f, sk.textMuted.toArgb())

        for (idx in 0 until 12) {
            val jm = idx + 1
            val r = idx / 2
            val c = idx % 2
            val left = if (c == 0) w - pad - colW else pad
            val top = gridTop + r * (rowH + gapY)
            val mRect = RectF(left, top, left + colW, top + rowH)
            drawGlassCard(canvas, mRect, 26f, sk)

            drawCenteredText(
                canvas,
                "${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}",
                mRect.centerX(),
                mRect.top + 26f,
                mTitlePaint
            )

            val innerPad = 16f
            val cellGap = 4f
            val gridW = colW - innerPad * 2
            val cellW = (gridW - cellGap * 6) / 7f

            val wdY = mRect.top + 54f
            for (wd in 0 until 7) {
                val cx = (mRect.right - innerPad) - wd * (cellW + cellGap) - cellW / 2f
                drawCenteredText(
                    canvas,
                    WEEKDAY_SHORT[wd],
                    cx,
                    wdY,
                    if (wd == 6) wdFridayPaint else wdPaint
                )
            }

            val len = jalaaliMonthLength(jy, jm)
            val firstWd = jalaaliWeekday(jy, jm, 1)
            val totalCells = firstWd + len
            val numRows = (totalCells + 6) / 7
            val cellsTop = mRect.top + 70f
            val availH = (mRect.bottom - 12f) - cellsTop
            val cellH = ((availH - cellGap * (numRows - 1)) / max(numRows, 5)).coerceAtMost(32f)

            for (d in 1..len) {
                val pos = firstWd + d - 1
                val cr = pos / 7
                val cc = pos % 7
                val cellRight = (mRect.right - innerPad) - cc * (cellW + cellGap)
                val cellLeft = cellRight - cellW
                val cellTop = cellsTop + cr * (cellH + cellGap)
                val cRect = RectF(cellLeft, cellTop, cellRight, cellTop + cellH)

                val key = makeDateKey(jy, jm, d)
                val code = ShiftEngine.getShiftCode(key, group)
                val kind = codeToKind(code)
                /* Only official holidays are red — Fridays keep their shift gradient! */
                val isOfficialHol = showEvents && Holidays.isHoliday(jy, jm, d)
                val isToday = today.jy == jy && today.jm == jm && today.jd == d

                val ink = if (isOfficialHol) {
                    drawHolidayChip(canvas, cRect, 8f, sk, drawBorder = false)
                } else {
                    drawShiftGradientChip(canvas, cRect, 8f, sk, kind, drawBorder = false)
                }
                if (isToday) {
                    canvas.drawRoundRect(cRect, 8f, 8f, strokePaint(sk.primary.toArgb(), 3f))
                }
                val numPaint = textPaint(
                    if (isToday) fonts.black else fonts.bold,
                    16f,
                    if (isOfficialHol) sk.holiday.toArgb() else ink
                )
                drawCenteredText(canvas, toPersianDigits(d), cRect.centerX(), cRect.centerY(), numPaint)
            }
        }

        val footerRect = RectF(pad, h - 104f, w - pad, h - 32f)
        drawIdenticalGradientLegendFooter(
            canvas = canvas,
            footerRect = footerRect,
            fonts = fonts,
            sk = sk,
            includeEvening = hasEvening,
            holidayLabel = "تعطیل رسمی"
        )

        return bmp
    }

    /* ================= 1b. Annual 4-Shift Roster Bitmap (لوحه سالانه — همهٔ شیفت‌ها) ================= */

    fun renderAllShiftsRosterBitmap(
        context: Any? = null,
        sk: SkPalette,
        jy: Int,
        showEvents: Boolean,
        mode: RosterAllShiftsMode = RosterAllShiftsMode.CLASSIC_1405
    ): Bitmap {
        if (mode == RosterAllShiftsMode.CLASSIC_1405) {
            return renderClassic1405RosterBitmap(context, sk, jy, showEvents)
        }

        val hasEvening = ShiftEngine.activePattern.eveningCount > 0
        val w = 1680
        val h = if (mode == RosterAllShiftsMode.BY_SHIFT_ROLE && !hasEvening) 2320 else 2520
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)
        val today = todayJalaali()

        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        val pad = 40f
        val headerRect = RectF(pad, 36f, w - pad, 152f)
        drawGlassCard(canvas, headerRect, 32f, sk)

        val titlePaint = textPaint(fonts.black, 40f, sk.text.toArgb(), Paint.Align.RIGHT)
        val subText = if (mode == RosterAllShiftsMode.BY_SHIFT_ROLE) {
            if (hasEvening) "${APP.NAME} — لوحهٔ کامل نوبت‌کاری (صبح‌کار / عصرکار / شب‌کار / استراحت)"
            else "${APP.NAME} — لوحهٔ کامل نوبت‌کاری (صبح‌کار / شب‌کار / استراحت)"
        } else {
            "${APP.NAME} — ماتریس سالانهٔ چهار شیفت (${GROUPS.joinToString(" / ") { it.faLetter() }})"
        }
        val subPaint = textPaint(fonts.bold, 22f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        canvas.drawText("لوحه نوبت‌کاری سال ${toPersianDigits(jy)}", headerRect.right - 32f, headerRect.top + 60f, titlePaint)
        canvas.drawText(subText, headerRect.right - 32f, headerRect.top + 100f, subPaint)

        val badgeRect = RectF(headerRect.left + 28f, headerRect.top + 28f, headerRect.left + 260f, headerRect.bottom - 28f)
        drawGlassCard(
            canvas, badgeRect, 22f, sk,
            tintColor = sk.primarySoft.toArgb(),
            borderColor = sk.primary.toArgb(),
            borderWidth = 2.5f
        )
        drawCenteredText(
            canvas,
            "همهٔ شیفت‌ها",
            badgeRect.centerX(),
            badgeRect.centerY(),
            textPaint(fonts.black, 26f, sk.primary.toArgb())
        )

        val listTop = 176f
        val listBottom = h - 128f
        val gapY = 14f
        val monthH = (listBottom - listTop - gapY * 11f) / 12f

        for (idx in 0 until 12) {
            val jm = idx + 1
            val top = listTop + idx * (monthH + gapY)
            val mRect = RectF(pad, top, w - pad, top + monthH)
            drawGlassCard(canvas, mRect, 22f, sk)

            val innerPad = 14f
            val monthColW = 118f
            val labelColW = 86f
            val colGap = 4f

            val mTitleRight = mRect.right - innerPad
            val mTitleLeft = mTitleRight - monthColW
            val mTitleRect = RectF(mTitleLeft, mRect.top + innerPad, mTitleRight, mRect.bottom - innerPad)
            drawGlassCard(
                canvas, mTitleRect, 14f, sk,
                tintColor = sk.primarySoft.toArgb(),
                borderColor = sk.primary.copy(alpha = 0.5f).toArgb(),
                borderWidth = 1.5f
            )
            drawCenteredText(
                canvas,
                JALALI_MONTHS[jm - 1],
                mTitleRect.centerX(),
                mTitleRect.centerY(),
                textPaint(fonts.black, 22f, sk.primary.toArgb())
            )

            val lblRight = mTitleLeft - colGap - 4f
            val lblLeft = lblRight - labelColW

            val daysAreaRight = lblLeft - colGap - 4f
            val daysAreaLeft = mRect.left + innerPad
            val daysAreaW = daysAreaRight - daysAreaLeft
            val cellGapX = 3f
            val cellW = (daysAreaW - cellGapX * 30f) / 31f

            val numRows = if (mode == RosterAllShiftsMode.BY_SHIFT_ROLE) {
                if (hasEvening) 5 else 4
            } else 5
            val cellGapY = 3f
            val availH = mRect.height() - innerPad * 2f
            val rowH = (availH - cellGapY * (numRows - 1)) / numRows

            val rowLabels: List<Pair<String, ShiftKind?>> = if (mode == RosterAllShiftsMode.BY_SHIFT_ROLE) {
                buildList {
                    add("تاریخ" to null)
                    add("صبح‌کار" to ShiftKind.DAY)
                    if (hasEvening) add("عصرکار" to ShiftKind.EVENING)
                    add("شب‌کار" to ShiftKind.NIGHT)
                    add("استراحت" to ShiftKind.REST)
                }
            } else {
                listOf("تاریخ" to null) + GROUPS.map { it.faLetter() to null }
            }

            rowLabels.forEachIndexed { rIdx, (rText, rKind) ->
                val rTop = mRect.top + innerPad + rIdx * (rowH + cellGapY)
                val lRect = RectF(lblLeft, rTop, lblRight, rTop + rowH)
                val rFg = when {
                    rKind != null -> drawShiftGradientChip(canvas, lRect, 7f, sk, rKind, drawBorder = false)
                    rIdx == 0 -> {
                        canvas.drawRoundRect(lRect, 7f, 7f, fillPaint(sk.surfaceStrong.toArgb()))
                        sk.textMuted.toArgb()
                    }
                    else -> {
                        canvas.drawRoundRect(lRect, 7f, 7f, fillPaint(sk.primarySoft.toArgb()))
                        sk.primary.toArgb()
                    }
                }
                drawCenteredText(
                    canvas, rText, lRect.centerX(), lRect.centerY(),
                    textPaint(fonts.extraBold, 14.5f, rFg)
                )
            }

            val len = jalaaliMonthLength(jy, jm)

            for (d in 1..len) {
                val cRight = daysAreaRight - (d - 1) * (cellW + cellGapX)
                val cLeft = cRight - cellW
                val key = makeDateKey(jy, jm, d)
                val allCodes = ShiftEngine.getAllGroupsForDate(key)
                val kinds = GROUPS.associateWith { g -> codeToKind(allCodes.getValue(g)) }
                /* Only official holidays are red — Fridays are not colored red! */
                val isOfficialHol = showEvents && Holidays.isHoliday(jy, jm, d)
                val isToday = today.jy == jy && today.jm == jm && today.jd == d

                val r0Top = mRect.top + innerPad
                val r0Rect = RectF(cLeft, r0Top, cRight, r0Top + rowH)
                val dFg = if (isOfficialHol) {
                    drawHolidayChip(canvas, r0Rect, 6f, sk, drawBorder = false)
                } else {
                    canvas.drawRoundRect(r0Rect, 6f, 6f, fillPaint(sk.surfaceStrong.toArgb()))
                    sk.text.toArgb()
                }
                if (isToday) {
                    canvas.drawRoundRect(r0Rect, 6f, 6f, strokePaint(sk.primary.toArgb(), 2.2f))
                }
                drawCenteredText(
                    canvas, toPersianDigits(d), r0Rect.centerX(), r0Rect.centerY(),
                    textPaint(if (isToday) fonts.black else fonts.bold, 15f, dFg)
                )

                if (mode == RosterAllShiftsMode.BY_SHIFT_ROLE) {
                    val dayG = GROUPS.filter { kinds[it] == ShiftKind.DAY }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
                    val eveG = GROUPS.filter { kinds[it] == ShiftKind.EVENING }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
                    val nightG = GROUPS.filter { kinds[it] == ShiftKind.NIGHT }.joinToString("") { it.faLetter() }.ifEmpty { "-" }
                    val restG = GROUPS.filter { kinds[it] == ShiftKind.REST }.joinToString("") { it.faLetter() }.ifEmpty { "-" }

                    val cellsData = buildList {
                        add(dayG to ShiftKind.DAY)
                        if (hasEvening) add(eveG to ShiftKind.EVENING)
                        add(nightG to ShiftKind.NIGHT)
                        add(restG to ShiftKind.REST)
                    }
                    cellsData.forEachIndexed { subIdx, (txt, sKind) ->
                        val rTop = mRect.top + innerPad + (subIdx + 1) * (rowH + cellGapY)
                        val cRect = RectF(cLeft, rTop, cRight, rTop + rowH)
                        val cFg = drawShiftGradientChip(canvas, cRect, 6f, sk, sKind, drawBorder = false)
                        drawCenteredText(
                            canvas, txt, cRect.centerX(), cRect.centerY(),
                            textPaint(fonts.black, if (txt.length > 1) 13.5f else 15.5f, cFg)
                        )
                    }
                } else {
                    GROUPS.forEachIndexed { gIdx, g ->
                        val rTop = mRect.top + innerPad + (gIdx + 1) * (rowH + cellGapY)
                        val cRect = RectF(cLeft, rTop, cRight, rTop + rowH)
                        val k = kinds.getValue(g)
                        val shortTxt = when (k) {
                            ShiftKind.DAY -> "ص"
                            ShiftKind.EVENING -> "ع"
                            ShiftKind.NIGHT -> "ش"
                            ShiftKind.REST -> "ا"
                        }
                        val cFg = drawShiftGradientChip(canvas, cRect, 6f, sk, k, drawBorder = false)
                        drawCenteredText(
                            canvas, shortTxt, cRect.centerX(), cRect.centerY(),
                            textPaint(fonts.black, 14.5f, cFg)
                        )
                    }
                }
            }
        }

        val footerRect = RectF(pad, h - 104f, w - pad, h - 32f)
        drawIdenticalGradientLegendFooter(
            canvas = canvas,
            footerRect = footerRect,
            fonts = fonts,
            sk = sk,
            includeEvening = hasEvening,
            holidayLabel = "تعطیل رسمی (ردیف تاریخ)"
        )

        return bmp
    }

    /* ================= 1c. Classic 4-Shift Chart Bitmap (لوحه کلاسیک — طراحی شیشه‌ای هماهنگ با تم) ================= */

    fun renderClassic1405RosterBitmap(
        context: Any? = null,
        sk: SkPalette,
        jy: Int,
        showEvents: Boolean
    ): Bitmap {
        val w = 1520
        val h = 2320
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)
        val today = todayJalaali()
        val rowsData = buildClassic1405LoheData(jy, showEvents)
        val isDark = sk.background.red < 0.3f

        /* 1. Signature Aurora + Theme Backdrop */
        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        /* 2. Dual Frost Glassy Header Pills: Right Title Pill + Left Separate Elegant Credit Pill */
        val pillTop = 34f
        val pillH = 104f
        val headerPadX = 34f
        val creditPillRect = RectF(headerPadX, pillTop, 540f, pillTop + pillH)
        val titlePillRect = RectF(564f, pillTop, w - headerPadX, pillTop + pillH)

        /* Left Separate Elegant Credit Pill */
        drawGlassCard(
            canvas, creditPillRect, 52f, sk,
            tintColor = sk.surfaceStrong.toArgb(),
            borderColor = sk.primary.copy(alpha = 0.65f).toArgb(),
            borderWidth = 2.5f
        )
        drawCenteredText(
            canvas,
            "تهیه و تنظیم لوحه",
            creditPillRect.centerX(),
            creditPillRect.top + 38f,
            textPaint(fonts.bold, 19f, sk.primary.toArgb())
        )
        drawCenteredText(
            canvas,
            "سید حسین موسوی سعیدی",
            creditPillRect.centerX(),
            creditPillRect.top + 76f,
            textPaint(fonts.black, 26f, sk.text.toArgb())
        )

        /* Right Main Title Pill */
        drawGlassCard(
            canvas, titlePillRect, 52f, sk,
            tintColor = sk.primarySoft.toArgb(),
            borderColor = sk.primary.toArgb(),
            borderWidth = 3f
        )
        drawCenteredText(
            canvas,
            "لوحه نوبت‌کاری سال ${toPersianDigits(jy)}",
            titlePillRect.centerX(),
            titlePillRect.top + 42f,
            textPaint(fonts.black, 36f, sk.text.toArgb())
        )
        drawCenteredText(
            canvas,
            "${APP.NAME} — تقویم هوشمند شیفت کاری",
            titlePillRect.centerX(),
            titlePillRect.top + 80f,
            textPaint(fonts.bold, 20f, sk.primary.toArgb())
        )

        /* 3. Main Frosted Glass Table Card & 17-Column Geometry */
        val cardPadX = 34f
        val cardTop = 156f
        val cardBottom = h - 134f
        val outerCardRect = RectF(cardPadX, cardTop, w - cardPadX, cardBottom)
        drawGlassCard(canvas, outerCardRect, 30f, sk, borderWidth = 2.5f)

        val tablePad = 14f
        val tableLeft = outerCardRect.left + tablePad
        val tableRight = outerCardRect.right - tablePad
        val tableTop = outerCardRect.top + tablePad
        val tableBottom = outerCardRect.bottom - tablePad
        val headerH = 152f
        val rowsTop = tableTop + headerH
        val availRowsH = tableBottom - rowsTop
        val rowH = availRowsH / 32f

        val totalTableW = tableRight - tableLeft
        val monthColW = totalTableW / (12f + 4f * 1.24f)
        val groupColW = monthColW * 1.24f

        /* Theme-harmonized Frost colors for A, B, C, D + Holidays + Fridays */
        val colHeaderColors = if (isDark) intArrayOf(
            Color.argb(145, 2, 132, 199),   // A: Cyan
            Color.argb(145, 217, 119, 6),   // B: Amber
            Color.argb(145, 5, 150, 105),   // C: Emerald
            Color.argb(145, 219, 39, 119)   // D: Magenta
        ) else intArrayOf(
            Color.argb(175, 125, 211, 252),
            Color.argb(185, 251, 191, 36),
            Color.argb(175, 74, 222, 128),
            Color.argb(175, 244, 114, 182)
        )
        val colBodyColors = if (isDark) intArrayOf(
            Color.argb(65, 56, 189, 248),   // A body
            Color.argb(65, 251, 191, 36),   // B body
            Color.argb(65, 52, 211, 153),   // C body
            Color.argb(65, 244, 114, 182)   // D body
        ) else intArrayOf(
            Color.argb(170, 186, 230, 253),
            Color.argb(180, 253, 224, 71),
            Color.argb(170, 134, 239, 172),
            Color.argb(170, 249, 168, 212)
        )
        val colInks = if (isDark) intArrayOf(
            Color.rgb(125, 211, 252),
            Color.rgb(253, 224, 71),
            Color.rgb(110, 231, 183),
            Color.rgb(249, 168, 212)
        ) else intArrayOf(
            Color.rgb(3, 105, 161),
            Color.rgb(146, 64, 14),
            Color.rgb(6, 95, 70),
            Color.rgb(157, 23, 77)
        )

        val redHolColor = if (isDark) sk.holiday.copy(alpha = 0.82f).toArgb() else Color.rgb(239, 68, 68)
        val yellowFriColor = if (isDark) sk.day.copy(alpha = 0.36f).toArgb() else Color.rgb(250, 204, 21)
        val yellowFriInk = if (isDark) sk.dayInk.toArgb() else Color.rgb(113, 63, 18)
        val normalCellBg = sk.surface.toArgb()
        val emptyCellBg = sk.sheetSolid.copy(alpha = 0.35f).toArgb()
        val headerMonthBg = sk.surfaceStrong.toArgb()
        val textMainColor = sk.text.toArgb()
        val gridThinPaint = strokePaint(sk.borderSoft.toArgb(), 1.3f)
        val gridThickPaint = strokePaint(sk.border.toArgb(), 3.2f)

        val colLefts = FloatArray(16)
        val colRights = FloatArray(16)
        var curX = tableLeft
        for (c in 0 until 16) {
            val cw = if (c in 6..9) groupColW else monthColW
            colLefts[c] = curX
            colRights[c] = curX + cw
            curX += cw
        }

        val firstHalfNames = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور")
        val secondHalfNames = arrayOf("مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

        /* Draw Header Cells */
        for (c in 0 until 16) {
            val hRect = RectF(colLefts[c], tableTop, colRights[c], rowsTop)
            if (c in 6..9) {
                val gIdx = c - 6
                val gEnum = ALL_SHIFT_GROUPS[gIdx]
                canvas.drawRect(hRect, fillPaint(colHeaderColors[gIdx]))
                canvas.drawRect(hRect, gridThinPaint)
                drawCenteredText(
                    canvas,
                    gEnum.faLetter(),
                    hRect.centerX(),
                    hRect.centerY(),
                    textPaint(fonts.black, 33f, colInks[gIdx])
                )
            } else {
                canvas.drawRect(hRect, fillPaint(headerMonthBg))
                canvas.drawRect(hRect, gridThinPaint)
                val mLabel = if (c < 6) firstHalfNames[c] else secondHalfNames[c - 10]
                canvas.save()
                canvas.rotate(-90f, hRect.centerX(), hRect.centerY())
                drawCenteredText(
                    canvas,
                    mLabel,
                    hRect.centerX(),
                    hRect.centerY(),
                    textPaint(fonts.black, 25f, textMainColor)
                )
                canvas.restore()
            }
        }

        /* Draw 32 Cycle Rows */
        for (r in 0 until 32) {
            val row = rowsData[r]
            val rTop = rowsTop + r * rowH
            val rBot = rTop + rowH

            for (c in 0 until 16) {
                val cRect = RectF(colLefts[c], rTop, colRights[c], rBot)
                if (c in 6..9) {
                    val gIdx = c - 6
                    val code = when (gIdx) {
                        0 -> row.codeA
                        1 -> row.codeB
                        2 -> row.codeC
                        else -> row.codeD
                    }
                    canvas.drawRect(cRect, fillPaint(colBodyColors[gIdx]))
                    canvas.drawRect(cRect, gridThinPaint)

                    val prefix = when (code.kind) {
                        ShiftKind.DAY -> "M"
                        ShiftKind.EVENING -> "E"
                        ShiftKind.NIGHT -> "N"
                        ShiftKind.REST -> "R"
                    }
                    val subNum = code.ordinalIndex.toString()
                    val mainPaint = textPaint(fonts.black, 22f, colInks[gIdx])
                    val subPaintNum = textPaint(fonts.black, 16f, colInks[gIdx])
                    drawCenteredText(canvas, prefix, cRect.centerX() - 7f, cRect.centerY() - 1f, mainPaint)
                    drawCenteredText(canvas, subNum, cRect.centerX() + 11f, cRect.centerY() + 4f, subPaintNum)
                } else {
                    val mColIdx = if (c < 6) c else (c - 4)
                    val cell = row.monthCells[mColIdx]
                    if (cell == null) {
                        canvas.drawRect(cRect, fillPaint(emptyCellBg))
                        canvas.drawRect(cRect, gridThinPaint)
                    } else {
                        val isToday = today.jy == cell.jy && today.jm == cell.jm && today.jd == cell.day
                        when {
                            cell.isOfficialHoliday && cell.isFriday -> {
                                canvas.drawRect(cRect, fillPaint(redHolColor))
                                val inner = RectF(cRect.left + 8f, cRect.top + 4f, cRect.right - 8f, cRect.bottom - 4f)
                                canvas.drawRoundRect(inner, 4f, 4f, fillPaint(yellowFriColor))
                            }
                            cell.isOfficialHoliday -> {
                                canvas.drawRect(cRect, fillPaint(redHolColor))
                            }
                            cell.isFriday -> {
                                canvas.drawRect(cRect, fillPaint(yellowFriColor))
                            }
                            else -> {
                                canvas.drawRect(cRect, fillPaint(normalCellBg))
                            }
                        }
                        canvas.drawRect(cRect, gridThinPaint)
                        if (isToday) {
                            val todayStroke = strokePaint(sk.primary.toArgb(), 3.2f)
                            canvas.drawRect(
                                RectF(cRect.left + 2f, cRect.top + 2f, cRect.right - 2f, cRect.bottom - 2f),
                                todayStroke
                            )
                        }
                        val txtColor = when {
                            cell.isOfficialHoliday && cell.isFriday -> yellowFriInk
                            cell.isOfficialHoliday -> Color.WHITE
                            cell.isFriday -> yellowFriInk
                            else -> textMainColor
                        }
                        drawCenteredText(
                            canvas,
                            toPersianDigits(cell.day),
                            cRect.centerX(),
                            cRect.centerY(),
                            textPaint(fonts.black, 21f, txtColor)
                        )
                    }
                }
            }

            if (r % 2 == 1) {
                canvas.drawLine(tableLeft, rBot, tableRight, rBot, gridThickPaint)
            }
        }

        /* Crisp structural borders around header and center A..D block */
        val tableRect = RectF(tableLeft, tableTop, tableRight, tableBottom)
        canvas.drawRect(tableRect, strokePaint(sk.primary.copy(alpha = 0.55f).toArgb(), 3.2f))
        canvas.drawLine(tableLeft, rowsTop, tableRight, rowsTop, strokePaint(sk.primary.copy(alpha = 0.55f).toArgb(), 3.2f))
        canvas.drawLine(colLefts[6], tableTop, colLefts[6], tableBottom, strokePaint(sk.primary.copy(alpha = 0.65f).toArgb(), 3.2f))
        canvas.drawLine(colRights[9], tableTop, colRights[9], tableBottom, strokePaint(sk.primary.copy(alpha = 0.65f).toArgb(), 3.2f))

        /* 4. Frosted Glass Footer Pill with Shift Code Naming Guide (M / E / N / R) */
        val footerRect = RectF(cardPadX, h - 110f, w - cardPadX, h - 32f)
        drawGlassCard(canvas, footerRect, 26f, sk)

        val holBadge = RectF(footerRect.right - 236f, footerRect.centerY() - 22f, footerRect.right - 22f, footerRect.centerY() + 22f)
        canvas.drawRoundRect(holBadge, 12f, 12f, fillPaint(redHolColor))
        drawCenteredText(canvas, "قرمز: تعطیل رسمی", holBadge.centerX(), holBadge.centerY(), textPaint(fonts.bold, 18f, Color.WHITE))

        val friBadge = RectF(footerRect.left + 22f, footerRect.centerY() - 22f, footerRect.left + 200f, footerRect.centerY() + 22f)
        canvas.drawRoundRect(friBadge, 12f, 12f, fillPaint(yellowFriColor))
        canvas.drawRoundRect(friBadge, 12f, 12f, strokePaint(yellowFriInk, 1.5f))
        drawCenteredText(canvas, "زرد: جمعه", friBadge.centerX(), friBadge.centerY(), textPaint(fonts.bold, 18f, yellowFriInk))

        /* Center Shift Naming Guide Badges (M: صبح‌کار | E: عصرکار | N: شب‌کار | R: استراحت) */
        val guideItems = buildList {
            add("M : صبح‌کار" to ShiftKind.DAY)
            if (ShiftEngine.activePattern.eveningCount > 0) {
                add("E : عصرکار" to ShiftKind.EVENING)
            }
            add("N : شب‌کار" to ShiftKind.NIGHT)
            add("R : استراحت" to ShiftKind.REST)
        }
        val guideAreaLeft = friBadge.right + 20f
        val guideAreaRight = holBadge.left - 20f
        val guideGap = 14f
        val guideBadgeW = (guideAreaRight - guideAreaLeft - guideGap * (guideItems.size - 1)) / guideItems.size.toFloat()
        guideItems.forEachIndexed { gIdx, (gText, gKind) ->
            val bRight = guideAreaRight - gIdx * (guideBadgeW + guideGap)
            val bRect = RectF(bRight - guideBadgeW, footerRect.centerY() - 22f, bRight, footerRect.centerY() + 22f)
            val bInk = drawShiftGradientChip(canvas, bRect, 12f, sk, gKind, drawBorder = true)
            drawCenteredText(
                canvas,
                gText,
                bRect.centerX(),
                bRect.centerY(),
                textPaint(fonts.black, 18f, bInk)
            )
        }

        return bmp
    }

    /* ================= 2. Monthly Calendar Grid Bitmap (تقویم ماهانه — شبکه‌ای) ================= */

    fun renderMonthBitmap(
        context: Any? = null,
        sk: SkPalette,
        jy: Int,
        jm: Int,
        filterGroup: GroupFilter,
        dayInfo: List<DayInfo>,
        firstWeekday: Int
    ): Bitmap {
        val w = 1200
        val h = 1520
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)

        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        val pad = 40f
        val headerRect = RectF(pad, 36f, w - pad, 156f)
        drawGlassCard(canvas, headerRect, 32f, sk)

        val titlePaint = textPaint(fonts.black, 40f, sk.text.toArgb(), Paint.Align.RIGHT)
        val subPaint = textPaint(fonts.bold, 21f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        canvas.drawText(
            "${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}",
            headerRect.right - 32f, headerRect.top + 62f, titlePaint
        )
        canvas.drawText(
            "${APP.NAME} — برنامهٔ ماهانهٔ شیفت کاری",
            headerRect.right - 32f, headerRect.top + 102f, subPaint
        )

        val effGroup = filterGroup.toShiftGroup()
        val badgeLabel = if (filterGroup == GroupFilter.ALL) "همهٔ گروه‌ها" else GROUP_FA.getValue(effGroup)
        val badgeRect = RectF(headerRect.left + 28f, headerRect.top + 28f, headerRect.left + 230f, headerRect.bottom - 28f)
        drawGlassCard(
            canvas, badgeRect, 22f, sk,
            tintColor = sk.primarySoft.toArgb(),
            borderColor = sk.primary.toArgb(),
            borderWidth = 2.5f
        )
        drawCenteredText(
            canvas, badgeLabel, badgeRect.centerX(), badgeRect.centerY(),
            textPaint(fonts.black, 25f, sk.primary.toArgb())
        )

        val calRect = RectF(pad, 180f, w - pad, 1180f)
        drawGlassCard(canvas, calRect, 32f, sk)

        val innerPad = 24f
        val cellGap = 10f
        val gridW = calRect.width() - innerPad * 2
        val cellW = (gridW - cellGap * 6) / 7f

        for (wd in 0 until 7) {
            val cx = (calRect.right - innerPad) - wd * (cellW + cellGap) - cellW / 2f
            drawCenteredText(
                canvas,
                WEEKDAYS[wd],
                cx,
                calRect.top + 42f,
                textPaint(fonts.bold, 20f, sk.textMuted.toArgb())
            )
        }

        val totalCells = firstWeekday + dayInfo.size
        val numRows = (totalCells + 6) / 7
        val cellsTop = calRect.top + 78f
        val availH = (calRect.bottom - 24f) - cellsTop
        val cellH = (availH - cellGap * (numRows - 1)) / max(numRows, 5)

        dayInfo.forEach { info ->
            val pos = firstWeekday + info.d - 1
            val r = pos / 7
            val c = pos % 7
            val cellRight = (calRect.right - innerPad) - c * (cellW + cellGap)
            val cellLeft = cellRight - cellW
            val cellTop = cellsTop + r * (cellH + cellGap)
            val cRect = RectF(cellLeft, cellTop, cellRight, cellTop + cellH)

            val isHol = info.holiday
            drawGlassCard(
                canvas, cRect, 22f, sk,
                tintColor = if (isHol) sk.holidaySoft.toArgb() else null,
                borderColor = when {
                    info.isToday -> sk.primary.toArgb()
                    isHol -> sk.holiday.copy(alpha = 0.35f).toArgb()
                    else -> sk.border.toArgb()
                },
                borderWidth = if (info.isToday) 4f else 1.5f
            )

            drawCenteredText(
                canvas,
                toPersianDigits(info.d),
                cRect.centerX(),
                cRect.top + cellH * 0.28f,
                textPaint(fonts.black, 28f, if (isHol) sk.holiday.toArgb() else sk.text.toArgb())
            )

            if (filterGroup == GroupFilter.ALL) {
                val bGap = 5f
                val bw = (cellW - 22f - bGap) / 2f
                val bh = (cellH * 0.48f - bGap) / 2f
                val bStartTop = cRect.top + cellH * 0.46f
                GROUPS.forEachIndexed { gi, g ->
                    val gr = gi / 2
                    val gc = gi % 2
                    val bRight = (cRect.right - 11f) - gc * (bw + bGap)
                    val bLeft = bRight - bw
                    val bTop = bStartTop + gr * (bh + bGap)
                    val bRect = RectF(bLeft, bTop, bRight, bTop + bh)
                    val code = info.all.getValue(g)
                    val ink = drawShiftGradientChip(canvas, bRect, 8f, sk, codeToKind(code), drawBorder = false)
                    drawCenteredText(
                        canvas,
                        "${g.faLetter()}${toPersianDigits(shiftCodeIndex(code))}",
                        bRect.centerX(),
                        bRect.centerY(),
                        textPaint(fonts.black, 14.5f, ink)
                    )
                }
            } else {
                val code = info.all.getValue(effGroup)
                val kind = info.swapKind ?: codeToKind(code)
                val pillRect = RectF(
                    cRect.left + 12f,
                    cRect.top + cellH * 0.52f,
                    cRect.right - 12f,
                    cRect.bottom - 12f
                )
                val labelText = if (info.swapKind != null) "${SHIFT_KIND_FA.getValue(info.swapKind)}*" else CODE_NUM_FA.getValue(code)
                val ink = drawShiftGradientChip(canvas, pillRect, 14f, sk, kind, drawBorder = true)
                drawCenteredText(
                    canvas,
                    labelText,
                    pillRect.centerX(),
                    pillRect.centerY(),
                    textPaint(fonts.bold, 17f, ink)
                )
            }
        }

        drawMonthlySummaryRow(
            canvas = canvas,
            fonts = fonts,
            sk = sk,
            dayInfo = dayInfo,
            effGroup = effGroup,
            pad = pad,
            w = w,
            sumTop = 1204f,
            sumH = 124f
        )

        val footerRect = RectF(pad, 1352f, w - pad, h - 36f)
        drawGlassCard(canvas, footerRect, 24f, sk)
        val monthHolidays = dayInfo.filter { it.holidayNames.isNotEmpty() }
            .joinToString("  •  ") { "${toPersianDigits(it.d)} ${JALALI_MONTHS[jm - 1]}: ${it.holidayNames.first()}" }
        val footerLine = if (monthHolidays.isNotEmpty()) {
            if (monthHolidays.length > 95) monthHolidays.take(95) + "…" else monthHolidays
        } else {
            "بدون تعطیلات رسمی در این ماه"
        }
        drawCenteredText(
            canvas, footerLine, footerRect.centerX(), footerRect.top + 44f,
            textPaint(fonts.bold, 19f, if (monthHolidays.isNotEmpty()) sk.holiday.toArgb() else sk.textMuted.toArgb())
        )
        drawCenteredText(
            canvas, "${APP.NAME} (${GROUP_FA.getValue(effGroup)}) — ${APP.TAGLINE}",
            footerRect.centerX(), footerRect.top + 92f,
            textPaint(fonts.bold, 18f, sk.textFaint.toArgb())
        )

        return bmp
    }

    /* ================= 3. Monthly Calendar Elegant Table Bitmap (نمای جدولی) ================= */

    fun renderMonthTableBitmap(
        context: Any? = null,
        sk: SkPalette,
        jy: Int,
        jm: Int,
        filterGroup: GroupFilter,
        dayInfo: List<DayInfo>
    ): Bitmap {
        val w = 1240
        val h = 2480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)

        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        val pad = 40f
        val effGroup = filterGroup.toShiftGroup()
        val isAll = filterGroup == GroupFilter.ALL

        val headerRect = RectF(pad, 36f, w - pad, 156f)
        drawGlassCard(canvas, headerRect, 32f, sk)

        val titlePaint = textPaint(fonts.black, 38f, sk.text.toArgb(), Paint.Align.RIGHT)
        val subPaint = textPaint(fonts.bold, 21f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        canvas.drawText(
            "جدول شیفت ${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}",
            headerRect.right - 32f, headerRect.top + 60f, titlePaint
        )
        canvas.drawText(
            "${APP.NAME} — برنامهٔ تفصیلی روزها، مناسبت‌ها و یادداشت‌ها",
            headerRect.right - 32f, headerRect.top + 100f, subPaint
        )

        val badgeLabel = if (isAll) "همهٔ گروه‌ها" else GROUP_FA.getValue(effGroup)
        val badgeRect = RectF(headerRect.left + 28f, headerRect.top + 28f, headerRect.left + 230f, headerRect.bottom - 28f)
        drawGlassCard(
            canvas, badgeRect, 22f, sk,
            tintColor = sk.primarySoft.toArgb(),
            borderColor = sk.primary.toArgb(),
            borderWidth = 2.5f
        )
        drawCenteredText(
            canvas, badgeLabel, badgeRect.centerX(), badgeRect.centerY(),
            textPaint(fonts.black, 25f, sk.primary.toArgb())
        )

        val tableRect = RectF(pad, 178f, w - pad, 2208f)
        drawGlassCard(canvas, tableRect, 32f, sk)

        val thHeight = 62f
        val thRect = RectF(tableRect.left + 4f, tableRect.top + 4f, tableRect.right - 4f, tableRect.top + thHeight)
        canvas.drawRoundRect(thRect, 28f, 28f, fillPaint(sk.glassHi.toArgb()))

        val colDayCenter = tableRect.right - 45f
        val colWdCenter = tableRect.right - 153f
        val colShiftCenter = tableRect.right - 355f
        val colEventsRight = tableRect.right - 510f

        val thPaint = textPaint(fonts.extraBold, 19f, sk.textMuted.toArgb())
        val thRightPaint = textPaint(fonts.extraBold, 19f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        drawCenteredText(canvas, "روز", colDayCenter, thRect.centerY(), thPaint)
        drawCenteredText(canvas, "هفته", colWdCenter, thRect.centerY(), thPaint)
        drawCenteredText(canvas, if (isAll) "شیفت گروه‌ها" else "وضعیت شیفت", colShiftCenter, thRect.centerY(), thPaint)
        drawRightText(canvas, "تعطیلات، مناسبت‌ها و یادداشت", colEventsRight, thRect.centerY(), thRightPaint)

        val rowsTop = tableRect.top + thHeight + 6f
        val rowsBottom = tableRect.bottom - 12f
        val rowCount = max(dayInfo.size, 29)
        val rowH = (rowsBottom - rowsTop) / rowCount

        val dividerPaint = strokePaint(sk.borderSoft.toArgb(), 1.2f)

        dayInfo.forEachIndexed { idx, info ->
            val rTop = rowsTop + idx * rowH
            val rBottom = rTop + rowH
            val rCy = (rTop + rBottom) / 2f
            val rowRect = RectF(tableRect.left + 12f, rTop + 2f, tableRect.right - 12f, rBottom - 2f)

            val isHol = info.holiday
            when {
                info.isToday -> {
                    canvas.drawRoundRect(rowRect, 14f, 14f, fillPaint(sk.primarySoft.toArgb()))
                    canvas.drawRoundRect(rowRect, 14f, 14f, strokePaint(sk.primary.toArgb(), 2.5f))
                }
                isHol -> {
                    canvas.drawRoundRect(
                        rowRect, 14f, 14f,
                        fillPaint(sk.holidaySoft.copy(alpha = 0.55f).toArgb())
                    )
                    val barRect = RectF(rowRect.right - 6f, rowRect.top + 8f, rowRect.right, rowRect.bottom - 8f)
                    canvas.drawRoundRect(barRect, 3f, 3f, fillPaint(sk.holiday.toArgb()))
                }
                idx % 2 == 1 -> {
                    canvas.drawRoundRect(
                        rowRect, 12f, 12f,
                        fillPaint(sk.surface.copy(alpha = 0.32f).toArgb())
                    )
                }
            }

            if (idx > 0) {
                canvas.drawLine(tableRect.left + 24f, rTop, tableRect.right - 24f, rTop, dividerPaint)
            }

            val dayColor = when {
                info.isToday -> sk.primary.toArgb()
                isHol -> sk.holiday.toArgb()
                else -> sk.text.toArgb()
            }
            drawCenteredText(
                canvas,
                toPersianDigits(info.d.toString().padStart(2, '0')),
                colDayCenter,
                rCy,
                textPaint(fonts.black, 21f, dayColor)
            )

            drawCenteredText(
                canvas,
                WEEKDAYS[info.weekday],
                colWdCenter,
                rCy,
                textPaint(fonts.bold, 19.5f, if (isHol) sk.holiday.toArgb() else sk.textMuted.toArgb())
            )

            if (isAll) {
                val bW = 54f
                val bH = (rowH - 16f).coerceAtMost(38f)
                val bGap = 6f
                val totalBW = bW * 4 + bGap * 3
                val startRight = colShiftCenter + totalBW / 2f
                GROUPS.forEachIndexed { gi, g ->
                    val bRight = startRight - gi * (bW + bGap)
                    val bRect = RectF(bRight - bW, rCy - bH / 2f, bRight, rCy + bH / 2f)
                    val code = info.all.getValue(g)
                    val ink = drawShiftGradientChip(canvas, bRect, 10f, sk, codeToKind(code), drawBorder = false)
                    drawCenteredText(
                        canvas,
                        "${g.faLetter()}${toPersianDigits(shiftCodeIndex(code))}",
                        bRect.centerX(),
                        bRect.centerY(),
                        textPaint(fonts.black, 16f, ink)
                    )
                }
            } else {
                val code = info.all.getValue(effGroup)
                val kind = info.swapKind ?: codeToKind(code)
                val pW = 210f
                val pH = (rowH - 14f).coerceAtMost(40f)
                val pRect = RectF(colShiftCenter - pW / 2f, rCy - pH / 2f, colShiftCenter + pW / 2f, rCy + pH / 2f)
                val pLabel = if (info.swapKind != null) "جابه‌جایی: ${SHIFT_KIND_FA.getValue(info.swapKind)}" else "${CODE_FA.getValue(code)} (${code.name})"
                val ink = drawShiftGradientChip(canvas, pRect, 13f, sk, kind, drawBorder = true)
                drawCenteredText(
                    canvas,
                    pLabel,
                    pRect.centerX(),
                    pRect.centerY(),
                    textPaint(fonts.bold, 17f, ink)
                )
            }

            val holStr = info.holidayNames.joinToString("، ")
            val occStr = info.occasions.joinToString("، ")
            val rawNote = info.note?.replace('\n', ' ')?.replace('\r', ' ')?.trim().orEmpty()
            val recPrefix = buildList {
                if (info.overtimeHours > 0f) add("اضافه‌کاری: ${toPersianDigits(info.overtimeHours.toInt())}س")
                if (info.swapKind != null) add("جابه‌جایی: ${SHIFT_KIND_FA.getValue(info.swapKind)}")
            }.joinToString(" · ")
            val noteStr = listOf(recPrefix, rawNote).filter { it.isNotEmpty() }.joinToString(" | ")

            val primaryLine = when {
                holStr.isNotEmpty() -> holStr
                occStr.isNotEmpty() -> occStr
                noteStr.isNotEmpty() -> "یادداشت: $noteStr"
                else -> "—"
            }
            val primaryColor = when {
                holStr.isNotEmpty() -> sk.holiday.toArgb()
                occStr.isNotEmpty() -> sk.text.toArgb()
                noteStr.isNotEmpty() -> sk.noteInk.toArgb()
                else -> sk.textFaint.toArgb()
            }
            val secondaryLine = when {
                holStr.isNotEmpty() && noteStr.isNotEmpty() -> "یادداشت: $noteStr"
                holStr.isNotEmpty() && occStr.isNotEmpty() -> occStr
                occStr.isNotEmpty() && noteStr.isNotEmpty() -> "یادداشت: $noteStr"
                else -> ""
            }

            val clippedPrimary = if (primaryLine.length > 52) primaryLine.take(52) + "…" else primaryLine
            if (secondaryLine.isEmpty()) {
                drawRightText(
                    canvas,
                    clippedPrimary,
                    colEventsRight,
                    rCy,
                    textPaint(if (holStr.isNotEmpty()) fonts.extraBold else fonts.bold, 18.5f, primaryColor, Paint.Align.RIGHT)
                )
            } else {
                val clippedSec = if (secondaryLine.length > 56) secondaryLine.take(56) + "…" else secondaryLine
                drawRightText(
                    canvas,
                    clippedPrimary,
                    colEventsRight,
                    rCy - rowH * 0.19f,
                    textPaint(if (holStr.isNotEmpty()) fonts.extraBold else fonts.bold, 17f, primaryColor, Paint.Align.RIGHT)
                )
                drawRightText(
                    canvas,
                    clippedSec,
                    colEventsRight,
                    rCy + rowH * 0.22f,
                    textPaint(fonts.bold, 15.5f, if (noteStr.isNotEmpty()) sk.noteInk.toArgb() else sk.textMuted.toArgb(), Paint.Align.RIGHT)
                )
            }
        }

        drawMonthlySummaryRow(
            canvas = canvas,
            fonts = fonts,
            sk = sk,
            dayInfo = dayInfo,
            effGroup = effGroup,
            pad = pad,
            w = w,
            sumTop = 2230f,
            sumH = 122f
        )

        val footerRect = RectF(pad, 2374f, w - pad, h - 32f)
        drawGlassCard(canvas, footerRect, 22f, sk)
        drawCenteredText(
            canvas,
            "${APP.NAME} (نسخهٔ ${toPersianDigits(APP.VERSION)}) — ${GROUP_FA.getValue(effGroup)} • ${APP.TAGLINE}",
            footerRect.centerX(),
            footerRect.centerY(),
            textPaint(fonts.bold, 19f, sk.textMuted.toArgb())
        )

        return bmp
    }

    /* ================= 4. Single Day Bitmap (کارت جزئیات روز) ================= */

    fun renderDayBitmap(
        context: Any? = null,
        sk: SkPalette,
        dateKey: String,
        myGroup: ShiftGroup,
        showEvents: Boolean,
        note: String?
    ): Bitmap {
        val j = parseDateKey(dateKey)
        val weekday = formatWeekday(j.jy, j.jm, j.jd)
        val g = toGregorian(j.jy, j.jm, j.jd)
        val hijri = jdnToIslamic(gregorianToJdn(g.gy, g.gm, g.gd))
        val events = if (showEvents) Holidays.getDayEvents(j.jy, j.jm, j.jd) else emptyList()
        val holidays = events.filter { it.isHoliday }
        val occasions = events.filter { !it.isHoliday }
        val mine = ShiftEngine.calculateShift(dateKey, myGroup)
        val (mineBg, mineInk) = kindSoftAndInk(sk, mine.type)

        val hasEvents = holidays.isNotEmpty() || occasions.isNotEmpty()
        val hasNote = !note.isNullOrBlank()

        val w = 1080
        val h = 920 + (if (hasEvents) 190 else 0) + (if (hasNote) 210 else 0)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)

        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        val pad = 44f
        val outerRect = RectF(pad, 44f, w - pad, h - 44f)
        drawGlassCard(canvas, outerRect, 38f, sk)

        var curY = outerRect.top + 42f
        val innerRight = outerRect.right - 38f
        val innerLeft = outerRect.left + 38f

        canvas.drawText(
            weekday,
            innerRight, curY + 26f,
            textPaint(fonts.bold, 25f, sk.primary.toArgb(), Paint.Align.RIGHT)
        )
        canvas.drawText(
            formatJalali(j.jy, j.jm, j.jd),
            innerRight, curY + 84f,
            textPaint(fonts.black, 46f, sk.text.toArgb(), Paint.Align.RIGHT)
        )
        val subCal = "${toPersianDigits(hijri.hd)} ${HIJRI_MONTHS[hijri.hm - 1]} ${toPersianDigits(hijri.hy)} قمری  •  ${formatGregorian(g.gy, g.gm, g.gd)} میلادی"
        canvas.drawText(
            subCal,
            innerRight, curY + 132f,
            textPaint(fonts.bold, 21f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        )

        val myBadge = RectF(innerLeft, curY + 8f, innerLeft + 260f, curY + 128f)
        drawGlassCard(canvas, myBadge, 26f, sk, tintColor = mineBg, borderColor = mineInk, borderWidth = 2.5f)
        drawShiftGradientChip(canvas, myBadge, 26f, sk, mine.type, drawBorder = true)
        drawCenteredText(
            canvas, "شیفت شما (${GROUP_FA.getValue(myGroup)})",
            myBadge.centerX(), myBadge.top + 38f,
            textPaint(fonts.bold, 19f, mineInk)
        )
        drawCenteredText(
            canvas, CODE_FA.getValue(mine.code),
            myBadge.centerX(), myBadge.top + 84f,
            textPaint(fonts.black, 27f, mineInk)
        )

        curY += 172f

        if (hasEvents) {
            val evRect = RectF(innerLeft, curY, innerRight, curY + 156f)
            val isHol = holidays.isNotEmpty()
            drawGlassCard(
                canvas, evRect, 26f, sk,
                tintColor = if (isHol) sk.holidaySoft.toArgb() else sk.surface.toArgb(),
                borderColor = if (isHol) sk.holiday.copy(alpha = 0.45f).toArgb() else sk.border.toArgb(),
                borderWidth = 2f
            )
            val headline = if (isHol) {
                "تعطیل رسمی: " + holidays.joinToString("، ") { it.title }
            } else {
                "مناسبت روز: " + occasions.first().title
            }
            val clippedHeadline = if (headline.length > 62) headline.take(62) + "…" else headline
            canvas.drawText(
                clippedHeadline,
                evRect.right - 26f, evRect.top + 60f,
                textPaint(fonts.extraBold, 23f, if (isHol) sk.holiday.toArgb() else sk.text.toArgb(), Paint.Align.RIGHT)
            )
            val secondLine = when {
                isHol && occasions.isNotEmpty() -> occasions.joinToString(" • ") { it.title }
                !isHol && occasions.size > 1 -> occasions.drop(1).joinToString(" • ") { it.title }
                else -> Holidays.HOLIDAY_SOURCE_LABEL
            }
            val clippedSecond = if (secondLine.length > 72) secondLine.take(72) + "…" else secondLine
            canvas.drawText(
                clippedSecond,
                evRect.right - 26f, evRect.top + 112f,
                textPaint(fonts.bold, 19f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
            )
            curY += 186f
        }

        canvas.drawText(
            "وضعیت شیفت گروه‌ها",
            innerRight, curY + 28f,
            textPaint(fonts.extraBold, 24f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        )
        curY += 48f

        val gGap = 20f
        val cardW = (innerRight - innerLeft - gGap) / 2f
        val cardH = 148f
        GROUPS.forEachIndexed { idx, grp ->
            val r = idx / 2
            val c = idx % 2
            val cRight = innerRight - c * (cardW + gGap)
            val cLeft = cRight - cardW
            val cTop = curY + r * (cardH + gGap)
            val gRect = RectF(cLeft, cTop, cRight, cTop + cardH)

            val shift = ShiftEngine.calculateShift(dateKey, grp)
            val isMine = grp == myGroup

            drawGlassCard(
                canvas, gRect, 26f, sk,
                tintColor = if (isMine) sk.primary.copy(alpha = 0.12f).toArgb() else null,
                borderColor = if (isMine) sk.primary.toArgb() else sk.border.toArgb(),
                borderWidth = if (isMine) 3.5f else 1.5f
            )

            val grpLabel = if (isMine) "${GROUP_FA.getValue(grp)} (شما)" else GROUP_FA.getValue(grp)
            canvas.drawText(
                grpLabel,
                gRect.right - 26f, gRect.top + 56f,
                textPaint(fonts.black, 26f, if (isMine) sk.primary.toArgb() else sk.text.toArgb(), Paint.Align.RIGHT)
            )
            canvas.drawText(
                "${shift.code.name}  •  ${CODE_FA.getValue(shift.code)}",
                gRect.right - 26f, gRect.top + 106f,
                textPaint(fonts.bold, 22f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
            )

            val kPill = RectF(gRect.left + 22f, gRect.top + 38f, gRect.left + 132f, gRect.bottom - 38f)
            val ink = drawShiftGradientChip(canvas, kPill, 18f, sk, shift.type, drawBorder = true)
            drawCenteredText(
                canvas,
                SHIFT_KIND_FA.getValue(shift.type),
                kPill.centerX(), kPill.centerY(),
                textPaint(fonts.black, 20f, ink)
            )
        }

        curY += cardH * 2 + gGap + 34f

        if (hasNote) {
            val nRect = RectF(innerLeft, curY, innerRight, curY + 170f)
            drawGlassCard(
                canvas, nRect, 26f, sk,
                tintColor = sk.note.toArgb(),
                borderColor = sk.noteInk.copy(alpha = 0.40f).toArgb(),
                borderWidth = 2f
            )
            canvas.drawText(
                "یادداشت روز:",
                nRect.right - 26f, nRect.top + 52f,
                textPaint(fonts.extraBold, 22f, sk.noteInk.toArgb(), Paint.Align.RIGHT)
            )
            val cleanNote = note!!.replace('\n', ' ').replace('\r', ' ').trim()
            val clippedNote = if (cleanNote.length > 70) cleanNote.take(70) + "…" else cleanNote
            canvas.drawText(
                clippedNote,
                nRect.right - 26f, nRect.top + 112f,
                textPaint(fonts.bold, 23f, sk.text.toArgb(), Paint.Align.RIGHT)
            )
            curY += 196f
        }

        drawCenteredText(
            canvas,
            "${APP.NAME} — ${APP.TAGLINE}",
            outerRect.centerX(),
            outerRect.bottom - 32f,
            textPaint(fonts.bold, 20f, sk.textFaint.toArgb())
        )

        return bmp
    }

    /* ================= 5. Work & Overtime Report Bitmap (کارت گزارش کارکرد و اضافه‌کاری) ================= */

    fun renderWorkReportBitmap(
        context: Any? = null,
        sk: SkPalette,
        report: WorkReportSummaryData
    ): Bitmap {
        val logRows = report.logEntries.take(12)
        val w = 1160
        val h = 1140 + (if (logRows.isNotEmpty()) 110 + logRows.size * 68 else 0)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fonts = Fonts(context)

        drawAuroraBackdrop(canvas, w.toFloat(), h.toFloat(), sk)

        val pad = 40f
        /* 1. Header Card */
        val headerRect = RectF(pad, 36f, w - pad, 166f)
        drawGlassCard(canvas, headerRect, 32f, sk)

        canvas.drawText(
            "گزارش کارکرد و اضافه‌کاری — ${report.periodTitle}",
            headerRect.right - 32f,
            headerRect.top + 60f,
            textPaint(fonts.black, 34f, sk.text.toArgb(), Paint.Align.RIGHT)
        )
        canvas.drawText(
            "الگوی فعال: ${report.patternName}",
            headerRect.right - 32f,
            headerRect.top + 104f,
            textPaint(fonts.bold, 20f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
        )

        val badgeRect = RectF(headerRect.left + 28f, headerRect.top + 28f, headerRect.left + 250f, headerRect.bottom - 28f)
        drawGlassCard(
            canvas, badgeRect, 22f, sk,
            tintColor = sk.primarySoft.toArgb(),
            borderColor = sk.primary.toArgb(),
            borderWidth = 2.5f
        )
        drawCenteredText(
            canvas,
            report.groupTitle,
            badgeRect.centerX(),
            badgeRect.centerY(),
            textPaint(fonts.black, 24f, sk.primary.toArgb())
        )

        /* 2. Key Payslip Metrics (2x2 Grid) */
        var curY = 192f
        val gap = 18f
        val boxW = (w - pad * 2 - gap) / 2f
        val boxH = 148f

        val otHoursStr = if (report.totalOvertimeHours % 1f == 0f) {
            toPersianDigits(report.totalOvertimeHours.toInt())
        } else {
            toPersianDigits(String.format(java.util.Locale.US, "%.1f", report.totalOvertimeHours))
        }
        val activeWorkDays = report.dayShiftDays + report.eveningShiftDays + report.nightShiftDays

        val metrics = listOf(
            Triple("مجموع اضافه‌کاری ثبت‌شده", "$otHoursStr ساعت", "در ${toPersianDigits(report.overtimeDaysCount)} روز کاری"),
            Triple("کل روزهای کاری (نوبت فعال)", "${toPersianDigits(activeWorkDays)} روز", "از مجموع ${toPersianDigits(report.totalDays)} روز دوره"),
            Triple("نوبت کاری در تعطیلات رسمی", "${toPersianDigits(report.holidayWorkDays)} روز", "تعطیل‌کاری رسمی"),
            Triple("نوبت کاری در روزهای جمعه / جابه‌جایی", "${toPersianDigits(report.fridayWorkDays)} جمعه · ${toPersianDigits(report.swapCount)} جابه‌جایی", "جمعه کاری و تغییرات نوبت")
        )

        metrics.forEachIndexed { idx, (mTitle, mVal, mSub) ->
            val r = idx / 2
            val c = idx % 2
            val right = (w - pad) - c * (boxW + gap)
            val left = right - boxW
            val top = curY + r * (boxH + gap)
            val mRect = RectF(left, top, right, top + boxH)
            drawGlassCard(
                canvas, mRect, 26f, sk,
                tintColor = if (idx == 0) sk.primarySoft.toArgb() else null,
                borderColor = if (idx == 0) sk.primary.toArgb() else sk.border.toArgb(),
                borderWidth = if (idx == 0) 2.5f else 1.8f
            )
            canvas.drawText(
                mTitle,
                mRect.right - 26f,
                mRect.top + 42f,
                textPaint(fonts.bold, 19f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
            )
            canvas.drawText(
                mVal,
                mRect.right - 26f,
                mRect.top + 92f,
                textPaint(fonts.black, 31f, if (idx == 0) sk.primary.toArgb() else sk.text.toArgb(), Paint.Align.RIGHT)
            )
            canvas.drawText(
                mSub,
                mRect.right - 26f,
                mRect.top + 128f,
                textPaint(fonts.bold, 16.5f, sk.textFaint.toArgb(), Paint.Align.RIGHT)
            )
        }

        curY += boxH * 2 + gap + 28f

        /* 3. 4-Shift Breakdown Strip */
        val shiftCardRect = RectF(pad, curY, w - pad, curY + 230f)
        drawGlassCard(canvas, shiftCardRect, 28f, sk)
        canvas.drawText(
            "تفکیک روزهای نوبت‌کاری در این دوره",
            shiftCardRect.right - 28f,
            shiftCardRect.top + 46f,
            textPaint(fonts.extraBold, 22f, sk.text.toArgb(), Paint.Align.RIGHT)
        )

        val sItems = buildList {
            add(Triple("صبح‌کار", report.dayShiftDays, ShiftKind.DAY))
            if (report.eveningShiftDays > 0 || ShiftEngine.activePattern.eveningCount > 0) {
                add(Triple("عصرکار", report.eveningShiftDays, ShiftKind.EVENING))
            }
            add(Triple("شب‌کار", report.nightShiftDays, ShiftKind.NIGHT))
            add(Triple("استراحت", report.restDays, ShiftKind.REST))
        }
        val sGap = 14f
        val sPad = 24f
        val sPillW = (shiftCardRect.width() - sPad * 2 - sGap * (sItems.size - 1)) / sItems.size.toFloat()
        val sTop = shiftCardRect.top + 74f
        val sH = 128f

        sItems.forEachIndexed { idx, (lbl, cnt, sKind) ->
            val pRight = (shiftCardRect.right - sPad) - idx * (sPillW + sGap)
            val pRect = RectF(pRight - sPillW, sTop, pRight, sTop + sH)
            val ink = drawShiftGradientChip(canvas, pRect, 22f, sk, sKind, drawBorder = true)
            drawCenteredText(canvas, lbl, pRect.centerX(), pRect.top + 44f, textPaint(fonts.bold, 21f, ink))
            drawCenteredText(canvas, "${toPersianDigits(cnt)} روز", pRect.centerX(), pRect.top + 92f, textPaint(fonts.black, 28f, ink))
        }

        curY += 256f

        /* 4. Detailed Overtime & Swap Log Rows (if any) */
        if (logRows.isNotEmpty()) {
            val logCardH = 88f + logRows.size * 68f
            val logRect = RectF(pad, curY, w - pad, curY + logCardH)
            drawGlassCard(canvas, logRect, 28f, sk)
            canvas.drawText(
                "ریز سوابق اضافه‌کاری و جابه‌جایی نوبت",
                logRect.right - 28f,
                logRect.top + 48f,
                textPaint(fonts.extraBold, 22f, sk.primary.toArgb(), Paint.Align.RIGHT)
            )

            logRows.forEachIndexed { rIdx, entry ->
                val rTop = logRect.top + 72f + rIdx * 68f
                val rowRect = RectF(logRect.left + 20f, rTop, logRect.right - 20f, rTop + 58f)
                canvas.drawRoundRect(rowRect, 14f, 14f, fillPaint(sk.surface.toArgb()))
                canvas.drawText(
                    entry.dateLabel,
                    rowRect.right - 18f,
                    rowRect.centerY() + 7f,
                    textPaint(fonts.black, 19f, sk.text.toArgb(), Paint.Align.RIGHT)
                )
                canvas.drawText(
                    entry.detailText,
                    rowRect.right - 250f,
                    rowRect.centerY() + 7f,
                    textPaint(fonts.bold, 18f, sk.textMuted.toArgb(), Paint.Align.RIGHT)
                )
                val bRect = RectF(rowRect.left + 14f, rowRect.top + 9f, rowRect.left + 210f, rowRect.bottom - 9f)
                val bInk = drawShiftGradientChip(canvas, bRect, 11f, sk, entry.kind, drawBorder = true)
                drawCenteredText(canvas, entry.badgeText, bRect.centerX(), bRect.centerY(), textPaint(fonts.black, 16.5f, bInk))
            }
            curY += logCardH + 24f
        }

        /* 5. Footer Card */
        val footerRect = RectF(pad, h - 104f, w - pad, h - 32f)
        drawGlassCard(canvas, footerRect, 24f, sk)
        drawCenteredText(
            canvas,
            "${APP.NAME} (نسخهٔ ${toPersianDigits(APP.VERSION)}) — گزارش رسمی کارکرد و نوبت‌کاری",
            footerRect.centerX(),
            footerRect.centerY(),
            textPaint(fonts.bold, 19f, sk.textMuted.toArgb())
        )

        return bmp
    }

    /* ================= Desktop Save PNG & Copy Image to Clipboard ================= */

    fun copyBitmapToClipboard(bitmap: Bitmap): Boolean =
        copyPngBytesToClipboard(bitmap.toPngBytes())

    fun saveBitmapWithDialog(bitmap: Bitmap, fileName: String): String? =
        savePngBytesWithDialog(fileName, bitmap.toPngBytes())
}
