/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.jitaicompanion.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1101
    }

    private val requiredPermissions: Array<String> = buildList {
        add(Manifest.permission.BODY_SENSORS)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureWatchPermissions()
        startWatchDataServiceIfAllowed()
        setContent {
            WearApp()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startWatchDataServiceIfAllowed()
        }
    }

    private fun ensureWatchPermissions() {
        if (hasRequiredPermissions()) return
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    private fun hasRequiredPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startWatchDataServiceIfAllowed() {
        if (!hasRequiredPermissions()) return
        val intent = Intent(this, WatchDataService::class.java).apply {
            putExtra("sessionId", "")
        }
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
fun WearApp() {
    JitaiCompanionTheme {
        AppScaffold {
            ScreenScaffold(
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        OdiCharacter(state = OdiAnimationState.IDLE)
                        Spacer(Modifier.height(8.dp))
                        Text(text = "Hi, I'm Odi", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Ready to help you", style = MaterialTheme.typography.bodySmall)
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
    WearApp()
}