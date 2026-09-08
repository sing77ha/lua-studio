package com.luastudio.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Editor content uses a monospace family; UI chrome uses the default sans.
val EditorFontFamily = FontFamily.Monospace

val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp)
)
