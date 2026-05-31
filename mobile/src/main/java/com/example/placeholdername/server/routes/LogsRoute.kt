package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.experiment.SessionLogStore
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.SessionLogPage
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.logsRoute() {
    get(Protocol.PATH_LOGS) {
        val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 200).coerceIn(1, 1000)
        val entries = SessionLogStore.query(since, limit)
        val nextSince = entries.lastOrNull()?.id ?: since
        call.respond(SessionLogPage(entries = entries, nextSince = nextSince))
    }
}
