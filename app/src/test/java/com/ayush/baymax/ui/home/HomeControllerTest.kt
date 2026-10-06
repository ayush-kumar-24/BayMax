package com.ayush.baymax.ui.home

import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.CareRecord
import com.ayush.baymax.agent.ChestContent
import com.ayush.baymax.agent.QuickChip
import com.ayush.baymax.voice.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeControllerTest {

    private class FakeSpeaker : Speaker {
        val lines = mutableListOf<String>()
        var stops = 0
        override suspend fun speak(text: String, lowBattery: Boolean) {
            lines += text
            delay(text.length * 50L)
        }
        override fun stop() { stops++ }
    }

    private fun TestScope.controller(speaker: Speaker = FakeSpeaker(), records: MutableList<CareRecord> = mutableListOf(), dialed: MutableList<String> = mutableListOf()) =
        HomeController(this, speaker = speaker, onSaveRecord = { records += it }, onDial = { dialed += it })

    @Test fun `ow wakes Baymax and shows the pain scale`() = runTest {
        val speaker = FakeSpeaker()
        val c = controller(speaker)
        c.send("ow")
        advanceUntilIdle()
        assertEquals(AgentState.PainScale, c.state.agentState)
        assertEquals(ChestContent.PainScale(), c.state.chest)
        assertTrue(c.state.tilt)
        assertTrue(speaker.lines.first().startsWith("Hello. I am Baymax"))
    }

    @Test fun `full loop through the scan to the satisfaction check and back to idle`() = runTest {
        val records = mutableListOf<CareRecord>()
        val c = controller(records = records)
        c.send("ow"); advanceUntilIdle()
        c.selectPain(4); advanceUntilIdle()
        assertEquals(AgentState.Interview, c.state.agentState)
        assertEquals(ChestContent.None, c.state.chest)
        assertEquals("Where does it hurt?", c.state.caption)
        assertTrue(c.state.chips.any { it.text == "Arm" })
        c.chip(QuickChip("Arm")); advanceUntilIdle()
        c.chip(QuickChip("Just now")); advanceUntilIdle()
        c.chip(QuickChip("No")); advanceUntilIdle()
        assertEquals(AgentState.Satisfaction, c.state.agentState)
        c.send("I am satisfied with my care"); advanceUntilIdle()
        assertEquals(AgentState.Idle, c.state.agentState)
        assertEquals("", c.state.caption)
        assertEquals(1, records.size)
    }

    @Test fun `a red flag interrupts Baymax mid-sentence and still lands in Emergency`() = runTest {
        val speaker = FakeSpeaker()
        val c = controller(speaker)
        c.send("ow")
        // Do not let the greeting finish.
        c.send("my chest hurts")
        advanceUntilIdle()
        assertEquals(AgentState.Emergency, c.state.agentState)
        assertTrue(c.state.chest is ChestContent.Emergency)
        assertTrue(c.state.emergency)
        assertTrue(speaker.stops > 0)
    }

    @Test fun `danger chip dials the emergency number`() = runTest {
        val dialed = mutableListOf<String>()
        val c = controller(dialed = dialed)
        c.send("heavy bleeding"); advanceUntilIdle()
        c.chip(c.state.chips.first { it.style == QuickChip.Style.Danger })
        assertEquals(listOf("112"), dialed)
    }

    @Test fun `muted Baymax shows captions without speaking`() = runTest {
        val speaker = FakeSpeaker()
        val c = controller(speaker)
        c.toggleMute()
        c.caseTap(); advanceUntilIdle()
        assertTrue(speaker.lines.isEmpty())
        assertEquals("How can I help you today?", c.state.caption)
    }

    @Test fun `low battery styles captions but never emergency lines`() = runTest {
        val c = controller()
        c.setLowBattery(true)
        c.caseTap(); advanceUntilIdle()
        assertEquals("hooow can i help you today?", c.state.caption)
        c.send("I can't breathe"); advanceUntilIdle()
        assertTrue(c.state.caption.startsWith("I have detected signs of a possible emergency"))
    }
}
