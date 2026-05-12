package com.example.jitaicompanion.convention.models

import com.example.jitaicompanion.convention.trivia.TriviaQuestion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InterventionRequest(
    @SerialName("notificationType") val notificationType: NotificationType,
    @SerialName("message") val message: String = "",
    @SerialName("durationMs") val durationMs: Int = 30000,
    @SerialName("gameType") val gameType: GameType? = null,
    @SerialName("phoneTaskType") val phoneTaskType: PhoneTaskType? = null,
    @SerialName("triviaQuestion") val triviaQuestion: TriviaQuestion? = null
)
