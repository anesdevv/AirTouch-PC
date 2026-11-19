package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CobaltTint
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralTint
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateMuted
import com.example.voice.VoiceState
import kotlinx.coroutines.delay

@Composable
fun VoiceOverlayCard(
    voiceState: VoiceState,
    autoCommandsEnabled: Boolean,
    onStopListening: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-dismiss transient success card after brief confirmation
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceState.Result) {
            delay(1700L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = voiceState !is VoiceState.Idle,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        exit = fadeOut() + scaleOut(targetScale = 0.95f),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("voice_overlay_card"),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CobaltTint,
                    ) {
                        Text(
                            text = if (autoCommandsEnabled) "Voice Commands + Text" else "Dictation Only",
                            style = MaterialTheme.typography.labelMedium,
                            color = CobaltAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_dismiss_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close_sheet),
                            tint = SlateMuted,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when (voiceState) {
                    is VoiceState.Listening -> {
                        val pulseTransition = rememberInfiniteTransition(label = "micPulse")
                        val pulseScale by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.24f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(750),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "pulseScale",
                        )
                        val rmsScale = 1f + (voiceState.rmsDb / 28f)

                        Box(
                            modifier = Modifier.size(84.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(maxOf(pulseScale, rmsScale))
                                    .clip(CircleShape)
                                    .background(CobaltTint),
                            )
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CobaltAccent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = voiceState.partialText.ifBlank {
                                stringResource(R.string.voice_listening_prompt)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = onStopListening,
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltAccent),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("voice_stop_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.cd_voice_stop),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Done Speaking")
                        }
                    }

                    is VoiceState.Result -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(EmeraldTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldOnline,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = voiceState.actionLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "\"${voiceState.transcript}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    is VoiceState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CoralTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = CoralAlert,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = voiceState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("Close")
                            }
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = CobaltAccent),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("voice_retry_button"),
                            ) {
                                Text("Try Again")
                            }
                        }
                    }

                    VoiceState.Idle -> {}
                }
            }
        }
    }
}
