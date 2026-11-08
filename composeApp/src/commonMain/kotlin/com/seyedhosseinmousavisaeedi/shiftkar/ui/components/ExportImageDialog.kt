package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import com.seyedhosseinmousavisaeedi.shiftkar.util.Bitmap
import com.seyedhosseinmousavisaeedi.shiftkar.util.ImageExporter
import com.seyedhosseinmousavisaeedi.shiftkar.util.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen popup modal for previewing, copying to clipboard (`Ctrl+V` in Telegram/WhatsApp Desktop),
 * or saving an exported PNG image on Desktop (`CalendarScreen`, `RosterScreen`, `ShiftManagerScreen`).
 */
@Composable
fun ExportImageDialog(
    visible: Boolean,
    title: String,
    fileName: String,
    hapticOn: Boolean = true,
    modeKey: Any? = null,
    headerExtra: (@Composable () -> Unit)? = null,
    produceBitmap: () -> Bitmap,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sk = LocalSk.current
    val dark = sk.sheetSolid.luminance() < 0.5f
    val scrollState = rememberScrollState()

    var bounds by remember { mutableStateOf(Rect.Zero) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(220, easing = CubicBezierEasing(0.22f, 0.8f, 0.36f, 1f)))
    }

    LaunchedEffect(title, fileName, modeKey) {
        bitmap = null
        val bmp = withContext(Dispatchers.Default) {
            runCatching { produceBitmap() }.getOrNull()
        }
        bitmap = bmp
        delay(60)
        runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x9E081024))
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (bounds != Rect.Zero && !bounds.contains(pos)) onDismiss()
                    }
                }
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 700.dp)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
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
                                endY = size.height * 0.42f
                            )
                        )
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* swallow taps inside modal */ }
                    .border(1.dp, sk.border, RoundedCornerShape(26.dp))
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(sk.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkIcons.Image, null, Modifier.size(19.dp), tint = sk.primary)
                    }
                    Text(
                        title,
                        Modifier.weight(1f).padding(start = 10.dp),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    SheetButton(icon = SkIcons.Close, desc = "بستن") { onDismiss() }
                }

                if (headerExtra != null) {
                    Spacer(Modifier.height(10.dp))
                    headerExtra()
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 380.dp)
                        .glass(RoundedCornerShape(18.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = remember(bmp) { bmp.asImageBitmap() },
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(
                            "در حال آماده‌سازی تصویر شیشه‌ای…",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IRANSansX,
                            color = sk.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                ExportActionButtons(
                    bitmap = bitmap,
                    fileName = fileName,
                    hapticOn = hapticOn,
                    onDone = onDismiss
                )
            }
        }
    }
}

/**
 * Inline glass export card for inside SkSheet (`DaySheet`).
 */
@Composable
fun InlineExportCard(
    visible: Boolean,
    title: String,
    fileName: String,
    hapticOn: Boolean = true,
    produceBitmap: () -> Bitmap,
    onReadyToScrollBottom: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sk = LocalSk.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(title, fileName) {
        onReadyToScrollBottom()
        val bmp = withContext(Dispatchers.Default) {
            runCatching { produceBitmap() }.getOrNull()
        }
        bitmap = bmp
        delay(50)
        onReadyToScrollBottom()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(sk.primarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.Image, null, Modifier.size(18.dp), tint = sk.primary)
            }
            Text(
                title,
                Modifier.weight(1f).padding(start = 10.dp),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IRANSansX,
                color = sk.text
            )
            SheetButton(icon = SkIcons.Close, desc = "بستن") { onDismiss() }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(sk.surface)
                .border(1.dp, sk.borderSoft, RoundedCornerShape(14.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Text(
                    "در حال آماده‌سازی تصویر…",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX,
                    color = sk.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ExportActionButtons(
            bitmap = bitmap,
            fileName = fileName,
            hapticOn = hapticOn,
            onDone = onDismiss
        )
    }
}

@Composable
private fun ExportActionButtons(
    bitmap: Bitmap?,
    fileName: String,
    hapticOn: Boolean,
    onDone: () -> Unit
) {
    val sk = LocalSk.current
    val haptic = rememberHaptics(hapticOn)
    val scope = rememberCoroutineScope()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Button 1: Copy Image directly to OS Clipboard (for Ctrl+V in Telegram/WhatsApp Desktop)
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(sk.primary)
                .clickableNoRipple(enabled = bitmap != null) {
                    val bmp = bitmap ?: return@clickableNoRipple
                    haptic.tick(12)
                    scope.launch(Dispatchers.Default) {
                        val ok = ImageExporter.copyBitmapToClipboard(bmp)
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                ToastCenter.show("تصویر در کلیپ‌بورد کپی شد (با Ctrl+V جای‌گذاری کنید)")
                            } else {
                                ToastCenter.show("خطا در کپی تصویر در کلیپ‌بورد")
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(SkIcons.Share, null, Modifier.size(16.dp), tint = Color.White)
            Text(
                "کپی در کلیپ‌بورد",
                Modifier.padding(start = 7.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = Color.White
            )
        }

        // Button 2: Save PNG File with OS Save Dialog
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .glass(RoundedCornerShape(14.dp), tint = sk.primary.copy(alpha = 0.12f))
                .border(1.5.dp, sk.primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .clickableNoRipple(enabled = bitmap != null) {
                    val bmp = bitmap ?: return@clickableNoRipple
                    haptic.tick(12)
                    scope.launch(Dispatchers.Default) {
                        val savedPath = ImageExporter.saveBitmapWithDialog(bmp, fileName)
                        withContext(Dispatchers.Main) {
                            if (savedPath != null) {
                                ToastCenter.show("تصویر با موفقیت ذخیره شد")
                                onDone()
                            }
                        }
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(SkIcons.Download, null, Modifier.size(16.dp), tint = sk.primary)
            Text(
                "ذخیرهٔ تصویر (PNG)",
                Modifier.padding(start = 7.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = IRANSansX,
                color = sk.primary
            )
        }
    }
}
