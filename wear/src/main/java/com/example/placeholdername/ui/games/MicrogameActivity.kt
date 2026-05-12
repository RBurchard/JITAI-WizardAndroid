package com.example.jitaicompanion.ui.games

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.example.jitaicompanion.datalayer.WearMessageSender
import com.example.jitaicompanion.ui.PositiveFeedbackActivity

abstract class MicrogameActivity : ComponentActivity() {

    protected lateinit var sender: WearMessageSender
    protected lateinit var vibrator: Vibrator
    private var isCompleted = false

    abstract val timeoutSeconds: Int

    @Composable
    abstract fun GameContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sender = WearMessageSender(this)
        vibrator = getSystemService(Vibrator::class.java)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { GameContent() }
    }

    protected fun onGameComplete(response: String) {
        if (isCompleted) return
        isCompleted = true
        sender.sendResponse(response)
        startActivity(Intent(this, PositiveFeedbackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    protected fun onGameFailed(response: String = "FAIL") {
        if (isCompleted) return
        isCompleted = true
        sender.sendResponse(response)
        finish()
    }

    protected fun vibratePulse() {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    protected fun vibratePattern(pattern: LongArray, repeat: Int = -1) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
    }
}
