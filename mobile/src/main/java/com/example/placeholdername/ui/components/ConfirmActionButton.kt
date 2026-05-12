package com.BWPStudio.JITAIWizard.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Composable
fun ConfirmActionButton(onConfirm: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
    ) { Text("Stop current intervention") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Are you sure?") },
            text = { Text("This will cancel any current interventions!") },
            confirmButton = {
                TextButton(onClick = { showDialog = false; onConfirm() }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Abort") }
            }
        )
    }
}
