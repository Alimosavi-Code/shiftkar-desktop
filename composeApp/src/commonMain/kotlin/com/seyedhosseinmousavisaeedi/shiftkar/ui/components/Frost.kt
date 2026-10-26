package com.seyedhosseinmousavisaeedi.shiftkar.ui.components

import com.seyedhosseinmousavisaeedi.shiftkar.platform.supportsBackdropBlur
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk
import kotlin.math.roundToInt

/**
 * FrostBarBackdrop — «شیشهٔ مات واقعی» برای نوار پایین.
 *
 * تاکنون نوار فقط یک گرادیانت نیمه‌شفاف داشت (بدون blur) و صاحب برنامه آن را
 * «ساده و بدون مات» می‌خواند. راه‌حل بدون کتابخانهٔ خارجی: چون پس‌زمینهٔ پشت
 * نوار همان AuroraBackground خودمان است، یک «رونوشت» از همان آورورا را با
 * آفست دقیق (منهای مختصات نوار در ریشه) داخل نوار می‌کشیم و با
 * Modifier.blur (RenderEffect، API 31+) مات می‌کنیم — یعنی دقیقاً همان
 * backdrop-filter مرورگر. لایهٔ محتوا (آیکون/متن) روی این لایهٔ مات کشیده
 * می‌شود و هیچ‌وقت تار نمی‌شود.
 *
 * API < 31: RenderEffect وجود ندارد (مثل مرورگر بدون backdrop-filter) →
 * به‌جایش لایهٔ tint کمی پررنگ‌تر می‌شود تا خوانایی حفظ شود.
 */
@Composable
fun FrostBarBackdrop(
    auroraSizePx: IntSize,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val sk = LocalSk.current
    val density = LocalDensity.current
    val canBlur = supportsBackdropBlur

    var myTopLeft by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                myTopLeft = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
    ) {
        /* ۱) رونوشت مات‌شدهٔ آورورا — هم‌ترازِ دقیق با پس‌زمینهٔ واقعی
         * v2.2.2 fix: لایهٔ رونوشت باید matchParentSize باشد وگرنه Boxِ «wrap content»
         * به اندازهٔ خودِ آورورا (تمام صفحه) بزرگ می‌شود — رگرسیون ۲.۲.۱ که نوار را
         * به بالای صفحه می‌فرستاد. داخل این لایه، رونوشت با آفست منفی کشیده و با
         * clipToBounds به همین نوار محدود می‌شود. */
        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
        ) {
            if (auroraSizePx != IntSize.Zero) {
                val auroraW = with(density) { auroraSizePx.width.toFloat().toDp() }
                val auroraH = with(density) { auroraSizePx.height.toFloat().toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(-myTopLeft.x, -myTopLeft.y) }
                        .size(auroraW, auroraH)
                        .then(if (canBlur) Modifier.blur(24.dp) else Modifier)
                ) {
                    AuroraBackground(Modifier.fillMaxSize())
                }
            }
        }

        /* ۲) لایهٔ tint شیشه — روی مات، زیر محتوا
         * v6.0.4 (مالک: «Make the bottom bar a bit more glassy! Just a bit»):
         * آلفا از ۰٫۹۴ به ۰٫۹۰ — هنوز هیچ متنی از زیر نوار رد نمی‌شود ولی شیشه
         * نفس بیشتری می‌کشد؛ شکل، خط دور و خط نور دست‌نخورده.
         * (پایه: v6.0.3 — سطحِ کدر ۰٫۹۴ روی همهٔ APIها به‌جای ۰٫۳۸ فقط API<31) */
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(sk.glassHi, sk.glassLo), start = Offset.Zero, end = Offset(0.55f, 1f)))
                    drawRect(sk.sheetSolid.copy(alpha = 0.90f))
                    // لبهٔ نور بالای شیشه (specular، پاریتی CSS)
                    drawLine(
                        color = sk.specular,
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1f
                    )
                }
        )

        /* ۳) خط دور */
        Box(Modifier.matchParentSize().border(1.dp, sk.border, shape))

        /* ۴) محتوا — همیشه تیز. فرزند «معمولی» (نه matchParentSize) تا اندازهٔ
         * نوار را تعیین کند؛ لایه‌های ۱ تا ۳ با matchParentSize به همین اندازه
         * درمی‌آیند (v2.2.2). */
        Box(Modifier, content = content)
    }
}
