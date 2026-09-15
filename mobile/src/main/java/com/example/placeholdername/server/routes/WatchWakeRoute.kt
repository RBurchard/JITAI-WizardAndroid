package com.BWPStudio.JITAIWizard.server.routes

import android.content.Context
import com.BWPStudio.JITAIWizard.datalayer.WearMessageSender
import com.example.jitaicompanion.convention.Protocol
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class WatchWakeRequest(val minutes: Int = Protocol.WATCH_WAKE_DEFAULT_MINUTES)

private val wakeJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * ControlStation's "Wake watch" button. Relays a [Protocol.PATH_POWER_WAKE] to the watch so it
 * holds full collection rate for a few minutes while the wizard sets up, without a run.
 *
 * Fire and forget like `/trigger`: the Data Layer send is asynchronous and the watch has no
 * reply for it, so "sent" here means "handed to Play Services". An empty body is fine.
 */
fun Route.watchWakeRoute(context: Context) {
    post(Protocol.PATH_WATCH_WAKE) {
        val body = call.receiveText()
        val req = if (body.isBlank()) WatchWakeRequest() else wakeJson.decodeFromString<WatchWakeRequest>(body)
        val minutes = req.minutes.coerceIn(1, 60)
        WearMessageSender(context).sendWake(minutes * 60_000L)
        call.respondText(
            """{"status":"sent","minutes":$minutes}""",
            ContentType.Application.Json
        )
    }
}
