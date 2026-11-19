package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CobaltTint
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SlateWash

private data class QuickKeyItem(
    val label: String,
    val keyCode: String,
    val modifiers: List<String> = emptyList(),
)

private val QuickWindowsKeys = listOf(
    QuickKeyItem("Esc", "ESC"),
    QuickKeyItem("Tab", "TAB"),
    QuickKeyItem("Win", "WIN"),
    QuickKeyItem("Alt+Tab", "TAB", listOf("ALT")),
    QuickKeyItem("Ctrl+C", "C", listOf("CTRL")),
    QuickKeyItem("Ctrl+V", "V", listOf("CTRL")),
    QuickKeyItem("Ctrl+Z", "Z", listOf("CTRL")),
    QuickKeyItem("←", "LEFT"),
    QuickKeyItem("↑", "UP"),
    QuickKeyItem("↓", "DOWN"),
    QuickKeyItem("→", "RIGHT"),
    QuickKeyItem("Vol-", "VOL_DOWN"),
    QuickKeyItem("Vol+", "VOL_UP"),
    QuickKeyItem("Mute", "VOL_MUTE"),
)

@Composable
fun KeyboardComposerPanel(
    visible: Boolean,
    liveTypeMode: Boolean,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleLiveType: () -> Unit,
    onSendNow: () -> Unit,
    onClearDraft: () -> Unit,
    onSpecialKey: (key: String, mods: List<String>, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SlateBorder),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                // Quick Windows Keys & Mode Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onToggleLiveType,
                        shape = RoundedCornerShape(10.dp),
                        color = if (liveTypeMode) CobaltTint else SlateWash,
                        border = BorderStroke(
                            1.dp,
                            if (liveTypeMode) CobaltAccent.copy(alpha = 0.4f) else SlateBorder,
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("keyboard_live_toggle"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = if (liveTypeMode) CobaltAccent else SlateMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (liveTypeMode) "Live" else "Batch",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (liveTypeMode) CobaltAccent else SlateMuted,
                            )
                        }
                    }

                    QuickWindowsKeys.forEach { item ->
                        Surface(
                            onClick = { onSpecialKey(item.keyCode, item.modifiers, item.label) },
                            shape = RoundedCornerShape(10.dp),
                            color = SlateWash,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("quick_key_${item.label.lowercase()}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateInk,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Text Input & Action Keys
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.keyboard_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSendNow() }),
                        trailingIcon = {
                            if (value.text.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearDraft,
                                    modifier = Modifier.testTag("keyboard_clear_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_text),
                                        tint = SlateMuted,
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CobaltAccent,
                            unfocusedBorderColor = SlateBorder,
                            focusedContainerColor = SlateWash.copy(alpha = 0.5f),
                            unfocusedContainerColor = SlateWash.copy(alpha = 0.5f),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .testTag("keyboard_text_input"),
                    )

                    IconButton(
                        onClick = { onSpecialKey("BACKSPACE", emptyList(), "Backspace") },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = SlateWash,
                            contentColor = SlateInk,
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("keyboard_backspace_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Backspace",
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = onSendNow,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = CobaltAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("keyboard_send_button"),
                    ) {
                        Icon(
                            imageVector = if (liveTypeMode || value.text.isEmpty()) {
                                Icons.AutoMirrored.Rounded.KeyboardReturn
                            } else {
                                Icons.AutoMirrored.Rounded.Send
                            },
                            contentDescription = stringResource(R.string.action_send_text),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
