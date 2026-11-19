package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RemotePreferences
import com.example.ui.theme.AlabasterCanvas
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import com.example.voice.VoiceCommandParser
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesSheet(
    preferences: RemotePreferences,
    onUpdate: ((RemotePreferences) -> RemotePreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val uriHandler = LocalUriHandler.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.action_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = SlateInk,
            )

            // Sliders Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlabasterCanvas,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Pointer Speed",
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateInk,
                        )
                        Text(
                            text = "${(preferences.pointerSensitivity * 10f).roundToInt() / 10f}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltAccent,
                        )
                    }
                    Slider(
                        value = preferences.pointerSensitivity,
                        onValueChange = { v -> onUpdate { it.copy(pointerSensitivity = v) } },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = CobaltAccent,
                            activeTrackColor = CobaltAccent,
                        ),
                        modifier = Modifier.testTag("pointer_speed_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Scroll Speed",
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateInk,
                        )
                        Text(
                            text = "${(preferences.scrollSensitivity * 10f).roundToInt() / 10f}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltAccent,
                        )
                    }
                    Slider(
                        value = preferences.scrollSensitivity,
                        onValueChange = { v -> onUpdate { it.copy(scrollSensitivity = v) } },
                        valueRange = 0.5f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = CobaltAccent,
                            activeTrackColor = CobaltAccent,
                        ),
                        modifier = Modifier.testTag("scroll_speed_slider"),
                    )
                }
            }

            // Toggles Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlabasterCanvas,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    PreferenceToggleRow(
                        title = "Tap to Click",
                        subtitle = "Single tap to click, 2-finger tap to right-click",
                        checked = preferences.tapToClick,
                        testTag = "toggle_tap_to_click",
                        onCheckedChange = { checked -> onUpdate { it.copy(tapToClick = checked) } },
                    )
                    HorizontalDivider(color = SlateBorder)
                    PreferenceToggleRow(
                        title = "Natural Scrolling",
                        subtitle = "Content follows two-finger swipe direction",
                        checked = preferences.naturalScrolling,
                        testTag = "toggle_natural_scroll",
                        onCheckedChange = { checked -> onUpdate { it.copy(naturalScrolling = checked) } },
                    )
                    HorizontalDivider(color = SlateBorder)
                    PreferenceToggleRow(
                        title = "Haptic Click Feedback",
                        subtitle = "Subtle vibration on clicks and voice actions",
                        checked = preferences.hapticsEnabled,
                        testTag = "toggle_haptics",
                        onCheckedChange = { checked -> onUpdate { it.copy(hapticsEnabled = checked) } },
                    )
                    HorizontalDivider(color = SlateBorder)
                    PreferenceToggleRow(
                        title = "Voice Action Commands",
                        subtitle = "Execute phrases like \"copy\" or \"scroll down\" automatically",
                        checked = preferences.voiceAutoCommands,
                        testTag = "toggle_voice_commands",
                        onCheckedChange = { checked -> onUpdate { it.copy(voiceAutoCommands = checked) } },
                    )
                }
            }

            // Voice Command Cheat Sheet
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlabasterCanvas,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Voice Command Examples",
                        style = MaterialTheme.typography.titleSmall,
                        color = SlateInk,
                    )
                    Spacer(Modifier.height(2.dp))
                    VoiceCommandParser.supportedExamples.forEach { (phrase, action) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = phrase,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateInk,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = action,
                                style = MaterialTheme.typography.labelMedium,
                                color = SlateMuted,
                            )
                        }
                    }
                }
            }

            // About Section
            val githubUrl = stringResource(R.string.about_github_url)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlabasterCanvas,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_about_card"),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.about_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = SlateInk,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.about_version_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                        )
                        Text(
                            text = stringResource(R.string.about_version_value),
                            style = MaterialTheme.typography.labelLarge,
                            color = SlateInk,
                            modifier = Modifier.testTag("about_version_text"),
                        )
                    }
                    HorizontalDivider(color = SlateBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.about_creator_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted,
                        )
                        Text(
                            text = stringResource(R.string.about_creator_name),
                            style = MaterialTheme.typography.titleSmall,
                            color = SlateInk,
                            modifier = Modifier.testTag("about_creator_text"),
                        )
                    }
                    HorizontalDivider(color = SlateBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                runCatching { uriHandler.openUri(githubUrl) }
                            }
                            .padding(vertical = 4.dp)
                            .testTag("about_github_link"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = CobaltAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.labelMedium,
                                color = SlateMuted,
                            )
                            Text(
                                text = githubUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CobaltAccent,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open GitHub profile",
                            tint = CobaltAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SlateInk,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = CobaltAccent,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}
