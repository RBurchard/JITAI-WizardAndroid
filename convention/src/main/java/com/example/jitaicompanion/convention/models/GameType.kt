package com.example.jitaicompanion.convention.models

import kotlinx.serialization.Serializable

@Serializable
enum class GameType {
    LOCK_PICKING, SIMON_SAYS, TRIVIA, STAND_STILL
}
