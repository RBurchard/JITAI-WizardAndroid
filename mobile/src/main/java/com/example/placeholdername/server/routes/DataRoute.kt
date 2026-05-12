package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.server.ServerState
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dataRoute() {
    get("/data") {
        call.respond(mapOf(
            "sessionId" to ServerState.sessionId,
            "participant" to (ServerState.participantInfo?.label ?: ""),
            "lastReactionTimeMs" to ServerState.lastReactionTimeMs,
            "lastHeartRate" to ServerState.lastHeartRate,
            "lastAction" to ServerState.lastAction
        ))
    }
}
