package com.example.jitaicompanion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.convention.locale.AppLanguages
import com.example.jitaicompanion.convention.locale.LocaleController
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.ssp
import com.example.jitaicompanion.ui.layout.wearDimens

/**
 * Watch-side settings: language picker + Force End (moved here from the main screen so it
 * cannot be tapped by accident, and so the main screen stays uncluttered on small watches).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onForceEnd: () -> Unit) {
    var confirmingEnd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var currentTag by remember { mutableStateOf(LocaleController.currentTag(context)) }
    val dimens = wearDimens

    Box(modifier = Modifier.fillMaxSize()) {
        if (confirmingEnd) {
            ConfirmForceEnd(
                onConfirm = onForceEnd,
                onCancel = { confirmingEnd = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(14.sdp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Text("‹", fontSize = dimens.titleTextSize)
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        fontSize = dimens.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(dimens.itemSpacing))
                Text(
                    stringResource(R.string.settings_language),
                    fontSize = dimens.captionTextSize,
                    color = Color(0xFF8899BB)
                )

                LanguageRow(
                    label = stringResource(R.string.settings_language_system),
                    selected = currentTag == AppLanguages.SYSTEM,
                    dimens = dimens
                ) {
                    LocaleController.setLanguage(context, AppLanguages.SYSTEM)
                    currentTag = AppLanguages.SYSTEM
                }
                AppLanguages.SUPPORTED.forEach { lang ->
                    LanguageRow(label = lang.nativeName, selected = currentTag == lang.tag, dimens = dimens) {
                        LocaleController.setLanguage(context, lang.tag)
                        currentTag = lang.tag
                    }
                }

                Spacer(Modifier.height(dimens.itemSpacing * 2))
                Button(onClick = { confirmingEnd = true }) {
                    Text(stringResource(R.string.settings_force_end), fontSize = dimens.bodyTextSize)
                }
                Spacer(Modifier.height(14.sdp))
            }
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    dimens: com.example.jitaicompanion.ui.layout.WearDimens,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.sdp)
            .background(Color.White.copy(alpha = if (selected) 0.12f else 0f), RoundedCornerShape(8.sdp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.sdp, vertical = 6.sdp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = dimens.bodyTextSize)
        if (selected) {
            Text("✓", fontSize = dimens.bodyTextSize, color = Color(0xFF42A5F5))
        }
    }
}

@Composable
private fun ConfirmForceEnd(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val dimens = wearDimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.settings_force_end_confirm),
            fontSize = dimens.bodyTextSize,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(dimens.itemSpacing * 2))
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
            Button(onClick = onCancel) { Text(stringResource(R.string.common_cancel), fontSize = dimens.bodyTextSize) }
            Button(onClick = onConfirm) { Text(stringResource(R.string.settings_force_end), fontSize = dimens.bodyTextSize) }
        }
    }
}
