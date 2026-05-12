package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.server.ServerState
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.statusRoute() {
    get("/status") {
        call.respond(mapOf(
            "connected" to true,
            "watchConnected" to ServerState.watchConnected,
            "sessionId" to ServerState.sessionId,
            "uptime" to ServerState.uptime,
            "lastBpm" to ServerState.lastHeartRate
        ))
    }
}
