package com.BWPStudio.JITAIWizard.server.routes

import android.content.Context
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.InterventionRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val triggerJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

fun Route.triggerRoute(context: Context) {
    post("/trigger") {
        val body = call.receiveText()
        val req = triggerJson.decodeFromString<InterventionRequest>(body)
        val intervention = Intervention(
            id = UUID.randomUUID().toString(),
            type = when {
                req.gameType != null -> "Timer"
                else -> "Text"
            },
            notification = req.notificationType,
            message = req.message,
            durationSeconds = maxOf(1, req.durationMs / 1000),
            gameType = req.gameType,
            phoneTaskType = req.phoneTaskType,
            triviaQuestion = req.triviaQuestion
        )
        req.triviaQuestion?.let { ServerState.triviaQuestions = listOf(it) }
        ServerState.lastInterventionSentAt = System.currentTimeMillis()
        WearMessageSender(context).sendIntervention(Json.encodeToString(intervention))
        call.respondText(
            """{"status":"sent","id":"${intervention.id}"}""",
            ContentType.Application.Json
        )
    }
}
