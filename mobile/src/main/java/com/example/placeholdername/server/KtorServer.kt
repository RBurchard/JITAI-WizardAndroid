package com.BWPStudio.JITAIWizard.server

import android.content.Context
import android.util.Log
import com.example.jitaicompanion.convention.Protocol
import com.BWPStudio.JITAIWizard.JITAIWizardApp
import com.BWPStudio.JITAIWizard.server.routes.dataRoute
import com.BWPStudio.JITAIWizard.server.routes.eventsWsRoute
import com.BWPStudio.JITAIWizard.server.routes.experimentControlRoute
import com.BWPStudio.JITAIWizard.server.routes.experimentRoute
import com.BWPStudio.JITAIWizard.server.routes.logsRoute
import com.BWPStudio.JITAIWizard.server.routes.participantRoute
import com.BWPStudio.JITAIWizard.server.routes.statusRoute
import com.BWPStudio.JITAIWizard.server.routes.streamRoute
import com.BWPStudio.JITAIWizard.server.routes.triggerFireRoute
import com.BWPStudio.JITAIWizard.server.routes.triggerRoute
import com.BWPStudio.JITAIWizard.server.routes.triviaRoute
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import java.net.BindException
import java.time.Duration
import kotlin.time.toKotlinDuration

class KtorServer(private val context: Context) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (server != null) return
        try {
            server = embeddedServer(Netty, port = Protocol.HTTP_PORT) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true })
                }
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15).toKotlinDuration()
                    timeout = Duration.ofSeconds(30).toKotlinDuration()
                }
                routing {
                    val app = context.applicationContext as JITAIWizardApp
                    triggerRoute(context)
                    statusRoute()
                    dataRoute()
                    participantRoute(context)
                    triviaRoute()
                    streamRoute()
                    experimentRoute(app)
                    experimentControlRoute(app)
                    logsRoute()
                    triggerFireRoute()
                    eventsWsRoute(app)
                }
            }.start(wait = false)
            Log.d("KtorServer", "HTTP server started on port ${Protocol.HTTP_PORT}")
        } catch (e: Exception) {
            server = null
            val message = if (e is BindException) {
                "HTTP server not started: port ${Protocol.HTTP_PORT} already in use"
            } else {
                "HTTP server failed to start: ${e.message}"
            }
            Log.e("KtorServer", message, e)
        }
    }

    fun stop() {
        server?.stop(1000, 3000)
        server = null
        Log.d("KtorServer", "HTTP server stopped")
    }
}
