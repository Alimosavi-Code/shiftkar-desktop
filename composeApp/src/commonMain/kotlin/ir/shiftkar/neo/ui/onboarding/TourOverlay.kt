package ir.shiftkar.neo.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import ir.shiftkar.neo.engine.toPersianDigits
import ir.shiftkar.neo.ui.theme.IRANSansX
import ir.shiftkar.neo.ui.theme.LocalSk

/**
 * Guided tour — 5-step spotlight tour (tour.tsx port).
 * The spotlight is a full-screen dim layer with a rounded-rect CUTOUT
 * (Path even-odd — the Compose equivalent of the box-shadow 200vmax trick),
 * and the tooltip is anchored right under/above the spotlight box.
 * Targets register their bounds via TourTarget modifiers.
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
    TourStep("bottom-nav", "همه بخش‌ها اینجاست", "سامانه‌های شرکی، لوحه شیفت سالانه و تنظیمات شخصی‌سازی از این نوار در دسترس‌اند.", TourStep.Placement.ABOVE)
)

/** Registry: tour target key -> global bounds. */
object TourRegistry {
    val bounds = androidx.compose.runtime.mutableStateMapOf<String, Rect>()
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
    val step = TOUR_STEPS[stepIndex]
    val box = TourRegistry.bounds[step.targetKey]

    /* چانهٔ آموزش هرگز نباید کاربر را گیر بیندازد: اگر هدف پیدا نشود یا کاربر
     * جای خالی لمس کند، لمس = مرحلهٔ بعد (همان نقش دکمهٔ بعدی). */
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null
            ) { onNext() }
    ) {
        /* spotlight: dim layer with cutout */
        Canvas(Modifier.fillMaxSize()) {
            val full = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
            if (box != null) {
                val hole = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            box.left - 10f, box.top - 10f, box.right + 10f, box.bottom + 10f,
                            22f, 22f
                        )
                    )
                }
                val cut = Path().apply { op(full, hole, PathOperation.Difference) }
                drawPath(cut, Color.Black.copy(alpha = 0.74f))
            } else {
                drawRect(Color.Black.copy(alpha = 0.74f))
            }
        }

        if (box != null) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val gap = with(density) { 12.dp.toPx() }
            val tooltipHeightEst = with(density) { 210.dp.toPx() }
            val screenH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            val below = step.placement == TourStep.Placement.BELOW
            val tooltipTop = if (below) {
                minOf(box.bottom + gap, screenH - tooltipHeightEst)
            } else {
                maxOf(box.top - gap - tooltipHeightEst, 12f)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset { IntOffset(0, tooltipTop.toInt()) }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(sk.sheetSolid)
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
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick
        )
    )
