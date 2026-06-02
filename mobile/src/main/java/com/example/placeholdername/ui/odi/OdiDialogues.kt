package com.BWPStudio.JITAIWizard.ui.odi

object OdiDialogues {
    val idle = listOf(
        "Monitoring...", "Stay sharp!", "I'm watching...", "All clear.",
        "Processing...", "You've got this.", "Looking good!", "Keeping an eye out."
    )
    val talking = listOf("Focus... you can do this!", "Check your phone.", "Task incoming.", "Hey, listen up!")
    val thinking = listOf("Analyzing...", "Hmm...", "Computing...", "One moment...", "Crunching the data...")
    val celebrating = listOf("You resisted!", "Excellent control!", "Mind over matter!", "Strong work!", "Outstanding!")
    val happy = listOf("Nice and steady.", "Feeling good!", "That's the spirit.", "Great pace!")
    val surprised = listOf("Oh!", "Whoa!", "Heads up!", "Did you feel that?")
    val sleeping = listOf("Zzz...", "Resting up...", "Wake me if you need me.", "Just a little nap.")
    val concerned = listOf("Easy now...", "Let's slow down.", "Take a breath.", "Everything okay?")

    fun linesFor(state: OdiAnimationState): List<String> = when (state) {
        OdiAnimationState.IDLE -> idle
        OdiAnimationState.TALKING -> talking
        OdiAnimationState.THINKING -> thinking
        OdiAnimationState.CELEBRATING -> celebrating
        OdiAnimationState.HAPPY -> happy
        OdiAnimationState.SURPRISED -> surprised
        OdiAnimationState.SLEEPING -> sleeping
        OdiAnimationState.CONCERNED -> concerned
    }

    fun forState(state: OdiAnimationState) = linesFor(state).random()
}
