package com.ayush.baymax.ui.home

import androidx.compose.runtime.Immutable
import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.ChestContent
import com.ayush.baymax.agent.QuickChip
import com.ayush.baymax.data.TrustedContact

@Immutable
data class HomeUiState(
    val agentState: AgentState = AgentState.Idle,
    val online: Boolean = true,
    val lowBattery: Boolean = false,
    val muted: Boolean = false,
    val micListening: Boolean = false,
    /** Baymax's current line, revealed word by word. */
    val caption: String = "",
    /** Bumped for every new line so the same text can be "said" twice. */
    val captionId: Int = 0,
    val userLine: String = "",
    val chips: List<QuickChip> = emptyList(),
    val chest: ChestContent = ChestContent.None,
    /** Head tilt for questions. */
    val tilt: Boolean = false,
    /** Bumped to request one blink (FR-17: blink once per reply). */
    val blinkTick: Int = 0,
    /** Waiting for the LLM. */
    val thinking: Boolean = false,
    /** A drafted message waiting for the user's confirmation (FR-26). */
    val draft: MessageDraft? = null,
) {
    val awake: Boolean get() = agentState.isAwake
    val emergency: Boolean get() = agentState == AgentState.Emergency
}

@Immutable
data class MessageDraft(val contact: TrustedContact, val text: String)
