package ir.wazhebox.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLowLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceDim = Color(0xFFDBDFEB),
    surfaceBright = Color.White,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = Color(0xFF231A5E),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerLowDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
)

// AMOLED: the background and the base surface go to true black so an OLED panel
// switches those pixels off, while the container tones stay a hair above black
// so sheets and cards are still distinguishable.
private val AmoledScheme = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF08090D),
    surfaceContainer = Color(0xFF0B0C11),
    surfaceContainerHigh = Color(0xFF12141A),
    surfaceContainerHighest = Color(0xFF191B23),
    surfaceVariant = Color(0xFF15171E),
    surfaceDim = Color.Black,
)

private val WazheBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Colours the Material scheme does not carry; provided through the theme. */
@Immutable
data class WazheBoxAccents(
    val warm: Color,
    val cool: Color,
)

private val LightAccents = WazheBoxAccents(warm = WarmAccentLight, cool = CoolAccentLight)
private val DarkAccents = WazheBoxAccents(warm = WarmAccentDark, cool = CoolAccentDark)

private val LocalAccents = staticCompositionLocalOf { LightAccents }

/** `MaterialTheme.accents.warm` / `.cool` inside any composable. */
val MaterialTheme.accents: WazheBoxAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalAccents.current

/**
 * App theme: always RTL (the only UI language in 1.0 is Persian, PRD §23).
 * [darkTheme] follows the user's choice (MainActivity reads it from settings),
 * and [amoled] swaps the dark palette for a true-black one. Dynamic colour stays
 * off because it would replace the brand palette on Android 12+.
 */
@Composable
fun WazheBoxTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme && amoled -> AmoledScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalAccents provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WazheBoxTypography,
            shapes = WazheBoxShapes,
            content = content,
        )
    }
}
