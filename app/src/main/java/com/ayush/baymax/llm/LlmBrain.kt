package com.ayush.baymax.llm

import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.Brain
import com.ayush.baymax.agent.Thought
import com.ayush.baymax.agent.ToolAction
import com.ayush.baymax.data.BaymaxRepository

/** Open conversation through the [LlmRouter], with memory and tools (FR-22, FR-24 to FR-29). */
class LlmBrain(
    private val router: LlmRouter,
    private val repository: BaymaxRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : Brain {

    override suspend fun think(userText: String, state: AgentState, lowBattery: Boolean): Thought {
        val history = repository.recentChat(PromptBuilder.MAX_TURNS)
        val facts = repository.factsForPrompt(PromptBuilder.MAX_FACTS, now())
        val request = PromptBuilder.build(history, facts, state, repository.settings.value.enabledChips, lowBattery)
        val reply = router.generate(request) ?: return Thought(line = null, unavailable = true)
        val actions = reply.toolCalls.mapNotNull(::toAction)
        val line = reply.text.takeIf { it.isNotBlank() }?.let(PromptBuilder::tidy)
        return Thought(line = line, actions = actions)
    }

    companion object {
        fun toAction(call: ToolCall): ToolAction? = when (call.name) {
            BaymaxTools.CREATE_REMINDER -> {
                val text = call.args["text"]?.takeIf { it.isNotBlank() }
                val first = call.args["minutes_from_now"]?.toDoubleOrNull()?.toLong()?.coerceAtLeast(1)
                val repeat = call.args["repeat_every_minutes"]?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }
                if (text != null) ToolAction.CreateReminder(text, first ?: repeat ?: 60, repeat) else null
            }
            BaymaxTools.READ_HEALTH -> ToolAction.ReadHealth
            BaymaxTools.DRAFT_MESSAGE -> ToolAction.DraftMessage(call.args["contact_name"]?.takeIf { it.isNotBlank() }, call.args["message"])
            BaymaxTools.REMEMBER -> call.args["fact"]?.takeIf { it.isNotBlank() }?.let { ToolAction.Remember(it) }
            else -> null
        }
    }
}
