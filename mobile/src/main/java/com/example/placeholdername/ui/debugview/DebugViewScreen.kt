package com.BWPStudio.JITAIWizard.ui.debugview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DebugViewScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Control", "Experiment")

    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar
        Surface(color = MaterialTheme.colorScheme.errorContainer, tonalElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DEBUG MODE", color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onBack) {
                    Text("Back", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                    text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> ControlScreen()
            1 -> ExperimentScreen()
        }
    }
}
