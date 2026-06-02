package com.BWPStudio.JITAIWizard.ui.odi

/**
 * The set of moods ODI can express. The original four states are kept first for
 * backwards compatibility; the rest add range so ODI can react to what's happening.
 *
 *  - [IDLE]        calm, breathing, occasionally glancing around
 *  - [TALKING]     mouth flapping while delivering a message
 *  - [THINKING]    eyes drift upward, little thought dots bubble up
 *  - [CELEBRATING] bouncing, beaming, confetti + sparkles
 *  - [HAPPY]       a warm, content smile (lighter than celebrating)
 *  - [SURPRISED]   wide eyes, small round mouth
 *  - [SLEEPING]    eyes shut, gentle breathing, drifting "z"s
 *  - [CONCERNED]   a worried frown (e.g. heart rate spike / lost sensors)
 */
enum class OdiAnimationState {
    IDLE,
    TALKING,
    THINKING,
    CELEBRATING,
    HAPPY,
    SURPRISED,
    SLEEPING,
    CONCERNED
}
