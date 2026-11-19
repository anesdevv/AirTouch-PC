package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.network.ConnectionState
import com.example.ui.components.ConnectSetupSheet
import com.example.ui.components.KeyboardComposerPanel
import com.example.ui.components.PrecisionTrackpad
import com.example.ui.components.PreferencesSheet
import com.example.ui.components.PresentationPanel
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.VoiceOverlayCard
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CobaltTint
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralTint
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateWash
import com.example.voice.VoiceState

@Composable
fun MainScreen(
    viewModel: RemoteViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showTopBarQrScanner by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showTopBarQrScanner = true
        } else {
            viewModel.showBanner(context.getString(R.string.qr_camera_permission_rationale))
        }
    }

    val launchTopBarQr = {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            showTopBarQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecognition()
        } else {
            viewModel.showBanner(context.getString(R.string.voice_permission_rationale))
        }
    }

    val onVoiceActionClick = {
        if (state.connectionState !is ConnectionState.Connected) {
            viewModel.openConnectSheet()
        } else if (!viewModel.isSpeechAvailable()) {
            viewModel.showBanner(context.getString(R.string.voice_unavailable))
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                if (state.preferences.hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (state.voiceState is VoiceState.Listening) {
                    viewModel.stopVoiceRecognition()
                } else {
                    viewModel.startVoiceRecognition()
                }
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top Bar: Connection Pill + Quick QR Scan + Preferences Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionStatusPill(
                    connectionState = state.connectionState,
                    onClick = viewModel::openConnectSheet,
                    modifier = Modifier.weight(1f),
                )

                FilledTonalIconButton(
                    onClick = launchTopBarQr,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = CobaltAccent,
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_bar_qr_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = stringResource(R.string.cd_scan_qr),
                    )
                }

                FilledTonalIconButton(
                    onClick = viewModel::openPreferencesSheet,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = SlateInk,
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("preferences_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                    )
                }
            }

            // Touchpad / Presentation Mode Switcher
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WorkspaceModeTab(
                        label = stringResource(R.string.mode_touchpad),
                        icon = Icons.Rounded.TouchApp,
                        selected = !state.isPresentationMode,
                        onClick = { viewModel.setPresentationMode(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_touchpad_tab"),
                    )
                    WorkspaceModeTab(
                        label = stringResource(R.string.mode_presentation),
                        icon = Icons.Rounded.Slideshow,
                        selected = state.isPresentationMode,
                        onClick = { viewModel.setPresentationMode(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_presentation_tab"),
                    )
                }
            }

            // Central Workspace: Precision Trackpad or Presentation Remote
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = state.isPresentationMode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "workspace_mode_transition",
                    modifier = Modifier.fillMaxSize(),
                ) { presentationActive ->
                    if (presentationActive) {
                        PresentationPanel(
                            isConnected = state.connectionState is ConnectionState.Connected,
                            isLaserActive = state.isLaserActive,
                            isBlackScreenActive = state.isBlackScreenActive,
                            hapticsEnabled = state.preferences.hapticsEnabled,
                            onStartShow = viewModel::startPresentation,
                            onEndShow = viewModel::endPresentation,
                            onToggleBlackScreen = viewModel::toggleBlackScreen,
                            onPrevSlide = viewModel::previousSlide,
                            onNextSlide = viewModel::nextSlide,
                            onLaserChange = viewModel::setLaserPointerActive,
                            onLaserMove = viewModel::onTrackpadMove,
                            onResetAccumulators = viewModel::resetAccumulators,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PrecisionTrackpad(
                            isConnected = state.connectionState is ConnectionState.Connected,
                            isDragging = state.isDragging,
                            tapToClick = state.preferences.tapToClick,
                            hapticsEnabled = state.preferences.hapticsEnabled,
                            onMove = viewModel::onTrackpadMove,
                            onScroll = viewModel::onTrackpadScroll,
                            onResetAccumulators = viewModel::resetAccumulators,
                            onClick = viewModel::sendClick,
                            onMediaKey = { key, label ->
                                viewModel.sendSpecialKey(key, emptyList(), bannerLabel = label)
                            },
                            onDragHoldChange = viewModel::setDragHold,
                            onConnectPrompt = viewModel::openConnectSheet,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Transient Action Confirmation Pill
                this@Column.AnimatedVisibility(
                    visible = state.lastActionBanner != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    state.lastActionBanner?.let { banner ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                // Voice Listening / Confirmation Overlay
                VoiceOverlayCard(
                    voiceState = state.voiceState,
                    autoCommandsEnabled = state.preferences.voiceAutoCommands,
                    onStopListening = viewModel::stopVoiceRecognition,
                    onRetry = { viewModel.startVoiceRecognition() },
                    onDismiss = viewModel::dismissVoiceOverlay,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Expandable Keyboard Bar
            KeyboardComposerPanel(
                visible = state.isKeyboardExpanded,
                liveTypeMode = state.liveTypeMode,
                value = state.keyboardInput,
                onValueChange = viewModel::onKeyboardInputChange,
                onToggleLiveType = viewModel::toggleLiveTypeMode,
                onSendNow = viewModel::sendKeyboardDraftNow,
                onClearDraft = viewModel::clearKeyboardDraft,
                onSpecialKey = { key, mods, label ->
                    viewModel.sendSpecialKey(key, mods, bannerLabel = label)
                },
            )

            // Minimal Bottom Action Dock
            BottomActionDock(
                isKeyboardExpanded = state.isKeyboardExpanded,
                isListening = state.voiceState is VoiceState.Listening,
                onToggleKeyboard = viewModel::toggleKeyboard,
                onVoiceClick = onVoiceActionClick,
                onQuickEnter = {
                    viewModel.sendSpecialKey("ENTER", emptyList(), bannerLabel = "Enter")
                },
            )
        }
    }

    if (state.isConnectSheetOpen) {
        ConnectSetupSheet(
            connectionState = state.connectionState,
            discoveredHosts = state.discoveredHosts,
            recentHosts = state.recentHosts,
            phoneWifiIp = state.phoneWifiIp,
            onScanWifi = viewModel::scanWifiHosts,
            onConnectHost = viewModel::connectToHost,
            onConnectManual = { ip, port -> viewModel.connectManual(ip, tcpPort = port) },
            onDisconnect = viewModel::disconnect,
            onDismiss = viewModel::closeConnectSheet,
            onFeedbackBanner = viewModel::showBanner,
        )
    }

    if (state.isPreferencesSheetOpen) {
        PreferencesSheet(
            preferences = state.preferences,
            onUpdate = viewModel::updatePreferences,
            onDismiss = viewModel::closePreferencesSheet,
        )
    }

    if (showTopBarQrScanner) {
        QrScannerDialog(
            onHostScanned = { scannedHost ->
                showTopBarQrScanner = false
                viewModel.connectToHost(scannedHost)
            },
            onDismiss = { showTopBarQrScanner = false },
        )
    }
}

@Composable
private fun ConnectionStatusPill(
    connectionState: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isError = connectionState is ConnectionState.Error
    val dotColor = when {
        isConnected -> EmeraldOnline
        isError -> CoralAlert
        connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Discovering -> CobaltAccent
        else -> SlateMuted
    }
    val pillTint = when {
        isConnected -> EmeraldTint.copy(alpha = 0.6f)
        isError -> CoralTint
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = pillTint,
        border = BorderStroke(
            1.dp,
            if (isConnected) EmeraldOnline.copy(alpha = 0.35f) else SlateBorder,
        ),
        modifier = modifier
            .height(48.dp)
            .testTag("connection_status_pill"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = if (isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = stringResource(R.string.cd_connection_status),
                tint = if (isConnected) EmeraldOnline else SlateMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))

            val primaryText = when (connectionState) {
                ConnectionState.Disconnected -> stringResource(R.string.status_tap_to_connect)
                ConnectionState.Discovering -> stringResource(R.string.status_discovering)
                is ConnectionState.Connecting -> "Connecting to ${connectionState.host.displayLabel}…"
                is ConnectionState.Connected -> connectionState.host.displayLabel
                is ConnectionState.Error -> "Disconnected • Tap to retry"
            }

            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall,
                color = SlateInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (connectionState is ConnectionState.Connected && connectionState.latencyMs != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ) {
                    Text(
                        text = "${connectionState.latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOnline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionDock(
    isKeyboardExpanded: Boolean,
    isListening: Boolean,
    onToggleKeyboard: () -> Unit,
    onVoiceClick: () -> Unit,
    onQuickEnter: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Keyboard Toggle Pill
            Surface(
                onClick = onToggleKeyboard,
                shape = RoundedCornerShape(16.dp),
                color = if (isKeyboardExpanded) CobaltTint else SlateWash,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("dock_keyboard_button"),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isKeyboardExpanded) Icons.Rounded.KeyboardHide else Icons.Rounded.Keyboard,
                        contentDescription = stringResource(R.string.cd_keyboard_toggle),
                        tint = if (isKeyboardExpanded) CobaltAccent else SlateInk,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_keyboard),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isKeyboardExpanded) CobaltAccent else SlateInk,
                    )
                }
            }

            // Primary Voice Dictation & Command Button
            Surface(
                onClick = onVoiceClick,
                shape = RoundedCornerShape(16.dp),
                color = if (isListening) CoralAlert else CobaltAccent,
                modifier = Modifier
                    .weight(1.25f)
                    .height(50.dp)
                    .testTag("dock_voice_button"),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.cd_voice_mic),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "Listening…" else stringResource(R.string.action_voice),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // Quick Enter Key
            FilledTonalIconButton(
                onClick = onQuickEnter,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = SlateWash,
                    contentColor = SlateInk,
                ),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("dock_enter_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardReturn,
                    contentDescription = "Enter key",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CobaltTint else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, CobaltAccent.copy(alpha = 0.45f)) else null,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) CobaltAccent else SlateMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) SlateInk else SlateMuted,
                maxLines = 1,
            )
        }
    }
}
