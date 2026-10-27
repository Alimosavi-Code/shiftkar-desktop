package com.seyedhosseinmousavisaeedi.shiftkar.ui.updater

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.engine.toPersianDigits
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

/**
 * صفحهٔ پشتیبانِ نصب (v2.2.9) — مالک: «if the apk failed to install in every
 * way this plain screen pop up! … And after that as a safety It should be a
 * screen prompting for installation of the apk!»
 *
 * وقتی پرامپت نصبِ سیستم به هر دلیلی باز نشد، یا کاربر از آن «لغو» زد و به
 * برنامه برگشت، به‌جای بن‌بست، این صفحهٔ تمام‌صفحهٔ شیشه‌ای می‌نشیند و با
 * دکمهٔ بزرگ «نصب» دوباره تلاش می‌کند. اگر بروزرسانی «اجباری» باشد، صفحه
 * بسته نمی‌شود — تا نصب، برنامه قابل استفاده نیست.
 *
 * روی هر چیزی که زیرش باشد (پنجرهٔ بروزرسانی، تب‌ها) می‌نشیند؛ پس همیشه
 * مسیرِ روشنِ «نصب نسخهٔ دانلودشده» در دسترس است.
 */
@Composable
fun InstallPromptScreen(
    versionName: String?,
    forced: Boolean,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val sk = LocalSk.current



    /* انیمیشن ورود — همان زبان پنجرهٔ بروزرسانی (مقیاس + شفافیت) */
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(260, easing = CubicBezierEasing(0.22f, 0.8f, 0.36f, 1f)))
    }

    Box(
        Modifier
            .fillMaxSize()
            /* تیره‌تر از پنجرهٔ بروزرسانی — این یک «صفحه» است، نه popover */
            .background(Color(0xCC081024))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* ضربه‌های بیرون جذب می‌شوند — بستن فقط با دکمه/بازگشت */ }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = intro.value
                    val sc = 0.94f + 0.06f * intro.value
                    scaleX = sc
                    scaleY = sc
                }
                .clip(RoundedCornerShape(28.dp))
                .background(sk.sheetSolid)
                .background(Brush.verticalGradient(listOf(sk.sheetBg, sk.sheetBg)))
                .border(1.dp, sk.border, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /* نشان آیکون — حلقهٔ سبزِ «آمادهٔ نصب» */
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(sk.primarySoft)
                    .border(2.dp, sk.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(SkIcons.Download, null, Modifier.size(28.dp), tint = sk.primary)
            }

            Text(
                "نصب بروزرسانی",
                Modifier.padding(top = 14.dp),
                fontSize = 18.sp, fontWeight = FontWeight.Black,
                fontFamily = IRANSansX, color = sk.text, textAlign = TextAlign.Center
            )

            Text(
                if (versionName.isNullOrBlank())
                    "نسخهٔ جدید برنامه دانلود شده است."
                else
                    "نسخهٔ جدید ${toPersianDigits(versionName)} دانلود شده است.",
                Modifier.padding(top = 8.dp),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = IRANSansX, color = sk.primary, textAlign = TextAlign.Center
            )
            Text(
                "فایل نصبی نسخهٔ جدید در پوشهٔ بروزرسانی ذخیره شده است. با دکمهٔ زیر نصاب برنامه را اجرا کنید.",
                Modifier.padding(top = 6.dp),
                fontSize = 12.sp, lineHeight = 21.sp,
                fontFamily = IRANSansX, color = sk.textMuted, textAlign = TextAlign.Center
            )

            if (forced) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "این بروزرسانی اجباری است — تا نصب نسخهٔ جدید، برنامه قابل استفاده نیست.",
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sk.holidaySoft)
                        .border(1.dp, sk.holiday.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX, color = sk.holiday, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))

            /* دکمهٔ اصلی — نصب */
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(sk.primary)
                    .clickableNoRipple { onInstall() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(SkIcons.Check, null, Modifier.size(18.dp), tint = sk.sheetSolid)
                Text(
                    "نصب نسخهٔ جدید",
                    Modifier.padding(start = 8.dp),
                    fontSize = 14.5.sp, fontWeight = FontWeight.Black,
                    fontFamily = IRANSansX, color = sk.sheetSolid
                )
            }

            if (!forced) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "بستن",
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickableNoRipple { onClose() }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                        .alpha(0.9f),
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    fontFamily = IRANSansX, color = sk.textFaint
                )
            }
        }
    }
}
