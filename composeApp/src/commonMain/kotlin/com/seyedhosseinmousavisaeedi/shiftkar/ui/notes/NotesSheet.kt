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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.formatJalali
import com.seyedhosseinmousavisaeedi.shiftkar.engine.formatWeekday
import com.seyedhosseinmousavisaeedi.shiftkar.engine.parseDateKey
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.platform.openTextFileWithDialog
import com.seyedhosseinmousavisaeedi.shiftkar.platform.saveTextFileWithDialog
import com.seyedhosseinmousavisaeedi.shiftkar.ui.AppViewModel
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.ToastCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkSheet
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

/**
 * All-notes sheet — searchable list of personal notes (notes-sheet.tsx port).
 */

/* v6.1.0: ردیفِ آمادهٔ نمایش — برچسب تاریخ قالب‌بندی‌شده فقط یک‌بار ساخته می‌شود */
private data class NoteRow(val dateKey: String, val text: String, val label: String)

@Composable
fun NotesSheet(
    vm: AppViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenDay: (String) -> Unit
) {
    val sk = LocalSk.current
    val s = vm.settings.collectAsState().value
    val notes = vm.notesFlow.collectAsState().value
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    /* v6.1.0 (بازبینی کد): مرتب‌سازی + قالب‌بندی تاریخ هر یادداشت «یک‌بار به‌ازای
     * هر تغییر notes» محاسبه می‌شود — قبلاً هر کلیدِ جستجو کل فهرست را دوباره
     * مرتب می‌کرد و parseDateKey/formatWeekday/formatJalali داخل حلقهٔ
     * ریکامپوزیشن برای همهٔ ردیف‌ها تکرار می‌شد. */
    val rows = remember(notes) {
        notes.entries
            .sortedByDescending { it.key }
            .map { (dateKey, text) ->
                val j = parseDateKey(dateKey)
                NoteRow(dateKey, text, "${formatWeekday(j.jy, j.jm, j.jd)} · ${formatJalali(j.jy, j.jm, j.jd)}")
            }
    }
    val filtered = remember(rows, q) {
        if (q.isBlank()) rows else rows.filter {
            it.text.contains(q.trim()) || it.dateKey.contains(q.trim())
        }
    }

    /* v2.2.3: `visible` flag → SkSheet owns the open/close slide animation */
    SkSheet(visible = visible, onDismiss = onDismiss, title = "یادداشت‌های من") {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {

            /* search field — v2.2.4: full glass recipe */
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
                            if (q.isEmpty()) Text("جستجو در یادداشت‌ها…", fontSize = 14.sp, fontFamily = IRANSansX, color = sk.textFaint)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            /* Desktop Backup & Restore Row */
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.primarySoft)
                        .border(1.dp, sk.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickableNoRipple {
                            scope.launch {
                                val jsonText = vm.notesRepo.exportJson()
                                val saved = withContext(Dispatchers.Default) {
                                    saveTextFileWithDialog("shiftkar-notes-backup.json", jsonText)
                                }
                                if (saved != null) {
                                    ToastCenter.show("فایل پشتیبان یادداشت‌ها ذخیره شد")
                                }
                            }
                        }
                        .padding(vertical = 9.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(SkIcons.Download, null, Modifier.size(14.dp), tint = sk.primary)
                    Text("پشتیبان‌گیری (JSON)", Modifier.padding(start = 6.dp), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.primary)
                }

                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.surface)
                        .border(1.dp, sk.borderSoft, RoundedCornerShape(12.dp))
                        .clickableNoRipple {
                            scope.launch {
                                val raw = withContext(Dispatchers.Default) { openTextFileWithDialog() }
                                if (raw != null) {
                                    val count = vm.notesRepo.importJson(raw)
                                    if (count >= 0) {
                                        ToastCenter.show("${toPersianDigits(count)} یادداشت با موفقیت بازیابی شد")
                                    } else {
                                        ToastCenter.show("فرمت فایل پشتیبان نامعتبر است")
                                    }
                                }
                            }
                        }
                        .padding(vertical = 9.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(SkIcons.Refresh, null, Modifier.size(14.dp), tint = sk.textMuted)
                    Text("بازیابی از فایل", Modifier.padding(start = 6.dp), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(56.dp).glass(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Notes, null, Modifier.size(24.dp), tint = sk.primary)
                    }
                    Text(
                        "هنوز یادداشتی نیست",
                        Modifier.padding(top = 12.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text
                    )
                    Text(
                        "از تقویم روی هر روز بزنید و یادداشت بسازید",
                        Modifier.padding(top = 4.dp),
                        fontSize = 12.sp, fontFamily = IRANSansX, color = sk.textMuted
                    )
                }
            } else {
                /* فهرست یادداشت‌ها داخل بدنهٔ خود Sheet (verticalScroll) رندر می‌شود؛
                 * LazyColumn اینجا مجاز نیست — ارتفاع نامحدود گرفتن باعث کرش
                 * "measured with an infinity maximum height constraints" می‌شد. */
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { row ->
                        val dateKey = row.dateKey
                        val text = row.text
                        Row(
                            Modifier
                                .fillMaxWidth()
                                /* v2.2.4: کارت یادداشت شیشه‌ای + هویت زرد زیر شیشه */
                                .glass(RoundedCornerShape(16.dp), tint = sk.note)
                                .border(1.dp, sk.noteInk.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .clickableNoRipple {
                                    onDismiss()
                                    onOpenDay(dateKey)
                                }
                                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(SkIcons.Calendar, null, Modifier.size(11.dp), tint = sk.noteInk)
                                    Text(
                                        row.label,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX,
                                        color = sk.noteInk
                                    )
                                }
                                Text(
                                    text,
                                    Modifier.padding(top = 4.dp),
                                    fontSize = 13.5.sp, lineHeight = 24.sp, fontFamily = IRANSansX,
                                    color = sk.text, maxLines = 2
                                )
                            }
                            Icon(
                                SkIcons.ChevronLeft, "باز کردن روز",
                                Modifier.size(15.dp), tint = sk.textFaint
                            )
                        }
                    }
                }
            }

            if (notes.isNotEmpty()) {
                Text(
                    "${toPersianDigits(filtered.size)} یادداشت",
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    fontSize = 11.sp, fontFamily = IRANSansX, color = sk.textFaint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
