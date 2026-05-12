package com.BWPStudio.JITAIWizard.server.routes

import com.example.jitaicompanion.convention.trivia.TriviaQuestion
import com.BWPStudio.JITAIWizard.server.ServerState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.triviaRoute() {
    post("/trivia") {
        val questions = call.receive<List<TriviaQuestion>>()
        ServerState.triviaQuestions = questions
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "count" to questions.size))
    }
    get("/trivia") {
        call.respond(ServerState.triviaQuestions)
    }
}
