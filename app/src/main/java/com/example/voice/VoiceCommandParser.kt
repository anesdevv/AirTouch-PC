package com.example.voice

import com.example.network.ClickAction
import com.example.network.MouseButton

sealed interface VoiceAction {
    val label: String

    data class Click(
        val button: MouseButton,
        val action: ClickAction,
        override val label: String,
    ) : VoiceAction

    data class Scroll(
        val verticalAmount: Int,
        override val label: String,
    ) : VoiceAction

    data class KeyCombo(
        val key: String,
        val modifiers: List<String> = emptyList(),
        override val label: String,
    ) : VoiceAction

    data class TypeText(
        val text: String,
        override val label: String = "Typed text",
    ) : VoiceAction
}

object VoiceCommandParser {

    fun parse(rawTranscript: String, autoExecuteCommands: Boolean): VoiceAction {
        val trimmed = rawTranscript.trim()
        if (trimmed.isEmpty()) {
            return VoiceAction.TypeText("", label = "Empty")
        }

        if (!autoExecuteCommands) {
            return VoiceAction.TypeText(trimmed, label = "Dictated \"$trimmed\"")
        }

        val lower = trimmed.lowercase().replace(Regex("[.,!?]+$"), "").trim()

        // Explicit dictation prefix lets users force typing any phrase (e.g., "type click")
        val dictationPrefixes = listOf("type ", "dictate ", "write ")
        for (prefix in dictationPrefixes) {
            if (lower.startsWith(prefix)) {
                val payload = trimmed.substring(prefix.length).trimStart()
                if (payload.isNotEmpty()) {
                    return VoiceAction.TypeText(payload, label = "Typed \"$payload\"")
                }
            }
        }

        return when (lower) {
            "click", "left click", "mouse click", "tap" ->
                VoiceAction.Click(MouseButton.LEFT, ClickAction.TAP, "Left Click")

            "double click" ->
                VoiceAction.Click(MouseButton.LEFT, ClickAction.DOUBLE, "Double Click")

            "right click", "context menu" ->
                VoiceAction.Click(MouseButton.RIGHT, ClickAction.TAP, "Right Click")

            "middle click" ->
                VoiceAction.Click(MouseButton.MIDDLE, ClickAction.TAP, "Middle Click")

            "scroll down", "move down" ->
                VoiceAction.Scroll(-360, "Scroll Down")

            "scroll up", "move up" ->
                VoiceAction.Scroll(360, "Scroll Up")

            "page down" ->
                VoiceAction.KeyCombo("PAGE_DOWN", label = "Page Down")

            "page up" ->
                VoiceAction.KeyCombo("PAGE_UP", label = "Page Up")

            "enter", "press enter", "return", "hit enter", "submit" ->
                VoiceAction.KeyCombo("ENTER", label = "Enter")

            "backspace", "erase", "delete last", "delete character" ->
                VoiceAction.KeyCombo("BACKSPACE", label = "Backspace")

            "delete", "press delete" ->
                VoiceAction.KeyCombo("DELETE", label = "Delete")

            "escape", "press escape", "esc", "cancel" ->
                VoiceAction.KeyCombo("ESC", label = "Escape")

            "tab", "press tab", "next field" ->
                VoiceAction.KeyCombo("TAB", label = "Tab")

            "space", "spacebar", "press space" ->
                VoiceAction.KeyCombo("SPACE", label = "Space")

            "copy", "copy that" ->
                VoiceAction.KeyCombo("C", listOf("CTRL"), "Ctrl+C (Copy)")

            "paste", "paste that" ->
                VoiceAction.KeyCombo("V", listOf("CTRL"), "Ctrl+V (Paste)")

            "cut", "cut that" ->
                VoiceAction.KeyCombo("X", listOf("CTRL"), "Ctrl+X (Cut)")

            "undo", "undo that" ->
                VoiceAction.KeyCombo("Z", listOf("CTRL"), "Ctrl+Z (Undo)")

            "redo" ->
                VoiceAction.KeyCombo("Y", listOf("CTRL"), "Ctrl+Y (Redo)")

            "select all" ->
                VoiceAction.KeyCombo("A", listOf("CTRL"), "Ctrl+A (Select All)")

            "save", "save file" ->
                VoiceAction.KeyCombo("S", listOf("CTRL"), "Ctrl+S (Save)")

            "find", "search" ->
                VoiceAction.KeyCombo("F", listOf("CTRL"), "Ctrl+F (Find)")

            "switch window", "next window", "alt tab" ->
                VoiceAction.KeyCombo("TAB", listOf("ALT"), "Alt+Tab (Switch Window)")

            "close tab" ->
                VoiceAction.KeyCombo("W", listOf("CTRL"), "Ctrl+W (Close Tab)")

            "close window", "close app" ->
                VoiceAction.KeyCombo("F4", listOf("ALT"), "Alt+F4 (Close Window)")

            "new tab" ->
                VoiceAction.KeyCombo("T", listOf("CTRL"), "Ctrl+T (New Tab)")

            "show desktop", "minimize all" ->
                VoiceAction.KeyCombo("D", listOf("WIN"), "Win+D (Desktop)")

            "start menu", "open start", "windows key" ->
                VoiceAction.KeyCombo("WIN", label = "Windows Start")

            "volume up", "turn up volume", "louder" ->
                VoiceAction.KeyCombo("VOL_UP", label = "Volume Up")

            "volume down", "turn down volume", "quieter" ->
                VoiceAction.KeyCombo("VOL_DOWN", label = "Volume Down")

            "mute", "unmute", "mute audio" ->
                VoiceAction.KeyCombo("VOL_MUTE", label = "Toggle Mute")

            "play", "pause", "play pause", "resume" ->
                VoiceAction.KeyCombo("PLAY_PAUSE", label = "Play / Pause")

            "next track", "skip track", "next song" ->
                VoiceAction.KeyCombo("NEXT_TRACK", label = "Next Track")

            "previous track", "previous song" ->
                VoiceAction.KeyCombo("PREV_TRACK", label = "Previous Track")

            "screenshot", "take screenshot", "print screen" ->
                VoiceAction.KeyCombo("PRINTSCREEN", label = "Print Screen")

            "arrow up", "up arrow" ->
                VoiceAction.KeyCombo("UP", label = "Arrow Up")

            "arrow down", "down arrow" ->
                VoiceAction.KeyCombo("DOWN", label = "Arrow Down")

            "arrow left", "left arrow" ->
                VoiceAction.KeyCombo("LEFT", label = "Arrow Left")

            "arrow right", "right arrow" ->
                VoiceAction.KeyCombo("RIGHT", label = "Arrow Right")

            else -> VoiceAction.TypeText(trimmed, label = "Typed \"$trimmed\"")
        }
    }

    val supportedExamples: List<Pair<String, String>> = listOf(
        "\"Click\" / \"Right click\" / \"Double click\"" to "Mouse clicks",
        "\"Scroll down\" / \"Scroll up\"" to "Smooth page scroll",
        "\"Copy\" / \"Paste\" / \"Undo\" / \"Select all\"" to "Clipboard shortcuts",
        "\"Switch window\" / \"Show desktop\" / \"Close tab\"" to "Window navigation",
        "\"Volume up\" / \"Mute\" / \"Play pause\"" to "Media & audio",
        "\"Type hello world\" or any sentence" to "Direct text dictation",
    )
}
