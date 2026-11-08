package com.seyedhosseinmousavisaeedi.shiftkar.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.data.DayRecord
import com.seyedhosseinmousavisaeedi.shiftkar.engine.*
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkSheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics

/**
 * All-notes & shift log sheet (v6.4.0):
 *  - Combines personal notes, overtime hours, and shift swaps
 *  - Smart Persian search (matches month names like «آبان», weekdays like «جمعه», Persian/ASCII numbers, or note text)
 *  - Quick filters: همه | این ماه | اضافه‌کاری و جابه‌جایی
 */

private enum class NoteLogFilter { ALL, CURRENT_MONTH, OVERTIME_OR_SWAP }

private data class NoteLogRow(
    val dateKey: String,
    val jy: Int,
    val jm: Int,
    val jd: Int,
    val text: String,
    val record: DayRecord?,
    val label: String,
    val searchableText: String
)

private fun normalizeDigits(input: String): String {
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val pIdx = persian.indexOf(ch)
        val aIdx = arabic.indexOf(ch)
        when {
            pIdx >= 0 -> sb.append(('0' + pIdx))
            aIdx >= 0 -> sb.append(('0' + aIdx))
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

@Composable
fun NotesSheet(
    vm: AppViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenDay: (String) -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val haptic = rememberHaptics(s.hapticOn)
    val notes = vm.notesFlow.collectAsState().value
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NoteLogFilter.ALL) }

    val rows = remember(notes, s.dayRecords) {
        val allKeys = (notes.keys + s.dayRecords.keys).toSortedSet(compareByDescending { it })
        allKeys.mapNotNull { dateKey ->
            val noteText = notes[dateKey]?.trim() ?: ""
            val rec = s.dayRecords[dateKey]?.takeIf { !it.isEmpty }
            if (noteText.isEmpty() && rec == null) return@mapNotNull null
            val j = parseDateKey(dateKey)
            val wd = formatWeekday(j.jy, j.jm, j.jd)
            val jalaliStr = formatJalali(j.jy, j.jm, j.jd)
            val label = "$wd · $jalaliStr"
            val recTags = buildString {
                if ((rec?.overtimeHours ?: 0f) > 0f) append(" اضافه‌کاری ${rec!!.overtimeHours.toInt()} ساعت ")
                if (rec?.swapKind != null) append(" جابه‌جایی ${SHIFT_KIND_FA.getValue(rec.swapKind)} ")
            }
            val searchable = normalizeDigits("$dateKey $label $noteText $recTags")
            NoteLogRow(
                dateKey = dateKey,
                jy = j.jy,
                jm = j.jm,
                jd = j.jd,
                text = noteText,
                record = rec,
                label = label,
                searchableText = searchable
            )
        }
    }

    val filtered = remember(rows, q, filter, s.viewYear, s.viewMonth) {
        val normQ = normalizeDigits(q.trim())
        rows.filter { r ->
            val matchesFilter = when (filter) {
                NoteLogFilter.ALL -> true
                NoteLogFilter.CURRENT_MONTH -> r.jy == s.viewYear && r.jm == s.viewMonth
                NoteLogFilter.OVERTIME_OR_SWAP -> r.record != null
            }
            val matchesQuery = normQ.isEmpty() || r.searchableText.contains(normQ, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val totalOvertime = remember(filtered) {
        filtered.sumOf { (it.record?.overtimeHours ?: 0f).toDouble() }.toFloat()
    }

    SkSheet(visible = visible, onDismiss = onDismiss, title = "یادداشت‌ها و سوابق شیفت") {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {

            /* Search field */
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(SkIcons.Search, null, Modifier.size(16.dp), tint = sk.textFaint)
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, fontFamily = IRANSansX, color = sk.text),
                    cursorBrush = SolidColor(sk.primary),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) {
                                Text(
                                    "جستجو با متن، نام ماه (مثلاً آبان)، روز هفته یا تاریخ…",
                                    fontSize = 12.5.sp,
                                    fontFamily = IRANSansX,
                                    color = sk.textFaint
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            /* Filter chips */
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    NoteLogFilter.ALL to "همه",
                    NoteLogFilter.CURRENT_MONTH to "${JALALI_MONTHS[s.viewMonth - 1]} ${toPersianDigits(s.viewYear)}",
                    NoteLogFilter.OVERTIME_OR_SWAP to "اضافه‌کاری / جابه‌جایی"
                ).forEach { (fMode, label) ->
                    val active = filter == fMode
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) sk.primarySoft else sk.surface)
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) sk.primary else sk.borderSoft,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickableNoRipple {
                                haptic.tick()
                                filter = fMode
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = if (active) sk.primary else sk.textMuted
                        )
                    }
                }
            }

            if (totalOvertime > 0f) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.primarySoft)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "مجموع اضافه‌کاری موارد نمایش‌داده‌شده:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    Text(
                        "${toPersianDigits(totalOvertime.toInt())} ساعت",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(56.dp).glass(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Notes, null, Modifier.size(24.dp), tint = sk.primary)
                    }
                    Text(
                        "موردی یافت نشد",
                        Modifier.padding(top = 12.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text
                    )
                    Text(
                        "از تقویم روی هر روز بزنید تا یادداشت، اضافه‌کاری یا جابه‌جایی ثبت کنید",
                        Modifier.padding(top = 4.dp),
                        fontSize = 12.sp, fontFamily = IRANSansX, color = sk.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .glass(RoundedCornerShape(16.dp))
                                .border(1.dp, sk.border, RoundedCornerShape(16.dp))
                                .clickableNoRipple {
                                    onDismiss()
                                    onOpenDay(row.dateKey)
                                }
                                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(SkIcons.Calendar, null, Modifier.size(11.dp), tint = sk.primary)
                                    Text(
                                        row.label,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.primary
                                    )
                                    val ot = row.record?.overtimeHours ?: 0f
                                    if (ot > 0f) {
                                        Text(
                                            "+${toPersianDigits(ot.toInt())} ساعت",
                                            Modifier
                                                .clip(RoundedCornerShape(100))
                                                .background(sk.primarySoft)
                                                .padding(horizontal = 7.dp, vertical = 1.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = IRANSansX,
                                            color = sk.primary
                                        )
                                    }
                                    val sw = row.record?.swapKind
                                    if (sw != null) {
                                        Text(
                                            "جابه‌جایی: ${SHIFT_KIND_FA.getValue(sw)}",
                                            Modifier
                                                .clip(RoundedCornerShape(100))
                                                .background(sk.surfaceStrong)
                                                .padding(horizontal = 7.dp, vertical = 1.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = IRANSansX,
                                            color = sk.text
                                        )
                                    }
                                }
                                if (row.text.isNotEmpty()) {
                                    Text(
                                        row.text,
                                        Modifier.padding(top = 4.dp),
                                        fontSize = 13.5.sp, lineHeight = 24.sp, fontFamily = IRANSansX,
                                        color = sk.text, maxLines = 2
                                    )
                                }
                            }
                            Icon(
                                SkIcons.ChevronLeft, "باز کردن روز",
                                Modifier.size(15.dp), tint = sk.textFaint
                            )
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                Text(
                    "${toPersianDigits(filtered.size)} مورد",
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
