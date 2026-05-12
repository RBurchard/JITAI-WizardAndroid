package com.example.jitaicompanion.convention.trivia

import kotlinx.serialization.Serializable

@Serializable
data class TriviaQuestion(
    val id: Int = 0,
    val question: String,
    val answers: List<String>,
    val correctIndex: Int
)
