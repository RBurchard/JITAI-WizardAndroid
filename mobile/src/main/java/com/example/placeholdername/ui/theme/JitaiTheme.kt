package com.BWPStudio.JITAIWizard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's palette, in one place.
 *
 * These values were already scattered as literals across the participant view, the settings
 * screens and the watch's own UI. Naming them here is what lets the Wizard view look like part
 * of the same product instead of a stock Material demo: the researcher-facing screens were the
 * only ones still running on Compose's default light scheme, because [MaterialTheme] was never
 * applied at the top of the app at all.
 */
object JitaiColors {
    /** Deep navy page background, shared with the participant view and the Control Station. */
    val Background = Color(0xFF0D1B2A)
    /** Slightly lifted navy for bars and grouped rows. */
    val SurfaceBar = Color(0xFF16283A)
    /** Card / list-row fill. */
    val Surface = Color(0xFF1A2A3A)
    /** Card fill for the row that is currently selected or running. */
    val SurfaceHigh = Color(0xFF223549)

    /** Accent blue. Titles, selected states, values. */
    val Accent = Color(0xFF42A5F5)
    /** Muted blue-grey for labels and secondary text. */
    val Muted = Color(0xFF8899BB)

    val Good = Color(0xFF7BD88F)
    val Warn = Color(0xFFE0B457)
    val Bad = Color(0xFFFF6B6B)

    /** Wizard-mode banner. Red enough to read as "not the participant screen" without glaring. */
    val WizardBanner = Color(0xFF3A1620)

    val Outline = Color(0xFF2C4257)
}

private val DarkScheme = darkColorScheme(
    primary = JitaiColors.Accent,
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF1B4A73),
    onPrimaryContainer = Color(0xFFCFE6FF),

    secondary = JitaiColors.Good,
    onSecondary = Color(0xFF04160B),
    secondaryContainer = Color(0xFF1B3A26),
    onSecondaryContainer = Color(0xFFBFF0CB),

    tertiary = JitaiColors.Warn,
    onTertiary = Color(0xFF221703),
    tertiaryContainer = Color(0xFF3A2A14),
    onTertiaryContainer = Color(0xFFFFE2A8),

    background = JitaiColors.Background,
    onBackground = Color(0xFFE6EEF7),
    surface = JitaiColors.Background,
    onSurface = Color(0xFFE6EEF7),
    surfaceVariant = JitaiColors.SurfaceBar,
    onSurfaceVariant = Color(0xFF9BB0C6),

    surfaceContainerLowest = Color(0xFF0A1622),
    surfaceContainerLow = Color(0xFF11202F),
    surfaceContainer = JitaiColors.SurfaceBar,
    surfaceContainerHigh = JitaiColors.Surface,
    surfaceContainerHighest = JitaiColors.SurfaceHigh,
    surfaceTint = JitaiColors.Accent,

    error = JitaiColors.Bad,
    onError = Color(0xFF2A0708),
    errorContainer = JitaiColors.WizardBanner,
    onErrorContainer = Color(0xFFFFD7DA),

    outline = Color(0xFF3B566F),
    outlineVariant = JitaiColors.Outline,
)

private val JitaiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Wraps the whole app.
 *
 * Dark only, deliberately: the participant view, the watch and the Control Station are all dark,
 * and a wizard operating the study in a dim room should not be flashbanged by the one screen
 * that happened to inherit the light default.
 */
@Composable
fun JitaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = JitaiShapes,
        content = content,
    )
}
