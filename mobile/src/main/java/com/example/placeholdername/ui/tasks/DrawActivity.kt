package com.BWPStudio.JITAIWizard.ui.tasks

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.BWPStudio.JITAIWizard.R
import com.example.jitaicompanion.convention.locale.LocaleController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DrawActivity : ComponentActivity() {

    lateinit var prompt: String

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "prompt" normally arrives from the researcher via this Intent extra — that's
        // experimental data and must not be localized. Only the fallback below is app copy.
        prompt = intent.getStringExtra("prompt") ?: getString(R.string.draw_task_default_prompt)
        setContent { DrawScreen(prompt = prompt, onDone = { finish() }) }
    }
}

@Composable
fun DrawScreen(prompt: String, onDone: () -> Unit) {
    val paths = remember { mutableStateListOf<Pair<Path, Color>>() }
    var currentPath by remember { mutableStateOf(Path()) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.primary, tonalElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(prompt, color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { paths.clear() }) { Text(stringResource(R.string.draw_task_clear), color = MaterialTheme.colorScheme.onPrimary) }
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            saveBitmap(context, paths)
                        }
                        onDone()
                    }) { Text(stringResource(R.string.common_done)) }
                }
            }
        }

        // Color picker row
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color(0xFF8B4513)).forEach { color ->
                Box(
                    modifier = Modifier.size(32.dp).background(color).clickable { currentColor = color }
                        .then(if (color == currentColor) Modifier.background(Color.White.copy(alpha = 0.3f)) else Modifier)
                )
            }
        }

        // Drawing canvas
        Canvas(
            modifier = Modifier.fillMaxSize().background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        },
                        onDrag = { change, dragAmount ->
                            currentPath.lineTo(change.position.x, change.position.y)
                            paths.add(Pair(currentPath, currentColor))
                            currentPath = Path().apply { moveTo(change.position.x, change.position.y) }
                        }
                    )
                }
        ) {
            paths.forEach { (path, color) ->
                drawPath(path = path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun saveBitmap(context: android.content.Context, paths: List<Pair<Path, Color>>) {
    // Simplified save — creates a small placeholder record in MediaStore
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "jitai_draw_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JITAI")
        }
    }
    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

// Extension to make Box clickable without import conflict
private fun Modifier.clickable(onClick: () -> Unit) = this.then(
    androidx.compose.ui.Modifier.pointerInput(Unit) {
        detectDragGestures(onDragStart = { _ -> onClick() }, onDrag = { _, _ -> })
    }
)
