package com.example.jitaicompanion.datalayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.NotificationType
import com.example.jitaicompanion.service.WatchDataService
import com.example.jitaicompanion.ui.InterventionActivity
import com.example.jitaicompanion.ui.games.LockPickingGameActivity
import com.example.jitaicompanion.ui.games.MicrogameActivity
import com.example.jitaicompanion.ui.games.SimonSaysGameActivity
import com.example.jitaicompanion.ui.games.StandStillGameActivity
import com.example.jitaicompanion.ui.games.TriviaGameActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

class WearMessageListener : WearableListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }
    private val sender by lazy { WearMessageSender(this) }

    override fun onMessageReceived(event: MessageEvent) {
        ensureDataServiceRunning()
        when (event.path) {
            Protocol.PATH_INTERVENTION -> handleIntervention(String(event.data))
            Protocol.PATH_PHONE_TASK -> showCheckPhoneScreen()
            Protocol.PATH_PING -> handlePing(String(event.data))
            Protocol.PATH_EXIT -> handleExit()
        }
    }

    private fun ensureDataServiceRunning() {
        val hasBodySensors = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasBodySensors) {
            Log.w("WearMessageListener", "BODY_SENSORS not granted — cannot start WatchDataService")
            return
        }
        if (!WatchDataService.isRunning.value) {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, WatchDataService::class.java)
            )
        }
    }

    private fun handleIntervention(data: String) {
        val intervention = try {
            json.decodeFromString<Intervention>(data)
        } catch (e: Exception) {
            Log.e("WearMessageListener", "Failed to parse intervention", e)
            return
        }

        Log.d("WearMessageListener", "Intervention received: ${intervention.type}")

        // A Stop / Cancel intervention should cleanly close any running interaction and
        // return to the Odi screen — never launch a (blank) intervention screen for it.
        val isStop = intervention.type.equals("Stop", ignoreCase = true) ||
            intervention.notification == NotificationType.CANCEL ||
            intervention.durationSeconds <= 0
        if (isStop) {
            Log.d("WearMessageListener", "Stop/Cancel intervention — finishing interactions")
            mainHandler.post {
                MicrogameActivity.finishCurrent()
                InterventionActivity.finishCurrent()
            }
            return
        }

        mainHandler.post {
            InterventionActivity.finishCurrent()
            MicrogameActivity.finishCurrent()

            val activityClass = when (intervention.gameType) {
                GameType.LOCK_PICKING -> LockPickingGameActivity::class.java
                GameType.SIMON_SAYS -> SimonSaysGameActivity::class.java
                GameType.TRIVIA -> TriviaGameActivity::class.java
                GameType.STAND_STILL -> StandStillGameActivity::class.java
                null -> null
            }

            val targetClass = activityClass ?: InterventionActivity::class.java

            val intent = Intent(applicationContext, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Protocol.KEY_INTERVENTION, data)
            }
            startActivity(intent)
            Log.d("WearMessageListener", "Started ${targetClass.simpleName}")
        }
    }

    private fun handleExit() {
        Log.d("WearMessageListener", "Exit command received")
        mainHandler.post {
            MicrogameActivity.finishCurrent()
            InterventionActivity.finishCurrent()
        }
    }

    private fun handlePing(data: String) {
        Log.d("WearMessageListener", "Ping received: $data")
        sender.sendPong("pong:${System.currentTimeMillis()}")
    }

    private fun showCheckPhoneScreen() {
        mainHandler.post {
            val intent = Intent(applicationContext, InterventionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Protocol.KEY_INTERVENTION, """{"id":"phone_task","type":"Text","notification":"VIBRATION1","message":"Check your phone!","durationSeconds":30}""")
            }
            startActivity(intent)
        }
    }
}
