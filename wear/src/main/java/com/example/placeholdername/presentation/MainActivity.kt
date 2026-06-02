package com.example.jitaicompanion.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.jitaicompanion.presentation.theme.JitaiCompanionTheme
import com.example.jitaicompanion.service.WatchDataService
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1101
    }

    // Service start is gated on these.
    private val mandatoryPermissions: Array<String> = arrayOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    // Sensor permissions: we request these but the service will attempt to run
    // if at least one heart-rate related permission is granted.
    private val sensorPermissions: Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BODY_SENSORS_BACKGROUND,
            "android.permission.health.READ_HEART_RATE"
        )
    } else {
        arrayOf(Manifest.permission.BODY_SENSORS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureWatchPermissions()
        startWatchDataServiceIfAllowed()
        setContent {
            WearApp(
                onOdiClick = { ensureWatchPermissions() },
                onForceEnd = { forceEnd() }
            )
        }
    }

    /**
     * Fully shuts the watch app down with no lingering background work: stops the
     * sensor foreground service, finishes any tracked interaction screens, removes the
     * task and terminates the process.
     */
    private fun forceEnd() {
        Log.i("MainActivity", "Force End requested — shutting down watch app")
        runCatching { stopService(Intent(this, WatchDataService::class.java)) }
        finishAndRemoveTask()
        exitProcess(0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d("MainActivity", "onRequestPermissionsResult: Received response for ${permissions.size} permissions")
            permissions.forEachIndexed { index, name ->
                Log.d("MainActivity", "Permission: $name, Result: ${if (grantResults[index] == 0) "GRANTED" else "DENIED"}")
            }
            
            // Auto-request background sensors if foreground just got granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.i("MainActivity", "Foreground sensors granted, now requesting BACKGROUND sensors...")
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND), PERMISSION_REQUEST_CODE)
                }
            }

            startWatchDataServiceIfAllowed()
        }
    }

    private fun ensureWatchPermissions() {
        Log.d("MainActivity", "ensureWatchPermissions: Checking...")
        val allToRequest = (mandatoryPermissions + sensorPermissions).distinct().toTypedArray()
        
        val missing = allToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            Log.d("MainActivity", "ensureWatchPermissions: All granted")
            return
        }

        Log.i("MainActivity", "ensureWatchPermissions: Requesting: $missing")
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun hasRequiredPermissions(): Boolean {
        val missingMandatory = mandatoryPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        // At least one HR permission must be granted
        val hrGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, "android.permission.health.READ_HEART_RATE") == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (missingMandatory.isNotEmpty()) {
            Log.w("MainActivity", "hasRequiredPermissions: Missing mandatory: $missingMandatory")
        }
        if (!hrGranted) {
            Log.w("MainActivity", "hasRequiredPermissions: No Heart Rate permissions (Body Sensors or Health Read) granted")
        }

        return missingMandatory.isEmpty() && hrGranted
    }

    private fun startWatchDataServiceIfAllowed() {
        Log.d("MainActivity", "startWatchDataServiceIfAllowed: Checking...")
        if (!hasRequiredPermissions()) {
            Log.e("MainActivity", "startWatchDataServiceIfAllowed: Permissions NOT sufficient")
            return
        }
        
        Log.i("MainActivity", "startWatchDataServiceIfAllowed: Starting service...")
        val intent = Intent(this, WatchDataService::class.java).apply {
            putExtra("sessionId", "main_session_${System.currentTimeMillis()}")
        }
        try {
            val component = ContextCompat.startForegroundService(this, intent)
            Log.i("MainActivity", "startWatchDataServiceIfAllowed: Service start result: $component")
        } catch (e: Exception) {
            Log.e("MainActivity", "startWatchDataServiceIfAllowed: FAILED to start service", e)
        }
    }
}

@Composable
fun WearApp(onOdiClick: () -> Unit, onForceEnd: () -> Unit = {}) {
    val heartRate by WatchDataService.heartRateState.collectAsState()
    val isRunning by WatchDataService.isRunning.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "heartPulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    JitaiCompanionTheme {
        AppScaffold {
            ScreenScaffold {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    // BPM badge anchored to the left edge
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "♥",
                                color = Color(0xFFEF5350),
                                fontSize = 14.sp,
                                modifier = Modifier.scale(if (heartRate > 0) heartScale else 1f)
                            )
                            Text(
                                text = if (heartRate > 0f) "${heartRate.toInt()}" else "--",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("BPM", color = Color(0xFF9E9E9E), fontSize = 9.sp)
                        }
                    }

                    // Odi centered (unchanged)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.clickable {
                            Log.d("MainActivity", "Odi clicked - forcing permission check")
                            onOdiClick()
                        }
                    ) {
                        OdiCharacter(
                            state = when {
                                heartRate > 120f -> OdiAnimationState.CONCERNED
                                heartRate > 0f -> OdiAnimationState.IDLE
                                else -> OdiAnimationState.THINKING
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isRunning) "Odi is watching" else "Hi, I'm Odi",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (heartRate > 0) "Stay active!" else if (!isRunning) "Tap to Grant Permissions" else "Waiting for sensors...",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Force End — fully closes the watch app and stops the background service.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "⏻ Force End",
                            color = Color(0xFFEF5350),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .clickable { onForceEnd() }
                        )
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp(onOdiClick = {})
}
