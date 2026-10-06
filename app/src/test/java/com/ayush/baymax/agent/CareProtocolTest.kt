package com.ayush.baymax.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareProtocolTest {

    private val spoken = mutableListOf<String>()
    private val records = mutableListOf<CareRecord>()

    private fun CareProtocol.run(r: Reaction): Reaction {
        r.steps.forEach { step ->
            when (step) {
                is Step.Say -> spoken += step.line
                is Step.SaveRecord -> records += step.record
                else -> Unit
            }
        }
        return r
    }

    private fun CareProtocol.say(text: String) = run(onUserText(text))

    /** Drives a protocol to the satisfaction check like UC-1 steps 1–7. */
    private fun toSatisfaction(p: CareProtocol) {
        p.say("ow")
        assertEquals(AgentState.PainScale, p.state)
        p.run(p.onPainSelected(4))
        assertEquals(AgentState.Scanning, p.state)
        p.run(p.onScanFinished(null))
        assertEquals(AgentState.Interview, p.state)
        p.say("Arm")
        p.say("Just now")
        p.say("No")
        assertEquals(AgentState.Satisfaction, p.state)
    }

    @Test fun `T-3 full care loop ends only with the exit phrase`() {
        val p = CareProtocol()
        toSatisfaction(p)
        assertTrue(spoken.any { it.contains("cold pack") })

        // FR-13: anything else returns to care, then asks again.
        for (reply in listOf("Not yet", "no", "maybe", "thank you", "I am satisfied", "ow")) {
            val r = p.say(reply)
            assertEquals("after \"$reply\"", AgentState.Satisfaction, p.state)
            assertFalse(r.steps.contains(Step.Deactivate))
        }

        val end = p.say("I am satisfied with my care")
        assertEquals(AgentState.Idle, p.state)
        assertTrue(end.steps.last() is Step.Deactivate)
        assertEquals("session logged exactly once", 1, records.size)
        assertEquals(4, records.single().painLevel)
    }

    @Test fun `T-4 red flags open Emergency from every state`() {
        val reachers: List<Pair<String, (CareProtocol) -> Unit>> = listOf(
            "Idle" to { _ -> },
            "Listening" to { p -> p.say("hello") },
            "PainScale" to { p -> p.say("ow") },
            "Scanning" to { p -> p.say("ow"); p.run(p.onPainSelected(5)) },
            "Interview" to { p -> p.say("ow"); p.run(p.onPainSelected(5)); p.run(p.onScanFinished(null)) },
            "Satisfaction" to { p -> toSatisfaction(p) },
            "MoodCheck" to { p -> p.say("hello"); p.say("I feel low") },
        )
        for ((name, reach) in reachers) {
            val p = CareProtocol()
            reach(p)
            val r = p.say("my chest hurts and I can't breathe")
            assertEquals("from $name", AgentState.Emergency, p.state)
            assertTrue("from $name", r.preempt)
            assertTrue("from $name", r.steps.any { it is Step.Chest && it.content is ChestContent.Emergency })
        }
    }

    @Test fun `emergency recovers to the satisfaction check and is logged`() {
        val p = CareProtocol()
        p.say("I want to kill myself")
        assertEquals(AgentState.Emergency, p.state)
        assertTrue(spoken.any { it.contains("14416") })
        p.say("please help")
        assertEquals(AgentState.Emergency, p.state)
        p.run(p.onImOkay())
        assertEquals(AgentState.Satisfaction, p.state)
        p.say("I am satisfied with my care")
        assertEquals(AgentState.Idle, p.state)
        assertTrue(records.single().redFlag)
    }

    @Test fun `call chip in emergency opens the dialer with the configured number`() {
        val p = CareProtocol(CareConfig(emergencyNumber = "108"))
        p.say("heavy bleeding")
        val r = p.say("Call 108")
        assertTrue(r.steps.contains(Step.OpenDialer("108")))
    }

    @Test fun `case tap wakes without starting care`() {
        val p = CareProtocol()
        p.run(p.onCaseTap())
        assertEquals(AgentState.Listening, p.state)
        assertTrue(spoken.first().startsWith("Hello. I am Baymax"))
    }

    @Test fun `exit phrase while idle does nothing`() {
        val p = CareProtocol()
        val r = p.say("I am satisfied with my care")
        assertTrue(r.steps.isEmpty())
        assertEquals(AgentState.Idle, p.state)
    }

    @Test fun `pain can be typed as digits or words`() {
        for ((input, level) in listOf("7" to 7, "I think a seven" to 7, "ten" to 10, "it is a 10" to 10, "1" to 1)) {
            val p = CareProtocol()
            p.say("ow")
            p.say(input)
            assertEquals(input, AgentState.Scanning, p.state)
        }
        val p = CareProtocol()
        p.say("ow")
        p.say("a lot")
        assertEquals(AgentState.PainScale, p.state)
    }

    @Test fun `high pain recommends a doctor`() {
        val p = CareProtocol()
        p.say("ow")
        p.run(p.onPainSelected(9))
        p.run(p.onScanFinished(null))
        p.say("Head"); p.say("Earlier today"); p.say("No")
        assertTrue(spoken.any { it.contains("see a doctor") })
    }

    @Test fun `mood check is logged and returns to satisfaction`() {
        val p = CareProtocol(CareConfig(trustedContactName = "Priya"))
        p.say("hello")
        p.say("I feel really low today")
        assertEquals(AgentState.MoodCheck, p.state)
        p.say("2")
        // UC-3: with a trusted contact, Baymax offers to draft a message first.
        assertEquals(AgentState.MoodCheck, p.state)
        assertTrue(spoken.any { it.contains("draft a message to Priya") })
        val r = p.say("No, please do not")
        assertFalse(r.steps.any { it is Step.Act })
        assertEquals(AgentState.Satisfaction, p.state)
        p.say("I am satisfied with my care")
        assertEquals(2, records.single().mood)
    }

    @Test fun `open conversation goes to the LLM, local intents do not`() {
        val p = CareProtocol()
        p.run(p.onCaseTap())
        assertTrue(p.say("what should I eat for dinner").steps.single() is Step.Think)
        assertTrue(p.say("remind me to drink water every 2 hours").steps.single() is Step.Act)
        assertTrue(p.say("my name is Ayush").steps.first() is Step.Act)
        assertEquals(AgentState.Listening, p.state)
    }

    @Test fun `message a friend in an emergency drafts an urgent message`() {
        val p = CareProtocol(CareConfig(trustedContactName = "Priya"))
        p.say("I can't breathe")
        val act = p.run(p.onMessageFriend()).steps.single() as Step.Act
        assertEquals(ToolAction.DraftMessage("Priya", null, urgent = true), act.action)
    }

    @Test fun `T-1 persona lines are literal, without contractions, under 40 words`() {
        // Walk through every branch to collect lines.
        toSatisfaction(CareProtocol())
        CareProtocol().apply { say("ow"); run(onPainSelected(9)); run(onScanFinished(HealthReadings(80, 100, 300))); say("Stomach"); say("A few days ago"); say("Yes"); say("not yet"); say("not yet"); say("not yet") }
        CareProtocol().apply { say("hi"); say("what is the weather"); say("bye"); say("I feel sad"); say("x"); say("1") }
        CareProtocol().apply { say("I want to end my life"); say("help"); run(onMessageFriend()); run(onImOkay()) }
        CareProtocol(CareConfig(trustedContactName = "Priya")).apply { say("hello"); say("I am lonely"); say("4"); say("yes"); say("not yet") }
        CareProtocol().apply { say("hi"); say("my name is Ayush") }
        CareProtocol().apply { say("ow"); say("a lot"); run(onPainSelected(3)); say("hello?") }

        val contraction = Regex("""\b[A-Za-z]+'(t|s|re|ll|ve|d|m)\b""")
        assertTrue(spoken.size > 20)
        for (line in spoken) {
            assertFalse("contraction in: $line", contraction.containsMatchIn(line))
            assertTrue("too long: $line", line.split(' ').size < 40)
            assertNotEquals("empty line", "", line.trim())
        }
    }
}
