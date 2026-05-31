package com.BWPStudio.JITAIWizard.server.routes

import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.triggers.TriggerEngine
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.ExperimentEnvelope
import com.example.jitaicompanion.convention.models.ExperimentPutRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.experimentRoute(app: JITAIWizardApp) {
    get(Protocol.PATH_EXPERIMENT) {
        val store = app.experimentStore
        call.response.headers.append(Protocol.ETAG_HEADER, store.etag)
        call.respond(ExperimentEnvelope(etag = store.etag, experiment = store.active.value))
    }
    put(Protocol.PATH_EXPERIMENT) {
        val req = call.receive<ExperimentPutRequest>()
        val store = app.experimentStore
        val ifMatch = req.ifMatch ?: call.request.headers[Protocol.ETAG_HEADER]
        if (ifMatch != null && ifMatch != store.etag) {
            call.response.headers.append(Protocol.ETAG_HEADER, store.etag)
            call.respond(HttpStatusCode.Conflict, ExperimentEnvelope(etag = store.etag, experiment = store.active.value))
            return@put
        }
        val newEtag = store.replace(req.experiment, fromSync = true)
        TriggerEngine.setTriggers(req.experiment.triggers)
        call.response.headers.append(Protocol.ETAG_HEADER, newEtag)
        call.respond(ExperimentEnvelope(etag = newEtag, experiment = store.active.value))
    }
}
