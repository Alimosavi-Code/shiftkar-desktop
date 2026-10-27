package com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

/**
 * Guided tour — 5-step spotlight tour with exact local coordinate resolution.
 * Uses `overlayCoords.localBoundingBoxOf(targetCoords, clipBounds = false)`
 * (and root-offset translation fallback) so the spotlight cutout is 100% pixel-accurate
 * regardless of top status bars, window resizing, or display scaling.
 */

data class TourStep(
    val targetKey: String,
    val title: String,
    val text: String,
    val placement: Placement
) {
    enum class Placement { BELOW, ABOVE }
}

val TOUR_STEPS = listOf(
    TourStep("today-banner", "شیفت امروز شما", "هر روز که وارد شوید، شیفت گروه شما اینجا خودکار محاسبه و نمایش داده می‌شود.", TourStep.Placement.BELOW),
    TourStep("month-nav", "جابه‌جایی بین ماه‌ها", "با فلش‌ها ماه را عوض کنید، روی نام ماه بزنید تا مستقیم انتخاب کنید، یا روی صفحه بکشید. دکمهٔ یادداشت‌ها هم اینجاست.", TourStep.Placement.BELOW),
    TourStep("group-filter", "فیلتر گروه", "شیفت گروه خودتان را ببینید یا با «همه» وضعیت هر چهار گروه را مقایسه کنید.", TourStep.Placement.BELOW),
    TourStep("view-toggle", "دو نمای تقویم", "نمای «تقویم» مثل یک تقویم کلاسیک است؛ نمای «جدولی» شیفت هر چهار گروه را ردیف‌به‌ردیف نشان می‌دهد و حالت تمام‌صفحه هم دارد.", TourStep.Placement.BELOW),
    TourStep("bottom-nav", "همه بخش‌ها اینجاست", "سامانه‌های شرکتی، لوحه شیفت سالانه و تنظیمات شخصی‌سازی از این نوار در دسترس‌اند.", TourStep.Placement.ABOVE)
)

/** Registry: tour target key -> live LayoutCoordinates & root bounds. */
object TourRegistry {
    val coords = mutableStateMapOf<String, LayoutCoordinates>()
    val bounds = mutableStateMapOf<String, Rect>()
    var version by mutableIntStateOf(0)
}

@Composable
fun TourOverlay(
    active: Boolean,
    stepIndex: Int,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    if (!active) return
    val sk = LocalSk.current
    val density = LocalDensity.current
    val step = TOUR_STEPS[stepIndex.coerceIn(0, TOUR_STEPS.lastIndex)]

    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var overlayRootPos by remember { mutableStateOf(Offset.Zero) }
    var tooltipHeightPx by remember { mutableFloatStateOf(with(density) { 195.dp.toPx() }) }

    // Recompute local target bounds whenever step, window size, density, or target coordinates update
    val registryVersion = TourRegistry.version
    val targetCoords = TourRegistry.coords[step.targetKey]
    val rawRootBounds = TourRegistry.bounds[step.targetKey]

    val box: Rect? = remember(step.targetKey, registryVersion, overlayCoords, overlayRootPos, targetCoords, rawRootBounds) {
        val ov = overlayCoords
        val tc = targetCoords
        if (ov != null && ov.isAttached && tc != null && tc.isAttached) {
            runCatching { ov.localBoundingBoxOf(tc, clipBounds = false) }.getOrNull()
        } else if (rawRootBounds != null) {
            rawRootBounds.translate(-overlayRootPos.x, -overlayRootPos.y)
        } else {
            null
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { c ->
                if (c.isAttached) {
                    overlayCoords = c
                    overlayRootPos = c.positionInRoot()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onNext() }
    ) {
        val padPx = with(density) { 6.dp.toPx() }
        val radiusPx = with(density) { 18.dp.toPx() }
        val strokePx = with(density) { 2.dp.toPx() }

        /* spotlight: dim layer with exact local cutout + primary highlight ring */
        Canvas(Modifier.fillMaxSize()) {
            val full = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            if (box != null) {
                val left = (box.left - padPx).coerceAtLeast(2f)
                val top = (box.top - padPx).coerceAtLeast(2f)
                val right = (box.right + padPx).coerceAtMost(size.width - 2f)
                val bottom = (box.bottom + padPx).coerceAtMost(size.height - 2f)

                val hole = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    )
                }
                val cut = Path().apply { op(full, hole, PathOperation.Difference) }
                drawPath(cut, Color.Black.copy(alpha = 0.74f))

                // Crisp highlight ring around the exact target
                drawRoundRect(
                    color = sk.primary.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            } else {
                drawRect(Color.Black.copy(alpha = 0.74f))
            }
        }

        if (box != null) {
            val gap = with(density) { 14.dp.toPx() }
            val marginPx = with(density) { 12.dp.toPx() }
            val screenH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            // Automatically flip placement if there isn't enough vertical space on the preferred side
            val preferBelow = step.placement == TourStep.Placement.BELOW
            val spaceBelow = screenH - (box.bottom + padPx + gap)
            val spaceAbove = (box.top - padPx - gap)
            val placeBelow = if (preferBelow) {
                spaceBelow >= tooltipHeightPx || spaceBelow >= spaceAbove
            } else {
                spaceAbove < tooltipHeightPx && spaceBelow > spaceAbove
            }

            val tooltipTop = if (placeBelow) {
                (box.bottom + padPx + gap).coerceIn(marginPx, (screenH - tooltipHeightPx - marginPx).coerceAtLeast(marginPx))
            } else {
                (box.top - padPx - gap - tooltipHeightPx).coerceIn(marginPx, (screenH - tooltipHeightPx - marginPx).coerceAtLeast(marginPx))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset { IntOffset(0, tooltipTop.toInt()) },
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .onGloballyPositioned { c ->
                            if (c.size.height > 0) {
                                tooltipHeightPx = c.size.height.toFloat()
                            }
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(sk.sheetSolid)
                        .border(1.dp, sk.border, RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consume clicks inside tooltip card */ }
                        .padding(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${toPersianDigits(stepIndex + 1)} / ${toPersianDigits(TOUR_STEPS.size)}",
                            Modifier
                                .clip(RoundedCornerShape(100))
                                .background(sk.primarySoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.primary
                        )
                        Text(
                            "پایان آموزش",
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickableNoRippleCompat(onFinish)
                                .padding(4.dp),
                            fontSize = 12.sp, fontFamily = IRANSansX, color = sk.textFaint
                        )
                    }
                    Text(
                        step.title,
                        Modifier.padding(top = 8.dp),
                        fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                    )
                    Text(
                        step.text,
                        Modifier.padding(top = 6.dp),
                        fontSize = 13.sp, lineHeight = 24.sp, fontFamily = IRANSansX, color = sk.textMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(sk.primary)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickableNoRippleCompat(onNext),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (stepIndex >= TOUR_STEPS.size - 1) "فهمیدم، شروع کنیم" else "بعدی",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.sheetSolid
                        )
                    }
                }
            }
        }
    }
}

/** Local no-ripple clickable that avoids importing the whole components package. */
private fun Modifier.clickableNoRippleCompat(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = onClick
        )
    )
