package com.example.jitaicompanion.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.jitaicompanion.R
import com.example.jitaicompanion.ui.layout.ProvideWearDimens
import com.example.jitaicompanion.ui.layout.sdp
import com.example.jitaicompanion.ui.layout.wearDimens
import com.example.jitaicompanion.ui.odi.OdiAnimationState
import com.example.jitaicompanion.ui.odi.OdiCharacter
import com.example.jitaicompanion.ui.odi.OdiConfetti
import kotlinx.coroutines.delay

class PositiveFeedbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ProvideWearDimens {
                MaterialTheme {
                    LaunchedEffect(Unit) {
                        delay(3000L)
                        finish()
                    }
                    val dimens = wearDimens
                    // Flat resource keys generated for the praise array — build the list here
                    // (inside composition, where stringResource is callable) and pick one just
                    // once per screen so it doesn't change out from under the 3s countdown.
                    val praise = listOf(
                        stringResource(R.string.positive_feedback_praise_1),
                        stringResource(R.string.positive_feedback_praise_2),
                        stringResource(R.string.positive_feedback_praise_3),
                        stringResource(R.string.positive_feedback_praise_4),
                        stringResource(R.string.positive_feedback_praise_5),
                        stringResource(R.string.positive_feedback_praise_6),
                        stringResource(R.string.positive_feedback_praise_7)
                    )
                    val message = remember { praise.random() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1B5E20)),
                        contentAlignment = Alignment.Center
                    ) {
                        OdiConfetti(active = true, modifier = Modifier.fillMaxSize())
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            OdiCharacter(state = OdiAnimationState.CELEBRATING, size = dimens.scaled(120.dp))
                            Spacer(Modifier.height(6.sdp))
                            Text(
                                text = stringResource(R.string.positive_feedback_odi_says, message),
                                fontSize = dimens.titleTextSize,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = dimens.horizontalPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
