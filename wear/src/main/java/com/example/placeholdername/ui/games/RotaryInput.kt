package com.example.jitaicompanion.ui.games

import android.content.Context
import android.view.InputDevice
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent

/**
 * True when this watch has a rotating crown or bezel.
 *
 * Used **only** to choose which hint to print. Every rotary-driven screen in this module also
 * accepts touch, unconditionally: feature detection is the kind of thing that silently reports
 * the wrong answer on some OEM build, and a player who cannot turn the dial at all has no way
 * out of a microgame.
 */
fun Context.hasRotaryInput(): Boolean = runCatching {
    InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_ROTARY_ENCODER) == true
    }
}.getOrDefault(false)

/**
 * Routes crown/bezel scroll into [onDelta] as a signed pixel amount.
 *
 * Rotary events are only delivered to a focused node, so this also claims focus for the node it
 * is applied to. Apply it to a single full-screen element per screen — two focusable rotary
 * targets in one composition will fight over focus.
 */
@Composable
fun Modifier.rotaryDelta(onDelta: (Float) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Throws if the node is not attached yet (e.g. the screen was left mid-frame); the
        // touch path still works, so a failure here must not take the game down.
        runCatching { focusRequester.requestFocus() }
    }
    return this
        .onRotaryScrollEvent { event ->
            onDelta(event.verticalScrollPixels)
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
