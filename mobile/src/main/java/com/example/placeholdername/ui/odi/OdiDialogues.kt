package com.BWPStudio.JITAIWizard.ui.odi

object OdiDialogues {
    val idle = listOf("Monitoring...", "Stay sharp!", "I'm watching...", "All clear.", "Processing...")
    val talking = listOf("Focus... you can do this!", "Check your phone.", "Task incoming.")
    val thinking = listOf("Analyzing...", "Hmm...", "Computing...", "One moment...")
    val celebrating = listOf("You resisted!", "Excellent control!", "Mind over matter!", "Strong work!", "Outstanding!")

    fun forState(state: OdiAnimationState) = when (state) {
        OdiAnimationState.IDLE -> idle.random()
        OdiAnimationState.TALKING -> talking.random()
        OdiAnimationState.THINKING -> thinking.random()
        OdiAnimationState.CELEBRATING -> celebrating.random()
    }
}
