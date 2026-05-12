package com.BWPStudio.JITAIWizard.server.routes

import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.InterventionRequest
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import android.content.Context
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun Route.triggerRoute(context: Context) {
    post("/trigger") {
        val req = call.receive<InterventionRequest>()
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
            phoneTaskType = req.phoneTaskType
        )
        req.triviaQuestion?.let { ServerState.triviaQuestions = listOf(it) }
        ServerState.lastInterventionSentAt = System.currentTimeMillis()
        val json = Json.encodeToString(intervention)
        WearMessageSender(context).sendIntervention(json)
        call.respond(HttpStatusCode.OK, mapOf("status" to "sent", "id" to intervention.id))
    }
}
