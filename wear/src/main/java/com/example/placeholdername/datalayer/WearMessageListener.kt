package com.example.jitaicompanion.datalayer

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.jitaicompanion.convention.Protocol
import com.example.jitaicompanion.convention.models.GameType
import com.example.jitaicompanion.convention.models.Intervention
import com.example.jitaicompanion.ui.InterventionActivity
import com.example.jitaicompanion.ui.games.LockPickingGameActivity
import com.example.jitaicompanion.ui.games.SimonSaysGameActivity
import com.example.jitaicompanion.ui.games.StandStillGameActivity
import com.example.jitaicompanion.ui.games.TriviaGameActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

class WearMessageListener : WearableListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            Protocol.PATH_INTERVENTION -> handleIntervention(String(event.data))
            Protocol.PATH_PHONE_TASK -> showCheckPhoneScreen()
        }
    }

    private fun handleIntervention(data: String) {
        val intervention = try {
            json.decodeFromString<Intervention>(data)
        } catch (e: Exception) {
            Log.e("WearMessageListener", "Failed to parse intervention", e)
            return
        }

        mainHandler.post {
            InterventionActivity.finishCurrent()

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
