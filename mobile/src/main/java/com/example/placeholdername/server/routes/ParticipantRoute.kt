package com.BWPStudio.JITAIWizard.server.routes

import android.content.Context
import com.example.jitaicompanion.convention.models.ParticipantInfo
import com.BWPStudio.JITAIWizard.server.ServerState
import com.BWPStudio.JITAIWizard.settings.SettingsKeys
import com.BWPStudio.JITAIWizard.settings.SettingsRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.participantRoute(context: Context) {
    val settings = SettingsRepository(context)

    post("/participant") {
        val info = call.receive<ParticipantInfo>()
        ServerState.participantInfo = info
        if (info.sessionId.isNotBlank()) ServerState.sessionId = info.sessionId
        // Only stamp the session start once so participant renames don't reset uptime.
        if (ServerState.sessionStartedAt == 0L) ServerState.sessionStartedAt = System.currentTimeMillis()
        // Persist the label so the participant survives an app restart and stays in sync
        // even when no ControlStation is connected.
        runCatching { settings.setSetting(SettingsKeys.PARTICIPANT, info.label) }
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
    get("/participant") {
        // Always return a parseable ParticipantInfo so the ControlStation can read the
        // phone-set participant even before a session has been started.
        call.respond(
            ServerState.participantInfo
                ?: ParticipantInfo(id = "", sessionId = "", label = "", deviceIp = "")
        )
    }
}
