package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.experiment.SessionLogStore
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.WsEnvelope
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.eventsWsRoute(app: JITAIWizardApp) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    webSocket(Protocol.PATH_EVENTS_WS) {
        try {
            coroutineScope {
                val jobs = mutableListOf<Job>()
                jobs += launch {
                    app.experimentEngine.envelopes.collect { env ->
                        outgoing.send(Frame.Text(json.encodeToString(env)))
                    }
                }
                jobs += launch {
                    SessionLogStore.tail.collect { entry ->
                        outgoing.send(Frame.Text(json.encodeToString(WsEnvelope(WsEnvelope.TYPE_LOG, json.encodeToString(entry)))))
                    }
                }
                for (frame in incoming) {
                    // drain client pings; we don't act on inbound frames currently
                }
                jobs.forEach { it.cancel() }
            }
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.NORMAL, "events ended"))
        }
    }
}
