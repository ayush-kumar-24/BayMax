package com.ayush.baymax.llm

import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.data.ChatMessage
import com.ayush.baymax.data.MemoryFact
import com.ayush.baymax.data.Mode

/**
 * Builds every LLM request. NFR-9: only the persona, the current state, the last 10 turns
 * and at most 5 memory facts are ever sent. Nothing from the health log is included.
 */
object PromptBuilder {
    const val MAX_TURNS = 10
    const val MAX_FACTS = 5

    private val PERSONA = """
        You are Baymax, a personal healthcare companion robot, speaking to your one patient.
        Speak like Baymax from the film: calm, kind, literal and polite. Short, simple sentences.
        Never use contractions: say "I am", "do not", "it is". No slang, no emoji, no markdown.
        Keep every reply under 40 words unless the user asks for more.
        You are not a doctor. Never diagnose. Never name a medicine dose. Give only general
        self-care and comfort, and suggest seeing a doctor when something sounds serious.
        If the user describes an emergency, tell them to call the emergency number now.
        Use a tool when the user asks for a reminder, their health data, to message a friend,
        or tells you a lasting fact about themselves worth remembering.
    """.trimIndent()

    fun build(
        history: List<ChatMessage>,
        facts: List<MemoryFact>,
        state: AgentState,
        modes: Set<Mode>,
        lowBattery: Boolean,
    ): LlmRequest {
        val system = buildString {
            appendLine(PERSONA)
            appendLine()
            appendLine("Current state: ${state.name}.")
            modes.forEach { appendLine(it.promptHint) }
            if (lowBattery) appendLine("Your battery is low: you may sound a little sleepy, but stay helpful.")
            val f = facts.take(MAX_FACTS)
            if (f.isNotEmpty()) {
                appendLine("What you remember about the user:")
                f.forEach { appendLine("- ${it.text}") }
            }
        }.trim()
        val turns = history
            .filter { it.role != ChatMessage.Role.Tool && it.text.isNotBlank() }
            .takeLast(MAX_TURNS)
            .map { ChatTurn(if (it.role == ChatMessage.Role.User) ChatTurn.Role.User else ChatTurn.Role.Assistant, it.text) }
            // Gemini requires the conversation to start with the user.
            .dropWhile { it.role == ChatTurn.Role.Assistant }
        return LlmRequest(system, turns, BaymaxTools.specs)
    }

    /**
     * Keeps replies in persona even if the model drifts (NFR-12): strips markdown and emoji,
     * and trims to whole sentences under 40 words.
     */
    fun tidy(text: String): String {
        val plain = text
            .replace(Regex("""[*_#`>]+"""), "")
            .replace(Regex("""[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val words = plain.split(' ')
        if (words.size < 40) return plain
        val sentences = Regex("""[^.!?]+[.!?]""").findAll(plain).map { it.value.trim() }.toList()
        val out = StringBuilder()
        for (s in sentences) {
            val next = if (out.isEmpty()) s else "$out $s"
            if (next.split(' ').size >= 40) break
            out.clear().append(next)
        }
        return out.toString().ifEmpty { words.take(39).joinToString(" ") + "." }
    }
}

/** The tools Baymax may ask the app to run (FR-22, FR-24 to FR-26). */
object BaymaxTools {
    const val CREATE_REMINDER = "create_reminder"
    const val READ_HEALTH = "read_health_data"
    const val DRAFT_MESSAGE = "draft_message"
    const val REMEMBER = "remember_fact"

    val specs = listOf(
        ToolSpec(
            CREATE_REMINDER,
            "Create a one-time or repeating reminder notification for the user.",
            listOf(
                ToolParam("text", ToolParam.Type.String, "What to remind the user about, e.g. 'drink water'."),
                ToolParam("minutes_from_now", ToolParam.Type.Integer, "Minutes until the first reminder."),
                ToolParam("repeat_every_minutes", ToolParam.Type.Integer, "Repeat interval in minutes; omit for one-time.", required = false),
            ),
        ),
        ToolSpec(READ_HEALTH, "Read today's steps, last night's sleep and the latest heart rate from Health Connect.", emptyList()),
        ToolSpec(
            DRAFT_MESSAGE,
            "Draft a message to one of the user's trusted contacts. The user must confirm before it is sent.",
            listOf(
                ToolParam("contact_name", ToolParam.Type.String, "Name of the trusted contact, if the user named one.", required = false),
                ToolParam("message", ToolParam.Type.String, "The message text, written as the user, warm and short."),
            ),
        ),
        ToolSpec(
            REMEMBER,
            "Remember a lasting fact about the user, such as their name, a condition, or a preference.",
            listOf(ToolParam("fact", ToolParam.Type.String, "The fact, as a short sentence about the user.")),
        ),
    )
}
