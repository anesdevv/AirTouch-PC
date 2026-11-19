package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.ClickAction
import com.example.network.MouseButton
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CobaltTint
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateSubtle
import com.example.ui.theme.SlateWash
import kotlin.math.abs

@Composable
fun PrecisionTrackpad(
    isConnected: Boolean,
    isDragging: Boolean,
    tapToClick: Boolean,
    hapticsEnabled: Boolean,
    onMove: (dx: Float, dy: Float) -> Unit,
    onScroll: (dx: Float, dy: Float) -> Unit,
    onResetAccumulators: () -> Unit,
    onClick: (MouseButton, ClickAction) -> Unit,
    onMediaKey: (key: String, label: String) -> Unit,
    onDragHoldChange: (Boolean) -> Unit,
    onConnectPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var activeTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var isTwoFingerScrolling by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isDragging) CobaltAccent else SlateBorder,
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        // Main Touch Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("trackpad_surface")
                .pointerInput(isConnected, tapToClick, hapticsEnabled) {
                    val touchSlop = viewConfiguration.touchSlop
                    val tapTimeoutMs = 280L
                    val doubleTapWindowMs = 320L
                    var lastTapUpTime = 0L

                    awaitPointerEventScope {
                        while (true) {
                            val firstDown = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                            val primaryId: PointerId = firstDown.id
                            val startPrimaryPos = firstDown.position
                            var prevPrimaryPos = firstDown.position
                            var maxPointerCount = 1
                            var maxSingleDisplacement = 0f
                            var twoFingerStartCenter: Offset? = null
                            var prevTwoFingerCenter: Offset? = null
                            var maxTwoFingerDisplacement = 0f
                            var hadTwoFingers = false
                            val downTime = System.currentTimeMillis()
                            var isDoubleTapCandidate = tapToClick && (downTime - lastTapUpTime) <= doubleTapWindowMs
                            var dragHoldActive = false

                            activeTouchPoint = firstDown.position

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break

                                    maxPointerCount = maxOf(maxPointerCount, pressed.size)

                                    if (pressed.size >= 2) {
                                        isDoubleTapCandidate = false
                                        if (dragHoldActive) {
                                            dragHoldActive = false
                                            onDragHoldChange(false)
                                        }
                                        hadTwoFingers = true
                                        isTwoFingerScrolling = true

                                        val center = Offset(
                                            x = (pressed[0].position.x + pressed[1].position.x) / 2f,
                                            y = (pressed[0].position.y + pressed[1].position.y) / 2f,
                                        )
                                        val startCenter = twoFingerStartCenter
                                        if (startCenter == null) {
                                            twoFingerStartCenter = center
                                        } else {
                                            val disp = kotlin.math.hypot(center.x - startCenter.x, center.y - startCenter.y)
                                            if (disp > maxTwoFingerDisplacement) {
                                                maxTwoFingerDisplacement = disp
                                            }
                                        }

                                        val prev = prevTwoFingerCenter
                                        if (prev != null) {
                                            val sdx = center.x - prev.x
                                            val sdy = center.y - prev.y
                                            if (maxTwoFingerDisplacement > touchSlop * 0.45f) {
                                                onScroll(sdx, sdy)
                                            }
                                        }
                                        prevTwoFingerCenter = center
                                        activeTouchPoint = center
                                        pressed.forEach { it.consume() }
                                    } else {
                                        isTwoFingerScrolling = false
                                        prevTwoFingerCenter = null
                                        val current = pressed.firstOrNull { it.id == primaryId } ?: pressed.first()

                                        if (hadTwoFingers) {
                                            // Prevent cursor jump while lifting two fingers sequentially
                                            prevPrimaryPos = current.position
                                            activeTouchPoint = current.position
                                        } else {
                                            val dx = current.position.x - prevPrimaryPos.x
                                            val dy = current.position.y - prevPrimaryPos.y
                                            prevPrimaryPos = current.position
                                            activeTouchPoint = current.position

                                            val disp = kotlin.math.hypot(
                                                current.position.x - startPrimaryPos.x,
                                                current.position.y - startPrimaryPos.y,
                                            )
                                            if (disp > maxSingleDisplacement) {
                                                maxSingleDisplacement = disp
                                            }

                                            if (isDoubleTapCandidate && !dragHoldActive) {
                                                val heldMs = System.currentTimeMillis() - downTime
                                                if (maxSingleDisplacement > 2.5f || heldMs >= 90L) {
                                                    dragHoldActive = true
                                                    if (hapticsEnabled) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                    onDragHoldChange(true)
                                                }
                                            }

                                            if (abs(dx) + abs(dy) > 0.1f) {
                                                onMove(dx, dy)
                                                current.consume()
                                            }
                                        }
                                    }
                                }
                            } finally {
                                activeTouchPoint = null
                                isTwoFingerScrolling = false
                                onResetAccumulators()
                                if (dragHoldActive) {
                                    onDragHoldChange(false)
                                }
                            }

                            val elapsed = System.currentTimeMillis() - downTime
                            if (tapToClick) {
                                if (maxPointerCount >= 2) {
                                    if (maxTwoFingerDisplacement < touchSlop * 1.6f &&
                                        maxSingleDisplacement < touchSlop * 1.6f &&
                                        elapsed < 320L
                                    ) {
                                        if (hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        onClick(MouseButton.RIGHT, ClickAction.TAP)
                                    }
                                    lastTapUpTime = 0L
                                } else if (dragHoldActive) {
                                    // Drag ended (or held double-tap released) -> LEFT DOWN + UP already completed
                                    lastTapUpTime = 0L
                                } else if (maxSingleDisplacement < touchSlop && elapsed < tapTimeoutMs) {
                                    if (hapticsEnabled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onClick(MouseButton.LEFT, ClickAction.TAP)
                                    lastTapUpTime = if (isDoubleTapCandidate) 0L else System.currentTimeMillis()
                                } else {
                                    lastTapUpTime = 0L
                                }
                            }
                        }
                    }
                },
        ) {
            // Cached precision micro-dot hardware grid + lightweight active finger halo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val step = 28.dp.toPx()
                        val dotRadius = 1.25.dp.toPx()
                        val dotColor = Color(0xFF232E40)
                        val areaWidth = size.width
                        val areaHeight = size.height
                        val dotOffsets = buildList {
                            var x = step
                            while (x < areaWidth - step * 0.5f) {
                                var y = step
                                while (y < areaHeight - step * 0.5f) {
                                    add(Offset(x, y))
                                    y += step
                                }
                                x += step
                            }
                        }
                        val railX = areaWidth - 10.dp.toPx()
                        val railStartY = 24.dp.toPx()
                        val railEndY = areaHeight - 24.dp.toPx()
                        val railStroke = 2.dp.toPx()
                        val haloRadius = 54.dp.toPx()
                        val coreRadius = 8.dp.toPx()

                        onDrawBehind {
                            for (pt in dotOffsets) {
                                drawCircle(
                                    color = dotColor,
                                    radius = dotRadius,
                                    center = pt,
                                )
                            }
                            drawLine(
                                color = if (isTwoFingerScrolling) CobaltAccent.copy(alpha = 0.55f) else Color(0xFF273346),
                                start = Offset(railX, railStartY),
                                end = Offset(railX, railEndY),
                                strokeWidth = railStroke,
                            )
                            activeTouchPoint?.let { pos ->
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            CobaltAccent.copy(alpha = 0.22f),
                                            CobaltAccent.copy(alpha = 0.0f),
                                        ),
                                        center = pos,
                                        radius = haloRadius,
                                    ),
                                    radius = haloRadius,
                                    center = pos,
                                )
                                drawCircle(
                                    color = CobaltAccent.copy(alpha = 0.35f),
                                    radius = coreRadius,
                                    center = pos,
                                )
                            }
                        }
                    },
            )

            // Drag indicator badge
            this@Column.AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = CobaltTint,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragIndicator,
                            contentDescription = null,
                            tint = CobaltAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Dragging",
                            style = MaterialTheme.typography.labelMedium,
                            color = CobaltAccent,
                        )
                    }
                }
            }

            // Center empty / subtle guide
            if (!isConnected) {
                Surface(
                    onClick = onConnectPrompt,
                    shape = RoundedCornerShape(20.dp),
                    color = SlateWash.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .testTag("trackpad_connect_prompt"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CobaltTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = CobaltAccent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Pair Your Windows PC",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.trackpad_hint_disconnected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = SlateSubtle,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.trackpad_hint_connected),
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateSubtle,
                    )
                }
            }
        }

        // Tactile Left / Middle / Right Click Paddles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SlateBorder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClickPaddle(
                label = stringResource(R.string.action_left_click),
                testTag = "left_click_button",
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick(MouseButton.LEFT, ClickAction.TAP)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .background(SlateBorder),
            )
            ClickPaddle(
                label = stringResource(R.string.action_middle_click),
                testTag = "middle_click_button",
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick(MouseButton.MIDDLE, ClickAction.TAP)
                },
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .background(SlateBorder),
            )
            ClickPaddle(
                label = stringResource(R.string.action_right_click),
                testTag = "right_click_button",
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onClick(MouseButton.RIGHT, ClickAction.TAP)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        // Media & Volume Strip (Pause, Vol Down, Vol Up) below Left/Right Click
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SlateBorder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(SlateWash.copy(alpha = 0.45f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaIconPaddle(
                icon = Icons.Rounded.Pause,
                contentDescription = stringResource(R.string.cd_play_pause),
                testTag = "media_pause_button",
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onMediaKey("PLAY_PAUSE", "Play / Pause")
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .background(SlateBorder),
            )
            MediaIconPaddle(
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                contentDescription = stringResource(R.string.cd_vol_down),
                testTag = "media_vol_down_button",
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onMediaKey("VOL_DOWN", "Volume Down")
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .background(SlateBorder),
            )
            MediaIconPaddle(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.cd_vol_up),
                testTag = "media_vol_up_button",
                onClick = {
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onMediaKey("VOL_UP", "Volume Up")
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ClickPaddle(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .testTag(testTag)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = CobaltAccent),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = SlateMuted,
        )
    }
}

@Composable
private fun MediaIconPaddle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .testTag(testTag)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = CobaltAccent),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = SlateMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}
