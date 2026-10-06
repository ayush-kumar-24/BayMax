package com.ayush.baymax.llm

import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.ToolAction
import com.ayush.baymax.data.ChatMessage
import com.ayush.baymax.data.MemoryFact
import com.ayush.baymax.data.Mode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTest {

    private class FakeClient(override val name: String, private val fail: List<Exception?>, override val isConfigured: Boolean = true) : LlmClient {
        var calls = 0
        override suspend fun generate(request: LlmRequest): LlmReply {
            val e = fail.getOrNull(calls++)
            if (e != null) throw e
            return LlmReply("reply from $name", emptyList())
        }
    }

    private val req = LlmRequest("s", listOf(ChatTurn(ChatTurn.Role.User, "hi")), emptyList())
    private fun rateLimit() = LlmException("429", retryable = true)

    @Test fun `T-5 retries once then falls back to Groq`() = runTest {
        val gemini = FakeClient("Gemini", listOf(rateLimit(), rateLimit()))
        val groq = FakeClient("Groq", emptyList())
        val router = LlmRouter(listOf(gemini, groq))
        assertEquals("reply from Groq", router.generate(req)?.text)
        assertEquals(2, gemini.calls)
        assertEquals(1, groq.calls)
        assertEquals("Groq", router.lastProvider)
    }

    @Test fun `a single rate limit is absorbed by the retry`() = runTest {
        val gemini = FakeClient("Gemini", listOf(rateLimit()))
        val groq = FakeClient("Groq", emptyList())
        assertEquals("reply from Gemini", LlmRouter(listOf(gemini, groq)).generate(req)?.text)
        assertEquals(0, groq.calls)
    }

    @Test fun `invalid key is not retried`() = runTest {
        val gemini = FakeClient("Gemini", listOf(LlmException("401", retryable = false)))
        val groq = FakeClient("Groq", emptyList())
        LlmRouter(listOf(gemini, groq)).generate(req)
        assertEquals(1, gemini.calls)
    }

    @Test fun `T-5 both providers failing returns null and never throws`() = runTest {
        val router = LlmRouter(listOf(FakeClient("Gemini", listOf(rateLimit(), rateLimit())), FakeClient("Groq", listOf(RuntimeException("x"), RuntimeException("y")))))
        assertNull(router.generate(req))
    }

    @Test fun `unconfigured providers are skipped`() = runTest {
        val gemini = FakeClient("Gemini", emptyList(), isConfigured = false)
        val groq = FakeClient("Groq", emptyList())
        assertEquals("reply from Groq", LlmRouter(listOf(gemini, groq)).generate(req)?.text)
        assertEquals(0, gemini.calls)
    }

    @Test fun `T-10 request carries at most 10 turns and 5 memory facts`() {
        val history = (1..30).map { ChatMessage(role = if (it % 2 == 0) ChatMessage.Role.Agent else ChatMessage.Role.User, text = "m$it", timestamp = it.toLong()) }
        val facts = (1..8).map { MemoryFact(it.toLong(), "fact $it", 0, 0) }
        val r = PromptBuilder.build(history, facts, AgentState.Listening, setOf(Mode.Sleep), lowBattery = false)
        assertTrue(r.turns.size <= 10)
        assertEquals(ChatTurn.Role.User, r.turns.first().role)
        assertEquals("m30", r.turns.last().text)
        assertEquals(5, Regex("""- fact \d""").findAll(r.system).count())
        assertTrue(r.system.contains("Current state: Listening"))
        assertTrue(r.system.contains("Sleep mode"))
        assertFalse(r.system.contains("fact 6"))
    }

    @Test fun `tidy strips markdown and trims long replies to whole sentences`() {
        assertEquals("Hello. I am here.", PromptBuilder.tidy("**Hello.** I am _here_."))
        val long = (1..12).joinToString(" ") { "This is sentence number $it." }
        val out = PromptBuilder.tidy(long)
        assertTrue(out.split(' ').size < 40)
        assertTrue(out.endsWith("."))
    }

    @Test fun `Groq tool calls are parsed`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"1","type":"function","function":{"name":"create_reminder","arguments":"{\"text\":\"drink water\",\"minutes_from_now\":120,\"repeat_every_minutes\":120}"}}]}}]}"""
        val reply = GroqClient("k").parse(body)
        assertEquals("", reply.text)
        val action = LlmBrain.toAction(reply.toolCalls.single())
        assertEquals(ToolAction.CreateReminder("drink water", 120, 120), action)
    }

    @Test fun `Gemini text and function calls are parsed`() {
        val body = """{"candidates":[{"content":{"role":"model","parts":[{"text":"I will remember that."},{"functionCall":{"name":"remember_fact","args":{"fact":"The user is allergic to peanuts."}}}]}}]}"""
        val reply = GeminiRestClient("k").parse(body)
        assertEquals("I will remember that.", reply.text)
        assertEquals(ToolAction.Remember("The user is allergic to peanuts."), LlmBrain.toAction(reply.toolCalls.single()))
    }

    @Test fun `request bodies include persona and tools`() {
        val r = PromptBuilder.build(listOf(ChatMessage(role = ChatMessage.Role.User, text = "hi", timestamp = 1)), emptyList(), AgentState.Listening, emptySet(), false)
        val groq = GroqClient("k").body(r)
        assertTrue(groq.contains("\"openai/gpt-oss-120b\""))
        assertTrue(groq.contains("create_reminder"))
        val gemini = GeminiRestClient("k").body(r)
        assertTrue(gemini.contains("functionDeclarations"))
        assertTrue(gemini.contains("systemInstruction"))
    }

    @Test fun `HTTP 429 from Groq is retryable, 401 is not`() = runTest {
        val limited = GroqClient("k", transport = { _, _, _ -> 429 to "slow down" })
        val e1 = runCatching { limited.generate(req) }.exceptionOrNull() as LlmException
        assertTrue(e1.retryable)
        val bad = GroqClient("k", transport = { _, _, _ -> 401 to "bad key" })
        val e2 = runCatching { bad.generate(req) }.exceptionOrNull() as LlmException
        assertFalse(e2.retryable)
    }
}
