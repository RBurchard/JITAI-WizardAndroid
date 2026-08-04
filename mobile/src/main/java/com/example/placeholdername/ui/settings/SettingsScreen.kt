package com.BWPStudio.JITAIWizard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.BWPStudio.JITAIWizard.BuildConfig
import com.BWPStudio.JITAIWizard.R
import com.example.jitaicompanion.convention.locale.AppLanguages
import com.example.jitaicompanion.convention.locale.LocaleController

/**
 * App-wide settings: currently just language. Reachable from the gear icon on
 * [com.BWPStudio.JITAIWizard.ui.userview.UserViewScreen].
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var currentTag by remember { mutableStateOf(LocaleController.currentTag(context)) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D1B2A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.settings_title),
                    color = Color.White,
                    fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.settings_language),
                color = Color(0xFF8899BB),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                stringResource(R.string.settings_language_description),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A2A3A), RoundedCornerShape(12.dp))
            ) {
                LanguageRow(
                    label = stringResource(R.string.settings_language_system),
                    selected = currentTag == AppLanguages.SYSTEM,
                    onClick = {
                        LocaleController.setLanguage(context, AppLanguages.SYSTEM)
                        currentTag = AppLanguages.SYSTEM
                        activity?.let { LocaleController.applyAndRecreate(it, AppLanguages.SYSTEM) }
                    }
                )
                AppLanguages.SUPPORTED.forEach { lang ->
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    LanguageRow(
                        label = lang.nativeName,
                        selected = currentTag == lang.tag,
                        onClick = {
                            LocaleController.setLanguage(context, lang.tag)
                            currentTag = lang.tag
                            activity?.let { LocaleController.applyAndRecreate(it, lang.tag) }
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF42A5F5))
        }
    }
}
