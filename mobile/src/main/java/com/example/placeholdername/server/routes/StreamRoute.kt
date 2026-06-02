package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.datalayer.WatchDataRelay
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.streamRoute() {
    webSocket("/stream") {
        try {
            WatchDataRelay.flow.collect { batch ->
                val json = Json.encodeToString(batch)
                outgoing.send(Frame.Text(json))
            }
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.NORMAL, "Stream ended"))
        }
    }
}
