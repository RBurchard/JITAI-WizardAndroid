package com.BWPStudio.JITAIWizard.ui.tasks

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prompt = intent.getStringExtra("prompt") ?: "Take a photo"
        setContent { CameraScreen(prompt = prompt, onDone = { finish() }) }
    }
}

@Composable
fun CameraScreen(prompt: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var photoTaken by remember { mutableStateOf(false) }

    val photoFile = remember {
        File(context.cacheDir, "jitai_photo_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
    }
    val photoUri: Uri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoTaken = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primary) {
            Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(prompt, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                if (photoTaken) {
                    Button(onClick = onDone) { Text("Done") }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!photoTaken) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(prompt, style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { cameraLauncher.launch(photoUri) }) { Text("Open Camera") }
                }
            } else {
                val bitmap = remember(photoFile) {
                    if (photoFile.exists()) BitmapFactory.decodeFile(photoFile.absolutePath) else null
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bitmap != null) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Captured photo",
                            modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { photoTaken = false; cameraLauncher.launch(photoUri) }) { Text("Retake") }
                        Button(onClick = onDone) { Text("Submit") }
                    }
                }
            }
        }
    }
}
