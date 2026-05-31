package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.ExperimentControl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.experimentControlRoute(app: JITAIWizardApp) {
    post(Protocol.PATH_EXPERIMENT_CONTROL) {
        val req = call.receive<ExperimentControl>()
        app.experimentEngine.dispatch(req.command, req.targetEventIndex)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "command" to req.command))
    }
}
