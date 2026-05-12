package com.BWPStudio.JITAIWizard.server.routes

import android.content.Context
import com.example.jitaicompanion.convention.models.Intervention
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.BWPStudio.JITAIWizard.server.ServerState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.triggerRoute(context: Context) {
    post("/trigger") {
        val intervention = call.receive<Intervention>()
        ServerState.lastInterventionSentAt = System.currentTimeMillis()
        val json = Json.encodeToString(intervention)
        WearMessageSender(context).sendIntervention(json)
        call.respond(HttpStatusCode.OK, mapOf("status" to "sent", "id" to intervention.id))
    }
}
