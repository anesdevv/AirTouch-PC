package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CobaltTint
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralTint
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateWash
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@Composable
fun PresentationPanel(
    isConnected: Boolean,
    isLaserActive: Boolean,
    isBlackScreenActive: Boolean,
    hapticsEnabled: Boolean,
    onStartShow: () -> Unit,
    onEndShow: () -> Unit,
    onToggleBlackScreen: () -> Unit,
    onPrevSlide: () -> Unit,
    onNextSlide: () -> Unit,
    onLaserChange: (Boolean) -> Unit,
    onLaserMove: (dx: Float, dy: Float) -> Unit,
    onResetAccumulators: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Keep screen awake while Presentation screen is open so hardware volume keys & laser stay responsive
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onLaserChange(false)
        }
    }

    var laserTouchPoint by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        val availableHeight = maxHeight
        // Fixed elements: 88dp Next + 60dp Prev + 48dp Row + 30dp spacing = 226dp
        val laserPadHeight = (availableHeight - 230.dp).coerceAtLeast(140.dp)
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (availableHeight < 370.dp) {
                        Modifier.verticalScroll(scrollState)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Primary Hero Button: Next slide
            Surface(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNextSlide()
                },
                shape = RoundedCornerShape(24.dp),
                color = SlateInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .testTag("pres_next_button"),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.pres_next_slide),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.background,
                    )
                }
            }

            // 2. Full-Width Secondary Button: Previous
            Surface(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevSlide()
                },
                shape = RoundedCornerShape(22.dp),
                color = SlateWash,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("pres_prev_button"),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.pres_prev_slide),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = SlateInk,
                    )
                }
            }

            // 3. Compact Pill Actions: Start show | End show | Black screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresPillButton(
                    label = stringResource(R.string.pres_start_show),
                    active = false,
                    testTag = "pres_start_button",
                    onClick = {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStartShow()
                    },
                    modifier = Modifier.weight(1f),
                )
                PresPillButton(
                    label = stringResource(R.string.pres_end_show),
                    active = false,
                    testTag = "pres_end_button",
                    onClick = {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEndShow()
                    },
                    modifier = Modifier.weight(1f),
                )
                PresPillButton(
                    label = stringResource(R.string.pres_black_screen),
                    active = isBlackScreenActive,
                    testTag = "pres_black_screen_button",
                    onClick = {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleBlackScreen()
                    },
                    modifier = Modifier.weight(1.08f),
                )
            }

            // 4. Giant Hold-Only Laser Pointer Pad (ignores quick taps/presses; activates only on hold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(laserPadHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isLaserActive) CoralTint else SlateWash.copy(alpha = 0.78f))
                    .border(
                        width = 1.dp,
                        color = if (isLaserActive) CoralAlert.copy(alpha = 0.85f) else SlateBorder,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .testTag("pres_laser_pad")
                    .pointerInput(isConnected, hapticsEnabled) {
                        val holdDelayMs = 150L
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                                val primaryId = down.id
                                var latestPos = down.position
                                down.consume()

                                // Hold-only gate: quick presses/taps (< 150ms) do NOTHING
                                var liftedBeforeHold = false
                                val holdDeadline = System.currentTimeMillis() + holdDelayMs
                                while (true) {
                                    val remaining = holdDeadline - System.currentTimeMillis()
                                    if (remaining <= 0L) break
                                    val ev = withTimeoutOrNull(remaining) { awaitPointerEvent() } ?: break
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        liftedBeforeHold = true
                                        break
                                    }
                                    val cur = pressed.firstOrNull { it.id == primaryId } ?: pressed.first()
                                    latestPos = cur.position
                                    cur.consume()
                                }
                                if (liftedBeforeHold) continue

                                // Hold threshold met -> engage laser cleanly
                                var prevPos = latestPos
                                laserTouchPoint = latestPos
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onLaserChange(true)

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        val current = pressed.firstOrNull { it.id == primaryId } ?: pressed.first()
                                        val dx = current.position.x - prevPos.x
                                        val dy = current.position.y - prevPos.y
                                        prevPos = current.position
                                        laserTouchPoint = current.position
                                        if (abs(dx) + abs(dy) > 0.1f) {
                                            onLaserMove(dx, dy)
                                            current.consume()
                                        }
                                    }
                                } finally {
                                    laserTouchPoint = null
                                    onLaserChange(false)
                                    onResetAccumulators()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    laserTouchPoint?.let { pos ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CoralAlert.copy(alpha = 0.42f),
                                    CoralAlert.copy(alpha = 0.0f),
                                ),
                                center = pos,
                                radius = 62.dp.toPx(),
                            ),
                            radius = 62.dp.toPx(),
                            center = pos,
                        )
                        drawCircle(
                            color = CoralAlert,
                            radius = 9.dp.toPx(),
                            center = pos,
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (isLaserActive) {
                            stringResource(R.string.pres_laser_active)
                        } else {
                            stringResource(R.string.pres_laser_title)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isLaserActive) CoralAlert else SlateInk,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.pres_laser_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresPillButton(
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) CobaltTint else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) CobaltAccent else SlateBorder,
        ),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = if (active) CobaltAccent else SlateInk,
                maxLines = 1,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
