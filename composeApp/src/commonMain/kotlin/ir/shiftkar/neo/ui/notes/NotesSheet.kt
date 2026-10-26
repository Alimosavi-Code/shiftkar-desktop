package ir.shiftkar.neo.ui.notes

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
import ir.shiftkar.neo.engine.formatJalali
import ir.shiftkar.neo.engine.formatWeekday
import ir.shiftkar.neo.engine.parseDateKey
import ir.shiftkar.neo.engine.toPersianDigits
import ir.shiftkar.neo.ui.AppViewModel
import ir.shiftkar.neo.ui.components.SkIcons
import ir.shiftkar.neo.ui.components.SkSheet
import ir.shiftkar.neo.ui.components.clickableNoRipple
import ir.shiftkar.neo.ui.components.glass
import ir.shiftkar.neo.ui.theme.IRANSansX
import ir.shiftkar.neo.ui.theme.LocalSk

/**
 * All-notes sheet — searchable list of personal notes (notes-sheet.tsx port).
 */

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

    val filtered = remember(notes, q) {
        val all = notes.entries.sortedByDescending { it.key }
        if (q.isBlank()) all else all.filter {
            it.value.contains(q.trim()) || it.key.contains(q.trim())
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
                    filtered.forEach { (dateKey, text) ->
                        val j = parseDateKey(dateKey)
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
                                        "${formatWeekday(j.jy, j.jm, j.jd)} · ${formatJalali(j.jy, j.jm, j.jd)}",
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
