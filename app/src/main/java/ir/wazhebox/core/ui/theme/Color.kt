package ir.wazhebox.core.ui.theme

import androidx.compose.ui.graphics.Color

// v2 palette: a bright indigo + teal pair on pure white. The previous warm
// paper/green scheme read as "study book"; this one is lighter and more
// energetic, and every surface container is tuned so the white background
// still has visible sheets on top of it.
internal val IndigoPrimary = Color(0xFF5B4BE1)
internal val IndigoPrimaryDark = Color(0xFFB9AEFF)
internal val TealSecondary = Color(0xFF0F9D8A)
internal val TealSecondaryDark = Color(0xFF6BD9C6)

internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceDark = Color(0xFF0F1014)
internal val OnSurfaceLight = Color(0xFF14151B)
internal val OnSurfaceDark = Color(0xFFE7E7EE)

internal val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val SurfaceContainerLowLight = Color(0xFFF7F8FC)
internal val SurfaceContainerHighLight = Color(0xFFEFF1F9)
internal val SurfaceContainerHighestLight = Color(0xFFE7EAF4)
internal val SurfaceContainerLowestDark = Color(0xFF14161C)
internal val SurfaceContainerLowDark = Color(0xFF171922)
internal val SurfaceContainerHighDark = Color(0xFF262935)
internal val SurfaceContainerHighestDark = Color(0xFF30343F)

internal val PrimaryContainerLight = Color(0xFFEAE6FF)
internal val OnPrimaryContainerLight = Color(0xFF1C1454)
internal val PrimaryContainerDark = Color(0xFF372C93)
internal val OnPrimaryContainerDark = Color(0xFFE8E3FF)
internal val SecondaryContainerLight = Color(0xFFD7F5EE)
internal val OnSecondaryContainerLight = Color(0xFF003731)
internal val SecondaryContainerDark = Color(0xFF0B4D44)
internal val OnSecondaryContainerDark = Color(0xFFC4F5EC)
internal val SurfaceVariantLight = Color(0xFFF1F2F8)
internal val OnSurfaceVariantLight = Color(0xFF585E70)
internal val SurfaceVariantDark = Color(0xFF24262F)
internal val OnSurfaceVariantDark = Color(0xFFC3C6D2)
internal val OutlineLight = Color(0xFFA7ACBC)
internal val OutlineDark = Color(0xFF8E93A3)
internal val OutlineVariantLight = Color(0xFFD6D9E4)
internal val OutlineVariantDark = Color(0xFF3A3E4A)
internal val ErrorLight = Color(0xFFD32F2F)
internal val ErrorDark = Color(0xFFF2B8B5)

// Two accents outside the Material scheme: amber for "hard", sky for "easy".
// They stay distinguishable in the four review ratings without inventing a
// fifth semantic colour (PRD §18.6: never colour alone, always with a label).
internal val WarmAccentLight = Color(0xFFD97706)
internal val WarmAccentDark = Color(0xFFFFC46B)
internal val CoolAccentLight = Color(0xFF0284C7)
internal val CoolAccentDark = Color(0xFF8CCDF5)
