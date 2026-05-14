package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.server.ServerState
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class StatusResponse(
    val connected: Boolean,
    val watchConnected: Boolean,
    val sessionId: String,
    val uptime: Long,
    val lastBpm: Float
)

fun Route.statusRoute() {
    get("/status") {
        call.respond(StatusResponse(
            connected = true,
            watchConnected = ServerState.watchConnected,
            sessionId = ServerState.sessionId,
            uptime = ServerState.uptime,
            lastBpm = ServerState.lastHeartRate
        ))
    }
}
