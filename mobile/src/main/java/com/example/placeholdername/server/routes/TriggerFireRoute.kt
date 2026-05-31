package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.triggers.ManualTriggerSource
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.TriggerFireRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.triggerFireRoute() {
    post(Protocol.PATH_TRIGGER_FIRE) {
        val req = call.receive<TriggerFireRequest>()
        ManualTriggerSource.fire(req.triggerId, source = req.source, details = "remote")
        call.respond(HttpStatusCode.OK, mapOf("status" to "fired", "triggerId" to req.triggerId))
    }
}
