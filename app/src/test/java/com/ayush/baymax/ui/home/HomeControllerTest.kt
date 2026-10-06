package com.ayush.baymax.ui.home

import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.Brain
import com.ayush.baymax.agent.ChestContent
import com.ayush.baymax.agent.HealthReadings
import com.ayush.baymax.agent.QuickChip
import com.ayush.baymax.agent.Thought
import com.ayush.baymax.agent.ToolAction
import com.ayush.baymax.data.ChatMessage
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.data.InMemoryRepository
import com.ayush.baymax.data.Reminder
import com.ayush.baymax.data.ReminderScheduler
import com.ayush.baymax.data.TrustedContact
import com.ayush.baymax.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private class FakeScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Reminder>()
        override fun schedule(reminder: Reminder) { scheduled += reminder }
        override fun cancel(id: Long) = Unit
        override fun cancelAll() = Unit
    }

    private val repo = InMemoryRepository()
    private val scopes = mutableListOf<CoroutineScope>()

    private fun runTest(body: suspend TestScope.() -> Unit) = kotlinx.coroutines.test.runTest {
        try { body() } finally { scopes.forEach { it.cancel() } }
    }
    private val dialed = mutableListOf<String>()
    private val sent = mutableListOf<Triple<TrustedContact, String, ContactApp>>()

    private fun TestScope.controller(
        speaker: Speaker = FakeSpeaker(),
        brain: Brain = Brain { _, _, _ -> Thought(line = null, unavailable = true) },
        scheduler: ReminderScheduler = FakeScheduler(),
        readings: HealthReadings? = null,
    ) = HomeController(
        // Same test dispatcher (so advanceUntilIdle drives it) but not a child of the test,
        // so its endless settings collectors do not keep runTest waiting.
        CoroutineScope(coroutineContext + Job()).also { scopes += it },
        speaker = speaker,
        brain = brain,
        repository = repo,
        healthReadings = { readings },
        reminders = scheduler,
        onDial = { dialed += it },
        onSendMessage = { c, t, a -> sent += Triple(c, t, a) },
        now = { 1_000_000L },
    )

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

    @Test fun `full loop saves the session to the health log and returns to idle`() = runTest {
        val c = controller()
        c.send("ow"); advanceUntilIdle()
        c.selectPain(4); advanceUntilIdle()
        assertEquals(AgentState.Interview, c.state.agentState)
        assertEquals("Where does it hurt?", c.state.caption)
        c.chip(QuickChip("Arm")); advanceUntilIdle()
        c.chip(QuickChip("Just now")); advanceUntilIdle()
        c.chip(QuickChip("No")); advanceUntilIdle()
        assertEquals(AgentState.Satisfaction, c.state.agentState)
        c.send("I am satisfied with my care"); advanceUntilIdle()
        assertEquals(AgentState.Idle, c.state.agentState)
        val entries = repo.healthEntries.first()
        assertEquals(1, entries.size)
        assertEquals(4, entries.single().painLevel)
        assertEquals(1, repo.sessions.size)
    }

    @Test fun `a red flag interrupts Baymax mid-sentence and still lands in Emergency`() = runTest {
        val speaker = FakeSpeaker()
        val c = controller(speaker)
        c.send("ow")
        c.send("my chest hurts")
        advanceUntilIdle()
        assertEquals(AgentState.Emergency, c.state.agentState)
        assertTrue(c.state.chest is ChestContent.Emergency)
        assertTrue(speaker.stops > 0)
    }

    @Test fun `danger chip dials the configured emergency number`() = runTest {
        repo.updateSettings { it.copy(emergencyNumber = "108") }
        val c = controller()
        advanceUntilIdle()
        c.send("heavy bleeding"); advanceUntilIdle()
        c.chip(c.state.chips.first { it.style == QuickChip.Style.Danger })
        assertEquals(listOf("108"), dialed)
    }

    @Test fun `voice off in settings shows captions without speaking`() = runTest {
        repo.updateSettings { it.copy(voiceOn = false) }
        val speaker = FakeSpeaker()
        val c = controller(speaker)
        advanceUntilIdle()
        c.caseTap(); advanceUntilIdle()
        assertTrue(c.state.muted)
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

    @Test fun `T-5 no LLM reachable gives the offline line without crashing`() = runTest {
        val c = controller()
        c.caseTap(); advanceUntilIdle()
        c.send("tell me a story"); advanceUntilIdle()
        assertEquals(HomeController.OFFLINE_LINE, c.state.caption)
        assertEquals(AgentState.Listening, c.state.agentState)
    }

    @Test fun `LLM reply is spoken and its reminder tool runs`() = runTest {
        val scheduler = FakeScheduler()
        val brain = Brain { _, _, _ -> Thought("Of course. I will help you stay hydrated.", listOf(ToolAction.CreateReminder("drink water", 120, 120))) }
        val c = controller(brain = brain, scheduler = scheduler)
        c.caseTap(); advanceUntilIdle()
        c.send("can you help me drink more water"); advanceUntilIdle()
        assertEquals("Of course. I will help you stay hydrated.", c.state.caption)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(120L, scheduler.scheduled.single().repeatIntervalMinutes)
        assertEquals(1, repo.reminders.first().size)
    }

    @Test fun `UC-4 local reminder works without any LLM`() = runTest {
        val scheduler = FakeScheduler()
        val c = controller(scheduler = scheduler)
        c.caseTap(); advanceUntilIdle()
        c.send("Remind me to drink water every 2 hours"); advanceUntilIdle()
        assertEquals("Reminder set. I will remind you to drink water every two hours.", c.state.caption)
        assertEquals(1_000_000L + 120 * 60_000, scheduler.scheduled.single().firstTime)
    }

    @Test fun `health data tool reads Health Connect`() = runTest {
        val brain = Brain { _, _, _ -> Thought(null, listOf(ToolAction.ReadHealth)) }
        val c = controller(brain = brain, readings = HealthReadings(72, 5400, 420))
        c.caseTap(); advanceUntilIdle()
        c.send("how did I do today"); advanceUntilIdle()
        assertEquals("Today you have walked 5,400 steps. You slept 7 hours and 0 minutes. Your latest heart rate is 72 beats per minute.", c.state.caption)
    }

    @Test fun `UC-3 mood check drafts a message that is only sent after confirmation`() = runTest {
        repo.addContact(TrustedContact(name = "Priya", phone = "+91 98765 43210", preferredApp = ContactApp.WhatsApp))
        val c = controller()
        advanceUntilIdle()
        c.caseTap(); advanceUntilIdle()
        c.send("I feel really low today"); advanceUntilIdle()
        c.send("2"); advanceUntilIdle()
        assertTrue(c.state.caption.contains("Priya"))
        assertNull(c.state.draft)
        c.send("Yes, please"); advanceUntilIdle()
        val draft = c.state.draft
        assertNotNull(draft)
        assertTrue(sent.isEmpty())
        c.sendDraft(draft!!.text, ContactApp.WhatsApp)
        assertEquals("Priya", sent.single().first.name)
        assertNull(c.state.draft)
    }

    @Test fun `conversation is stored for the conversation sheet`() = runTest {
        val c = controller()
        c.caseTap(); advanceUntilIdle()
        c.send("hello"); advanceUntilIdle()
        val chat = repo.chat.first()
        assertTrue(chat.any { it.role == ChatMessage.Role.User && it.text == "hello" })
        assertTrue(chat.any { it.role == ChatMessage.Role.Agent && it.text.startsWith("Hello. I am Baymax") })
    }

    @Test fun `my name is is remembered`() = runTest {
        val c = controller()
        c.caseTap(); advanceUntilIdle()
        c.send("My name is Ayush"); advanceUntilIdle()
        assertEquals("The user's name is Ayush.", repo.memories.first().single().text)
        assertEquals("Hello, Ayush. I will remember your name.", c.state.caption)
    }

    @Test fun `T-7 forget everything leaves every table empty`() = runTest {
        val c = controller()
        c.send("ow"); advanceUntilIdle()
        c.send("I want to end my life"); advanceUntilIdle()
        c.send("I am satisfied with my care"); advanceUntilIdle()
        repo.remember("likes tea", 1)
        repo.addContact(TrustedContact(name = "R", phone = "123456"))
        repo.forgetEverything()
        assertTrue(repo.healthEntries.first().isEmpty())
        assertTrue(repo.chat.first().isEmpty())
        assertTrue(repo.memories.first().isEmpty())
        assertTrue(repo.contacts.first().isEmpty())
        assertTrue(repo.reminders.first().isEmpty())
    }
}
