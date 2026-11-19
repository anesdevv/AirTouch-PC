package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.companion.WindowsBatchLauncher
import com.example.companion.WindowsCompanionScript
import com.example.network.ConnectionState
import com.example.network.PcHost
import com.example.ui.theme.AlabasterCanvas
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSetupSheet(
    connectionState: ConnectionState,
    discoveredHosts: List<PcHost>,
    recentHosts: List<PcHost>,
    phoneWifiIp: String?,
    onScanWifi: () -> Unit,
    onConnectHost: (PcHost) -> Unit,
    onConnectManual: (ip: String, tcpPort: Int) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onFeedbackBanner: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val errorHostIp = (connectionState as? ConnectionState.Error)?.host?.ipAddress
    val initialIp = remember(recentHosts, errorHostIp) {
        errorHostIp ?: recentHosts.firstOrNull()?.ipAddress ?: ""
    }
    var ipText by remember(initialIp) { mutableStateOf(initialIp) }
    var portText by remember { mutableStateOf(PcHost.DEFAULT_TCP_PORT.toString()) }
    var showScriptPreview by remember { mutableStateOf(false) }
    var useBatchLauncher by remember { mutableStateOf(true) }
    var copiedJustNow by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            onFeedbackBanner(context.getString(R.string.qr_camera_permission_rationale))
        }
    }

    val launchQrScanner = {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Connect Windows PC",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SlateInk,
                    )
                    Text(
                        text = if (phoneWifiIp != null) {
                            "Phone Wi-Fi IP: $phoneWifiIp • UDP + TCP"
                        } else {
                            "Same Wi-Fi network • UDP + TCP low-latency"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateMuted,
                    )
                }
                if (connectionState is ConnectionState.Connected) {
                    OutlinedButton(
                        onClick = {
                            onDisconnect()
                            onDismiss()
                        },
                        border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAlert),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("disconnect_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_disconnect))
                    }
                }
            }

            // Fast QR Pairing Card Button
            Surface(
                onClick = launchQrScanner,
                shape = RoundedCornerShape(16.dp),
                color = CobaltTint.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, CobaltAccent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_qr_button"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CobaltAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = stringResource(R.string.cd_scan_qr),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.action_scan_qr),
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateInk,
                        )
                        Text(
                            text = "Pair instantly from the terminal QR code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                        )
                    }
                }
            }

            // Error Notice & Windows Firewall Fix
            if (connectionState is ConnectionState.Error) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CoralTint,
                    border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralAlert,
                        )
                        Text(
                            text = "Fix: Run ${WindowsBatchLauncher.FILENAME} & click Yes on the firewall prompt, or paste these lines in Administrator PowerShell / CMD:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateInk,
                        )
                        val firewallCmd =
                            "netsh advfirewall firewall add rule name=\"AirTouch PC TCP\" dir=in action=allow protocol=TCP localport=54546 profile=any\nnetsh advfirewall firewall add rule name=\"AirTouch PC UDP\" dir=in action=allow protocol=UDP localport=54545 profile=any"
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AirTouch Firewall Command", firewallCmd))
                                onFeedbackBanner("Copied PowerShell/CMD firewall commands")
                            },
                            border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateInk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("copy_firewall_cmd_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Windows Firewall Fix Command")
                        }
                    }
                }
            }

            // Discovered & Recent PCs
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlabasterCanvas,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Nearby & Recent PCs",
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateInk,
                        )
                        TextButton(
                            onClick = onScanWifi,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("scan_wifi_button"),
                        ) {
                            if (connectionState is ConnectionState.Discovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = CobaltAccent,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_scan_wifi))
                        }
                    }

                    val combinedHosts = remember(discoveredHosts, recentHosts) {
                        val map = linkedMapOf<String, Pair<PcHost, Boolean>>()
                        discoveredHosts.forEach { map[it.ipAddress] = it to true }
                        recentHosts.forEach {
                            if (!map.containsKey(it.ipAddress)) {
                                map[it.ipAddress] = it to false
                            }
                        }
                        map.values.toList()
                    }

                    if (combinedHosts.isEmpty()) {
                        Text(
                            text = "No PC found yet. Double-click ${WindowsBatchLauncher.FILENAME} on Windows and tap Scan Wi-Fi, or enter its IP below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                        )
                    } else {
                        combinedHosts.forEach { (host, isLive) ->
                            val isActive = (connectionState as? ConnectionState.Connected)?.host?.ipAddress == host.ipAddress
                            Surface(
                                onClick = { onConnectHost(host) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isActive) EmeraldTint.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) EmeraldOnline else SlateBorder,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("host_item_${host.ipAddress}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isLive || isActive) CobaltTint else SlateWash),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Computer,
                                            contentDescription = null,
                                            tint = if (isLive || isActive) CobaltAccent else SlateMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = host.displayLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SlateInk,
                                        )
                                        Text(
                                            text = "${host.ipAddress}:${host.tcpPort}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateMuted,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = when {
                                            isActive -> EmeraldTint
                                            isLive -> CobaltTint
                                            else -> SlateWash
                                        },
                                    ) {
                                        Text(
                                            text = when {
                                                isActive -> "Active"
                                                isLive -> "Online"
                                                else -> "Recent"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = when {
                                                isActive -> EmeraldOnline
                                                isLive -> CobaltAccent
                                                else -> SlateMuted
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Manual IP Entry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("PC IP Address") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onConnectManual(
                                ipText,
                                portText.toIntOrNull() ?: PcHost.DEFAULT_TCP_PORT,
                            )
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CobaltAccent,
                        unfocusedBorderColor = SlateBorder,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_ip_input"),
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CobaltAccent,
                        unfocusedBorderColor = SlateBorder,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .width(96.dp)
                        .testTag("manual_port_input"),
                )
            }

            Button(
                onClick = {
                    onConnectManual(
                        ipText,
                        portText.toIntOrNull() ?: PcHost.DEFAULT_TCP_PORT,
                    )
                },
                enabled = ipText.isNotBlank() && connectionState !is ConnectionState.Connecting,
                colors = ButtonDefaults.buttonColors(containerColor = CobaltAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("manual_connect_button"),
            ) {
                if (connectionState is ConnectionState.Connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.status_connecting))
                } else {
                    Text(stringResource(R.string.action_connect))
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Zero-Install Windows Companion Card
            val activeFilename = if (useBatchLauncher) {
                WindowsBatchLauncher.FILENAME
            } else {
                WindowsCompanionScript.FILENAME
            }
            val activeSourceCode = if (useBatchLauncher) {
                WindowsBatchLauncher.SOURCE_CODE
            } else {
                WindowsCompanionScript.SOURCE_CODE
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlabasterCanvas,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CobaltTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = CobaltAccent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Windows Companion",
                                style = MaterialTheme.typography.titleSmall,
                                color = SlateInk,
                            )
                            Text(
                                text = if (useBatchLauncher) {
                                    "Zero installs • No Python needed • Any Windows 10/11 PC"
                                } else {
                                    "Zero dependencies • Python 3 standard library only"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted,
                            )
                        }
                    }

                    // Format Selector: Zero-Install .bat (default) vs Python (.py)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CompanionModePill(
                            label = stringResource(R.string.companion_tab_bat),
                            selected = useBatchLauncher,
                            onClick = {
                                useBatchLauncher = true
                                copiedJustNow = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("companion_mode_bat"),
                        )
                        CompanionModePill(
                            label = stringResource(R.string.companion_tab_py),
                            selected = !useBatchLauncher,
                            onClick = {
                                useBatchLauncher = false
                                copiedJustNow = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("companion_mode_py"),
                        )
                    }

                    Text(
                        text = if (useBatchLauncher) {
                            "1. Send or save ${WindowsBatchLauncher.FILENAME} to any Windows PC.\n2. Double-click it (accept the firewall prompt once) and scan the QR code."
                        } else {
                            "1. Save ${WindowsCompanionScript.FILENAME} on your Windows PC.\n2. Run: python ${WindowsCompanionScript.FILENAME}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateInk,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyScriptToClipboard(
                                    context = context,
                                    filename = activeFilename,
                                    sourceCode = activeSourceCode,
                                )
                                copiedJustNow = true
                                onFeedbackBanner("Copied $activeFilename")
                            },
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_script_button"),
                        ) {
                            Icon(
                                imageVector = if (copiedJustNow) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(
                                    if (useBatchLauncher) R.string.cd_copy_bat else R.string.cd_copy_script,
                                ),
                                tint = if (copiedJustNow) EmeraldOnline else CobaltAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    copiedJustNow -> "Copied"
                                    useBatchLauncher -> stringResource(R.string.action_copy_bat)
                                    else -> stringResource(R.string.action_copy_script)
                                },
                            )
                        }

                        Button(
                            onClick = {
                                shareScriptFile(
                                    context = context,
                                    filename = activeFilename,
                                    sourceCode = activeSourceCode,
                                    mimeType = if (useBatchLauncher) "text/plain" else "text/x-python",
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CobaltAccent,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_script_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = stringResource(
                                    if (useBatchLauncher) R.string.cd_share_bat else R.string.cd_share_script,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (useBatchLauncher) R.string.action_share_bat else R.string.action_share_script,
                                ),
                            )
                        }
                    }

                    TextButton(
                        onClick = { showScriptPreview = !showScriptPreview },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("toggle_script_preview_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (showScriptPreview) "Hide $activeFilename Code" else "View $activeFilename Code")
                    }

                    AnimatedVisibility(visible = showScriptPreview) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = activeSourceCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateInk,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onHostScanned = { scannedHost ->
                showQrScanner = false
                onConnectHost(scannedHost)
            },
            onDismiss = { showQrScanner = false },
        )
    }
}

@Composable
private fun CompanionModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) CobaltTint else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, CobaltAccent.copy(alpha = 0.45f)) else null,
        modifier = modifier.height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) SlateInk else SlateMuted,
            )
        }
    }
}

private fun copyScriptToClipboard(
    context: Context,
    filename: String,
    sourceCode: String,
) {
    val normalized = if (filename.endsWith(".bat", ignoreCase = true)) {
        sourceCode.replace("\r\n", "\n").replace("\n", "\r\n")
    } else {
        sourceCode
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(filename, normalized))
}

private fun shareScriptFile(
    context: Context,
    filename: String,
    sourceCode: String,
    mimeType: String,
) {
    runCatching {
        val dir = File(context.cacheDir, "scripts").apply { mkdirs() }
        val scriptFile = File(dir, filename)
        val normalized = if (filename.endsWith(".bat", ignoreCase = true)) {
            sourceCode.replace("\r\n", "\n").replace("\n", "\r\n")
        } else {
            sourceCode
        }
        scriptFile.writeText(normalized, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            scriptFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Send $filename to PC"))
    }
}
