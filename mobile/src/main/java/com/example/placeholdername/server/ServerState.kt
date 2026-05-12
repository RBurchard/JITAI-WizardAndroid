package com.BWPStudio.JITAIWizard.server

import com.example.jitaicompanion.convention.models.ParticipantInfo
import com.example.jitaicompanion.convention.trivia.TriviaQuestion

object ServerState {
    var participantInfo: ParticipantInfo? = null
    var sessionId: String = ""
    var sessionStartedAt: Long = 0L
    var lastInterventionSentAt: Long = 0L
    var lastReactionTimeMs: Long = 0L
    var lastHeartRate: Float = 0f
    var lastAction: String = ""
    var watchConnected: Boolean = false
    var triviaQuestions: List<TriviaQuestion> = emptyList()
    val uptime get() = if (sessionStartedAt > 0) System.currentTimeMillis() - sessionStartedAt else 0L
}
