package com.BWPStudio.JITAIWizard.ui.odi

import android.content.Context
import com.BWPStudio.JITAIWizard.R

/**
 * ODI's mood-based dialogue lines, sourced from `<string-array>` resources so they can
 * be localized. This is a plain Kotlin object with no Context of its own, so callers
 * must supply one (typically `LocalContext.current` from a composable).
 */
object OdiDialogues {

    private fun arrayResFor(state: OdiAnimationState): Int = when (state) {
        OdiAnimationState.IDLE -> R.array.odi_lines_idle
        OdiAnimationState.TALKING -> R.array.odi_lines_talking
        OdiAnimationState.THINKING -> R.array.odi_lines_thinking
        OdiAnimationState.CELEBRATING -> R.array.odi_lines_celebrating
        OdiAnimationState.HAPPY -> R.array.odi_lines_happy
        OdiAnimationState.SURPRISED -> R.array.odi_lines_surprised
        OdiAnimationState.SLEEPING -> R.array.odi_lines_sleeping
        OdiAnimationState.CONCERNED -> R.array.odi_lines_concerned
    }

    fun forState(context: Context, state: OdiAnimationState): String =
        context.resources.getStringArray(arrayResFor(state)).random()
}
