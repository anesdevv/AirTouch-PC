package com.example

import com.example.network.ClickAction
import com.example.network.MouseButton
import com.example.voice.VoiceAction
import com.example.voice.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun parsesMouseClickCommands() {
        val left = VoiceCommandParser.parse("Click.", autoExecuteCommands = true)
        assertTrue(left is VoiceAction.Click)
        assertEquals(MouseButton.LEFT, (left as VoiceAction.Click).button)
        assertEquals(ClickAction.TAP, left.action)

        val right = VoiceCommandParser.parse("right click", autoExecuteCommands = true)
        assertTrue(right is VoiceAction.Click)
        assertEquals(MouseButton.RIGHT, (right as VoiceAction.Click).button)

        val dbl = VoiceCommandParser.parse("Double Click!", autoExecuteCommands = true)
        assertTrue(dbl is VoiceAction.Click)
        assertEquals(ClickAction.DOUBLE, (dbl as VoiceAction.Click).action)
    }

    @Test
    fun parsesWindowsShortcutCommands() {
        val copy = VoiceCommandParser.parse("copy", autoExecuteCommands = true)
        assertTrue(copy is VoiceAction.KeyCombo)
        assertEquals("C", (copy as VoiceAction.KeyCombo).key)
        assertEquals(listOf("CTRL"), copy.modifiers)

        val switchWin = VoiceCommandParser.parse("switch window", autoExecuteCommands = true)
        assertTrue(switchWin is VoiceAction.KeyCombo)
        assertEquals("TAB", (switchWin as VoiceAction.KeyCombo).key)
        assertEquals(listOf("ALT"), switchWin.modifiers)
    }

    @Test
    fun respectsForcedTypePrefixAndDictationFallback() {
        val forced = VoiceCommandParser.parse("type click here", autoExecuteCommands = true)
        assertTrue(forced is VoiceAction.TypeText)
        assertEquals("click here", (forced as VoiceAction.TypeText).text)

        val sentence = VoiceCommandParser.parse("hello world", autoExecuteCommands = true)
        assertTrue(sentence is VoiceAction.TypeText)
        assertEquals("hello world", (sentence as VoiceAction.TypeText).text)

        val disabled = VoiceCommandParser.parse("copy", autoExecuteCommands = false)
        assertTrue(disabled is VoiceAction.TypeText)
        assertEquals("copy", (disabled as VoiceAction.TypeText).text)
    }
}
