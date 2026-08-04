package com.example.jitaicompanion.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Width, in dp, of the watch these layouts were originally tuned on (a 45 mm round watch —
 * 450×450 px at density 2.0). Every hardcoded dimension in this module was chosen to look
 * right at this width, so it is the natural reference to scale away from.
 */
private const val REFERENCE_WIDTH_DP = 225f

/**
 * Smallest scale we allow. Below roughly 0.8 the Wear Material3 touch targets drop under the
 * 48 dp minimum and controls stop being reliably tappable, so we clamp rather than let a very
 * small screen shrink the UI into unusability.
 */
private const val MIN_SCALE = 0.80f

/** Largest scale, so a big watch gets more breathing room rather than comically large text. */
private const val MAX_SCALE = 1.10f

/**
 * Google's Wear OS breakpoint: screens narrower than this are the "small" class and need
 * tightened padding and a scrollable fallback for anything taller than one screen.
 */
private const val SMALL_BREAKPOINT_DP = 225

/**
 * Screen-size-aware dimensions for the watch UI.
 *
 * Wear OS ships displays from about 192 dp wide (1.2", e.g. Pixel Watch) up to about 227 dp
 * (1.5", 45–46 mm). That is an ~18 % span, and layouts tuned only for the large end clip text
 * and cramp buttons at the small end. Rather than maintain two layouts, every dimension is
 * expressed relative to [REFERENCE_WIDTH_DP] and scaled by [scale].
 *
 * Read it with [LocalWearDimens], or use the [sdp]/[ssp] shorthands.
 */
@Immutable
data class WearDimens(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    /** True on watches below the 225 dp breakpoint, where content must be tightened. */
    val isSmall: Boolean,
    /** Multiplier applied to reference-sized dimensions, clamped to a usable range. */
    val scale: Float,
) {
    /** Scales a dimension that was tuned on the 45 mm reference watch. */
    fun scaled(value: Dp): Dp = value * scale

    /** Scales a font size that was tuned on the 45 mm reference watch. */
    fun scaled(value: TextUnit): TextUnit = value * scale

    /**
     * Horizontal inset that keeps content inside the round display.
     *
     * A round screen clips its corners, so text laid out to the full width loses its first and
     * last characters. ~5.5 % a side is the usual Wear guidance for text blocks; small screens
     * get slightly less so the shrunken text still has room to breathe.
     */
    val horizontalPadding: Dp get() = if (isSmall) 10.dp else 14.dp

    /** Vertical inset for the top/bottom of the round display. */
    val verticalPadding: Dp get() = if (isSmall) 6.dp else 10.dp

    /** Padding for a full-screen content column on a round watch. */
    val contentPadding: PaddingValues
        get() = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = verticalPadding,
            bottom = verticalPadding,
        )

    /** Gap between stacked controls — tighter on small screens, where vertical space is scarcest. */
    val itemSpacing: Dp get() = if (isSmall) 4.dp else 8.dp

    /**
     * Minimum height for a tappable control. Wear's accessibility floor is 48 dp and we never
     * scale below it, however small the watch.
     */
    val minTouchTarget: Dp get() = 48.dp

    /** Body text size, scaled. */
    val bodyTextSize: TextUnit get() = scaled(14.sp)

    /** Title text size, scaled. */
    val titleTextSize: TextUnit get() = scaled(18.sp)

    /** Caption/unit text size, scaled, with a floor so it stays legible. */
    val captionTextSize: TextUnit get() = maxOf(scaled(11.sp).value, 9f).sp
}

/** Provides [WearDimens] to the composition. Defaults to the reference watch if not provided. */
val LocalWearDimens: ProvidableCompositionLocal<WearDimens> = staticCompositionLocalOf {
    WearDimens(
        screenWidthDp = REFERENCE_WIDTH_DP.toInt(),
        screenHeightDp = REFERENCE_WIDTH_DP.toInt(),
        isSmall = false,
        scale = 1f,
    )
}

/**
 * Computes [WearDimens] from the current window and provides it to [content].
 *
 * Wrap the whole screen in this — typically just inside the theme — so every composable below
 * can size itself to the actual watch instead of assuming a 45 mm one.
 */
@Composable
fun ProvideWearDimens(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val dimens = remember(widthDp, heightDp) {
        WearDimens(
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            isSmall = widthDp < SMALL_BREAKPOINT_DP,
            scale = (widthDp / REFERENCE_WIDTH_DP).coerceIn(MIN_SCALE, MAX_SCALE),
        )
    }

    CompositionLocalProvider(LocalWearDimens provides dimens, content = content)
}

/** Shorthand for the active [WearDimens]. */
val wearDimens: WearDimens
    @Composable @ReadOnlyComposable get() = LocalWearDimens.current

/**
 * A dp value tuned on the 45 mm reference watch, scaled to the current one.
 *
 * `16.sdp` replaces a hardcoded `16.dp`.
 */
val Int.sdp: Dp
    @Composable @ReadOnlyComposable get() = LocalWearDimens.current.scaled(this.dp)

/** As [sdp], for fractional values. */
val Double.sdp: Dp
    @Composable @ReadOnlyComposable get() = LocalWearDimens.current.scaled(this.dp)

/**
 * An sp font size tuned on the 45 mm reference watch, scaled to the current one.
 *
 * `14.ssp` replaces a hardcoded `14.sp`.
 */
val Int.ssp: TextUnit
    @Composable @ReadOnlyComposable get() = LocalWearDimens.current.scaled(this.sp)

/** As [ssp], for fractional values. */
val Double.ssp: TextUnit
    @Composable @ReadOnlyComposable get() = LocalWearDimens.current.scaled(this.sp)
