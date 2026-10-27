package com.seyedhosseinmousavisaeedi.shiftkar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.platform.loadBundledFontFamily

val IRANSansX: FontFamily by lazy { loadBundledFontFamily() }

val ShiftKarTypography: Typography by lazy {
    Typography(
        displayLarge = TextStyle(fontFamily = IRANSansX, fontSize = 57.sp, fontWeight = FontWeight.Black),
        displayMedium = TextStyle(fontFamily = IRANSansX, fontSize = 45.sp, fontWeight = FontWeight.Black),
        displaySmall = TextStyle(fontFamily = IRANSansX, fontSize = 36.sp, fontWeight = FontWeight.Black),
        headlineLarge = TextStyle(fontFamily = IRANSansX, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
        headlineMedium = TextStyle(fontFamily = IRANSansX, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
        headlineSmall = TextStyle(fontFamily = IRANSansX, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
        titleLarge = TextStyle(fontFamily = IRANSansX, fontSize = 22.sp, fontWeight = FontWeight.Black),
        titleMedium = TextStyle(fontFamily = IRANSansX, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
        titleSmall = TextStyle(fontFamily = IRANSansX, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontFamily = IRANSansX, fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = IRANSansX, fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = IRANSansX, fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = IRANSansX, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        labelMedium = TextStyle(fontFamily = IRANSansX, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        labelSmall = TextStyle(fontFamily = IRANSansX, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    )
}
