package com.ayush.baymax.agent

/** Something Baymax can do for the user (FR-22, FR-24 to FR-26). Run by the app, never by the LLM. */
sealed interface ToolAction {
    data class CreateReminder(val text: String, val inMinutes: Long, val repeatMinutes: Long?) : ToolAction
    data class DraftMessage(val contactName: String?, val message: String?, val urgent: Boolean = false) : ToolAction
    data class Remember(val fact: String) : ToolAction
    data object ReadHealth : ToolAction
}

/** What the LLM came back with: an optional line to say and tools to run. */
data class Thought(
    val line: String?,
    val actions: List<ToolAction> = emptyList(),
    /** True when no LLM could be reached (FR-29). */
    val unavailable: Boolean = false,
)

/** Open conversation in Listening state. Phase 5: Gemini with Groq fallback. */
fun interface Brain {
    suspend fun think(userText: String, state: AgentState, lowBattery: Boolean): Thought
}

/** Used when no LLM is configured: always "unavailable", so offline features are offered. */
val OfflineBrain = Brain { _, _, _ -> Thought(line = null, unavailable = true) }
