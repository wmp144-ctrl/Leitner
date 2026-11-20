package ir.wazhebox.core.ui.theme

import androidx.compose.ui.graphics.Color

// Base palette from PRD §18.2 — calm green + muted purple, warm paper surface.
internal val GreenPrimaryLight = Color(0xFF2F6B5E)
internal val GreenPrimaryDark = Color(0xFF8FD6C4)
internal val PurpleSecondaryLight = Color(0xFF6B5B95)
internal val PurpleSecondaryDark = Color(0xFFC9BDE8)
internal val SurfaceLight = Color(0xFFFBF9F6)
internal val SurfaceDark = Color(0xFF12140F)
internal val ErrorLight = Color(0xFFB3261E)
internal val ErrorDark = Color(0xFFF2B8B5)

internal val PrimaryContainerLight = Color(0xFFB7E6D9)
internal val OnPrimaryContainerLight = Color(0xFF00201A)
internal val PrimaryContainerDark = Color(0xFF1D4E45)
internal val OnPrimaryContainerDark = Color(0xFFD3F5EA)
internal val SecondaryContainerLight = Color(0xFFE7DFF9)
internal val OnSecondaryContainerLight = Color(0xFF211A38)
internal val SecondaryContainerDark = Color(0xFF443A5C)
internal val OnSecondaryContainerDark = Color(0xFFECE4FF)
internal val SurfaceVariantLight = Color(0xFFEDE9E2)
internal val OnSurfaceVariantLight = Color(0xFF4A4640)
internal val SurfaceVariantDark = Color(0xFF262A22)
internal val OnSurfaceVariantDark = Color(0xFFC8C7BE)
internal val OutlineLight = Color(0xFF7C776E)
internal val OutlineDark = Color(0xFF94918A)
internal val OnSurfaceLight = Color(0xFF1B1C17)
internal val OnSurfaceDark = Color(0xFFE5E3DA)

// Secondary accents outside the Material scheme: warm for "hard", cool for
// "easy". They keep the four review ratings distinguishable without inventing
// a fifth semantic colour (PRD §18.6: never colour alone, always with a label).
internal val WarmAccentLight = Color(0xFFB4550F)
internal val WarmAccentDark = Color(0xFFFFB785)
internal val CoolAccentLight = Color(0xFF3A5FA8)
internal val CoolAccentDark = Color(0xFFA9C2F7)
