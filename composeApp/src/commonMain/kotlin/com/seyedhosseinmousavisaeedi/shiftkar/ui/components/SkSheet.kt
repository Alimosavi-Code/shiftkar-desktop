package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

private val SheetEnterEase = CubicBezierEasing(0.22f, 0.8f, 0.36f, 1f)

/**
 * Desktop-optimized Popup Sheet / Modal Dialog:
 * Opens as a centered glass popup (`Dialog`) on top of the current screen so that
 * Pattern Editing (`ShiftManagerScreen`), Day Details (`DaySheet`), Month Picker (`MonthPickerSheet`),
 * and Notes (`NotesSheet`) always float as a clean popup modal with a close button (`✕`)
 * and backdrop click-to-dismiss instead of expanding inline inside the page.
 */
@Composable
fun SkSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    expandable: Boolean = true,
    bodyScroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return

    val sk = LocalSk.current
    val dark = sk.sheetSolid.luminance() < 0.5f
    var sheetBounds by remember { mutableStateOf(Rect.Zero) }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(220, easing = SheetEnterEase))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xA6070E20))
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (sheetBounds != Rect.Zero && !sheetBounds.contains(pos)) {
                            onDismiss()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .heightIn(max = 690.dp)
                    .onGloballyPositioned { sheetBounds = it.boundsInRoot() }
                    .graphicsLayer {
                        alpha = intro.value
                        val sc = 0.94f + 0.06f * intro.value
                        scaleX = sc
                        scaleY = sc
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(sk.sheetSolid)
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    sk.primary.copy(alpha = if (dark) 0.20f else 0.12f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.38f
                            )
                        )
                    }
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(sk.specular.copy(alpha = 0.12f), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.22f
                            )
                        )
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* swallow taps inside modal */ }
                    .border(
                        width = 1.2.dp,
                        color = sk.border,
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Spacer(Modifier.height(10.dp))

                // Top decorative handle pill
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(100))
                        .background(sk.textFaint.copy(alpha = 0.45f))
                )

                // Modal header with Title + always-visible Close button (✕)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp)
                        .heightIn(min = 38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IRANSansX,
                        color = sk.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SheetButton(icon = SkIcons.Close, desc = "بستن") { onDismiss() }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(sk.borderSoft))
                Spacer(Modifier.height(8.dp))

                // Scrollable modal body
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(bodyScroll)
                        .padding(bottom = 12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun SheetButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    val sk = LocalSk.current
    Box(
        Modifier
            .size(36.dp)
            .glass(RoundedCornerShape(12.dp))
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(17.dp), tint = sk.textMuted)
    }
}
