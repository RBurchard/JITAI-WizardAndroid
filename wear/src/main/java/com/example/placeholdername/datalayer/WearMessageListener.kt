package com.example.jitaicompanion.datalayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.convention.models.MicrogameSettings
import com.example.jitaicompanion.convention.models.NotificationType
import com.example.jitaicompanion.power.WatchPowerPolicy
import com.example.jitaicompanion.service.WatchDataService
import com.example.jitaicompanion.ui.InterventionActivity
import com.example.jitaicompanion.ui.games.GameSettings
import com.example.jitaicompanion.ui.games.MicrogameActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WearMessageListener : WearableListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }
    private val sender by lazy { WearMessageSender(this) }

    override fun onMessageReceived(event: MessageEvent) {
        ensureDataServiceRunning()
        // Any message means someone is working with this watch, which is enough to keep it out
        // of the idle profile for the next few minutes.
        WatchPowerPolicy.onPhoneContact()
        when (event.path) {
            Protocol.PATH_INTERVENTION -> handleIntervention(String(event.data))
            Protocol.PATH_PHONE_TASK -> showCheckPhoneScreen()
            Protocol.PATH_PING -> handlePing(String(event.data))
            Protocol.PATH_EXIT -> handleExit()
            Protocol.PATH_GAME_SETTINGS -> handleGameSettings(String(event.data))
            Protocol.PATH_SESSION_STATE -> handleSessionState(String(event.data))
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

            // Always route through InterventionActivity so the text + vibration + notification
            // are always shown first. InterventionActivity will launch the game (if any) once
            // the user has acknowledged the prompt.
            val intent = Intent(applicationContext, InterventionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Protocol.KEY_INTERVENTION, data)
            }
            startActivity(intent)
            Log.d("WearMessageListener", "Started InterventionActivity (gameType=${intervention.gameType})")
        }
    }

    /**
     * Applies microgame settings pushed from the phone, then echoes back what was stored.
     *
     * The ack carries the *stored* value, not the received one, so the phone's status row
     * reflects what the watch will actually play rather than what the phone hoped it sent. A
     * value the watch had to clamp shows up as a mismatch instead of passing silently.
     */
    private fun handleGameSettings(data: String) {
        val incoming = try {
            json.decodeFromString<MicrogameSettings>(data)
        } catch (e: Exception) {
            Log.e("WearMessageListener", "Failed to parse microgame settings", e)
            return
        }
        val stored = GameSettings.save(applicationContext, incoming)
        Log.i("WearMessageListener", "Microgame settings applied: ${stored.summary()}")
        sender.sendGameSettingsAck(json.encodeToString(stored))
    }

    /**
     * The phone's view of whether a run is in progress.
     *
     * The watch never infers this. A schedule can sit on a fifteen minute wait between events,
     * and a watch that guessed "nothing has happened, I must be idle" would quietly stop
     * recording in the middle of a condition.
     */
    private fun handleSessionState(data: String) {
        val active = data.trim() == "1"
        Log.d("WearMessageListener", "Session state from phone: active=$active")
        WatchPowerPolicy.onSessionState(active)
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
            val intervention = Intervention(
                id = "phone_task",
                type = "Text",
                notification = NotificationType.VIBRATION1,
                message = getString(R.string.intervention_phone_task_message),
                durationSeconds = 30
            )
            val intent = Intent(applicationContext, InterventionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Protocol.KEY_INTERVENTION, json.encodeToString(intervention))
            }
            startActivity(intent)
        }
    }
}
