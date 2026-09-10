package com.BWPStudio.JITAIWizard.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.BWPStudio.JITAIWizard.R

/**
 * The panic button: cancels whatever the watch is doing, behind a confirmation.
 *
 * Its labels used to be hardcoded English, which showed up untranslated on a phone running the
 * German or Dutch UI. They now come from resources like everything else.
 */
@Composable
fun ConfirmActionButton(onConfirm: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.height(18.dp))
        Text(
            "  " + stringResource(R.string.control_stop_button),
            fontWeight = FontWeight.SemiBold
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.control_stop_confirm_title)) },
            text = { Text(stringResource(R.string.control_stop_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDialog = false; onConfirm() }) {
                    Text(stringResource(R.string.common_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
