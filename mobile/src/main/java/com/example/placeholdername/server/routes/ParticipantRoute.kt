package com.BWPStudio.JITAIWizard.server.routes

import com.example.jitaicompanion.convention.models.ParticipantInfo
import com.BWPStudio.JITAIWizard.server.ServerState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.participantRoute() {
    post("/participant") {
        val info = call.receive<ParticipantInfo>()
        ServerState.participantInfo = info
        ServerState.sessionId = info.sessionId
        ServerState.sessionStartedAt = System.currentTimeMillis()
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
    get("/participant") {
        call.respond(ServerState.participantInfo ?: mapOf("error" to "not set"))
    }
}
