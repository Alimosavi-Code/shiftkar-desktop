package com.seyedhosseinmousavisaeedi.shiftkar.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

/**
 * Guided tour (v6.7.3) — 6-step spotlight tour with smart non-overlapping tooltip positioning:
 *  1) today-banner  — وضعیت شیفت امروز شما
 *  2) month-nav     — جابه‌جایی ماه، یادداشت‌ها و نمای جدولی
 *  3) group-filter  — فیلتر گروه، مقایسهٔ استراحت مشترک و خروجی تصویر
 *  4) today-cell    — نشانگر روزِ شیفت (خورشید ۱ / ماه ۲)، اضافه‌کاری و جابه‌جایی
 *  5) summary-strip — نوار خلاصهٔ ماهانهٔ نوبت‌ها و تعطیلات
 *  6) bottom-nav    — لوحه کلاسیک، مدیریت شیفت و پرسنل، و تنظیمات
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
    TourStep(
        "today-banner",
        "وضعیت شیفت امروز شما",
        "در این کارت، نوبت امروز شما (صبح‌کار، عصرکار، شب‌کار یا استراحت) به‌همراه شمارهٔ روز نوبت، ساعت اضافه‌کاری یا جابه‌جایی ثبت‌شده نمایش داده می‌شود. با کلیک روی آن، جزئیات کامل امروز باز می‌شود.",
        TourStep.Placement.BELOW
    ),
    TourStep(
        "month-nav",
        "جابه‌جایی ماه، یادداشت‌ها و نمای جدولی",
        "با فلش‌ها یا کشیدن ماوس بین ماه‌ها جابه‌جا شوید، روی نام ماه کلیک کنید تا سریع به هر سال و ماهی بروید، از آیکون یادداشت‌ها همهٔ یادداشت‌ها را مرور کنید یا بین نمای شبکه‌ای و جدولی تمام‌صفحه سوئیچ نمایید.",
        TourStep.Placement.BELOW
    ),
    TourStep(
        "group-filter",
        "فیلتر گروه، مقایسهٔ دو شیفت و خروجی عکس",
        "بین گروه‌ها یا حالت «همه» جابه‌جا شوید. نکتهٔ کاربردی: با نگه‌داشتن کلیک روی یک گروه دیگر، روزهای استراحت مشترک دو شیفت در تقویم هایلایت می‌شود! دکمهٔ تصویر هم خروجی PNG ماه را می‌سازد.",
        TourStep.Placement.BELOW
    ),
    TourStep(
        "today-cell",
        "نشانگر روزِ شیفت، اضافه‌کاری و جابه‌جایی",
        "در هر خانهٔ تقویم، آیکون شیفت به‌همراه شمارهٔ روز آن نوبت (مثلاً خورشید ۱ یا ماه ۲) دیده می‌شود. با کلیک روی هر روز می‌توانید یادداشت، ساعت اضافه‌کاری و جابه‌جایی شیفت ثبت کنید و وضعیت همکاران را ببینید.",
        TourStep.Placement.ABOVE
    ),
    TourStep(
        "summary-strip",
        "خلاصهٔ آماری ماه در یک نگاه",
        "در پایین تقویم، تعداد کل روزهای صبح‌کار، عصرکار، شب‌کار، تعطیلات رسمی و استراحتِ شما در آن ماه به‌صورت زنده شمارش و نمایش داده می‌شود.",
        TourStep.Placement.ABOVE
    ),
    TourStep(
        "bottom-nav",
        "لوحه کلاسیک، مدیریت شیفت و پرسنل",
        "از نوار پایین به «لوحه» (شامل لوحه کلاسیک سالانه با خروجی تصویر)، تب «مدیریت شیفت» (۴۵ الگوی آماده، ساخت شیفت سفارشی با قابلیت افزودن/حذف گروه‌ها، و مدیریت همکاران) و «تنظیمات» دسترسی دارید.",
        TourStep.Placement.ABOVE
    )
)

/** Registry: tour target key -> global bounds. */
object TourRegistry {
    val bounds = mutableStateMapOf<String, Rect>()
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
    val step = TOUR_STEPS[stepIndex.coerceIn(0, TOUR_STEPS.lastIndex)]
    val box = TourRegistry.bounds[step.targetKey]
    val density = LocalDensity.current
    var measuredTooltipH by remember { mutableIntStateOf(with(density) { 215.dp.toPx().toInt() }) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onNext() }
    ) {
        val screenH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        /* spotlight: dim layer with glowing primary ring around cutout */
        val ringColor = sk.primary
        Canvas(Modifier.fillMaxSize()) {
            val full = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            if (box != null) {
                val pad = 8f
                val l = (box.left - pad).coerceAtLeast(4f)
                val t = (box.top - pad).coerceAtLeast(4f)
                val r = (box.right + pad).coerceAtMost(size.width - 4f)
                val b = (box.bottom + pad).coerceAtMost(size.height - 4f)
                val hole = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(l, t, r, b, 20f, 20f)
                    )
                }
                val cut = Path().apply { op(full, hole, PathOperation.Difference) }
                drawPath(cut, Color.Black.copy(alpha = 0.76f))
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(l, t),
                    size = Size((r - l).coerceAtLeast(1f), (b - t).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3.5f)
                )
            } else {
                drawRect(Color.Black.copy(alpha = 0.76f))
            }
        }

        val gap = with(density) { 16.dp.toPx() }
        val safeTop = with(density) { 54.dp.toPx() }
        val safeBottom = screenH - measuredTooltipH - with(density) { 24.dp.toPx() }

        val tooltipTop = if (box != null) {
            val roomBelow = screenH - box.bottom - gap - with(density) { 24.dp.toPx() }
            val roomAbove = box.top - gap - safeTop
            val preferBelow = step.placement == TourStep.Placement.BELOW
            when {
                preferBelow && roomBelow >= measuredTooltipH -> box.bottom + gap
                !preferBelow && roomAbove >= measuredTooltipH -> box.top - gap - measuredTooltipH
                roomBelow >= roomAbove -> (box.bottom + gap).coerceIn(safeTop, safeBottom.coerceAtLeast(safeTop))
                else -> (box.top - gap - measuredTooltipH).coerceIn(safeTop, safeBottom.coerceAtLeast(safeTop))
            }
        } else {
            ((screenH - measuredTooltipH) / 2f).coerceAtLeast(safeTop)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, tooltipTop.toInt()) }
                .onSizeChanged { if (it.height > 0) measuredTooltipH = it.height }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(sk.sheetSolid)
                    .border(1.5.dp, sk.primary.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "گام ${toPersianDigits(stepIndex + 1)} از ${toPersianDigits(TOUR_STEPS.size)}",
                        Modifier
                            .clip(RoundedCornerShape(100))
                            .background(sk.primarySoft)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 11.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.primary
                    )
                    Text(
                        "پایان آموزش",
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickableNoRippleCompat(onFinish)
                            .padding(4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.textFaint
                    )
                }
                Text(
                    step.title,
                    Modifier.padding(top = 8.dp),
                    fontSize = 15.5.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text
                )
                Text(
                    step.text,
                    Modifier.padding(top = 6.dp),
                    fontSize = 12.5.sp, lineHeight = 22.sp, fontFamily = IRANSansX, color = sk.textMuted
                )
                Spacer(Modifier.height(14.dp))
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

private fun Modifier.clickableNoRippleCompat(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick
        )
    )
