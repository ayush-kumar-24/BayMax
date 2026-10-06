package com.ayush.baymax.agent

import com.ayush.baymax.agent.SafetyRules.RedFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyRulesTest {

    @Test fun `red flags from the SRS are detected`() {
        assertEquals(RedFlag.ChestPain, SafetyRules.redFlag("My chest hurts and I can't breathe"))
        assertEquals(RedFlag.Breathing, SafetyRules.redFlag("I CAN’T BREATHE"))
        assertEquals(RedFlag.Bleeding, SafetyRules.redFlag("there is heavy bleeding from my leg"))
        assertEquals(RedFlag.SelfHarm, SafetyRules.redFlag("I want to kill myself"))
        assertEquals(RedFlag.SelfHarm, SafetyRules.redFlag("thinking about self-harm"))
        assertEquals(RedFlag.ChestPain, SafetyRules.redFlag("I have chest pain"))
    }

    @Test fun `ordinary complaints are not red flags`() {
        listOf("ow", "I hurt my arm", "my head hurts", "I feel low today", "I am bleeding a little from a paper cut", "my chest is fine")
            .forEach { assertNull(it, SafetyRules.redFlag(it)) }
    }

    @Test fun `exit phrase is case-insensitive and exact`() {
        assertTrue(SafetyRules.isExitPhrase("I am satisfied with my care"))
        assertTrue(SafetyRules.isExitPhrase("  i AM satisfied with MY care. "))
        assertTrue(SafetyRules.isExitPhrase("I’m satisfied with my care"))
        assertFalse(SafetyRules.isExitPhrase("I am satisfied"))
        assertFalse(SafetyRules.isExitPhrase("I am not satisfied with my care"))
        assertFalse(SafetyRules.isExitPhrase("yes"))
    }

    @Test fun `distress words wake Baymax`() {
        val words = SafetyRules.DEFAULT_DISTRESS_WORDS
        assertTrue(SafetyRules.isDistress("Ow!", words))
        assertTrue(SafetyRules.isDistress("that really hurts", words))
        assertTrue(SafetyRules.isDistress("I am in pain", words))
        assertFalse(SafetyRules.isDistress("hello there", words))
        assertFalse(SafetyRules.isDistress("I own a bowl", words))
    }
}
