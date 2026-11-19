package ir.wazhebox.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import ir.wazhebox.R

/**
 * Vazirmatn (variable) for Persian UI text, Inter for English words.
 * Fonts ship inside the APK (res/font) — nothing is downloaded.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

// Inter ships as a single variable font file; Compose synthesizes the heavier
// weights so the APK stays small (PRD §21: AAB ≤ 20MB).
val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
)

private val PersianLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val WazheBoxTypography: Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    headlineMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    headlineSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    titleLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    titleMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    bodyLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    labelMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
    labelSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 18.sp,
        lineHeightStyle = PersianLineHeightStyle,
    ),
)

/** English word on the review card: Inter, 32sp/600 (PRD §18.2). */
val EnglishWordStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 32.sp,
    lineHeight = 44.sp,
)

/** Persian meaning on the review card: Vazirmatn 24sp/500. */
val PersianMeaningStyle = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 38.sp,
    lineHeightStyle = PersianLineHeightStyle,
)
