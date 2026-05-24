package com.zyro.snapdown.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zyro.snapdown.R

// SF Pro Display — untuk heading & judul besar
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular,  FontWeight.Normal),
    Font(R.font.sf_pro_display_medium,   FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold,     FontWeight.Bold),
    Font(R.font.sf_pro_display_bold,     FontWeight.ExtraBold),
)

// SF Pro Text — untuk body & teks kecil
val SFProText = FontFamily(
    Font(R.font.sf_pro_text_regular,  FontWeight.Normal),
    Font(R.font.sf_pro_text_medium,   FontWeight.Medium),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_text_bold,     FontWeight.Bold),
)

val SnapTypography = Typography(
    // Layar / hero title
    displayLarge  = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.Bold,     fontSize = 34.sp, lineHeight = 41.sp),
    displayMedium = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 34.sp),
    displaySmall  = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),

    // Headline
    headlineLarge  = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.Bold,     fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall  = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),

    // Title
    titleLarge  = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = SFProText,    fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall  = TextStyle(fontFamily = SFProText,    fontWeight = FontWeight.Medium,   fontSize = 13.sp, lineHeight = 18.sp),

    // Body
    bodyLarge  = TextStyle(fontFamily = SFProText, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = SFProText, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall  = TextStyle(fontFamily = SFProText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),

    // Label
    labelLarge  = TextStyle(fontFamily = SFProText, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = SFProText, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall  = TextStyle(fontFamily = SFProText, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp),
)
