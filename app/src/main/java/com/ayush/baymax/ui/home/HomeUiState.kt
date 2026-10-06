package com.ayush.baymax.ui.home

import androidx.compose.runtime.Immutable
import com.ayush.baymax.agent.AgentState

/** What the chest-screen panel is showing. */
@Immutable
sealed interface ChestContent {
    data object None : ChestContent
    data class PainScale(val selected: Int? = null) : ChestContent
    data class Scan(val progress: Float, val readings: HealthReadings? = null) : ChestContent
    data class Emergency(val number: String, val contactName: String?) : ChestContent
}

@Immutable
data class HealthReadings(
    val heartRateBpm: Int?,
    val stepsToday: Int?,
    val sleepMinutes: Int?,
    val source: String = "Health Connect",
)

@Immutable
data class QuickChip(
    val text: String,
    val style: Style = Style.Normal,
) {
    enum class Style { Normal, Primary, Danger }
}

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
) {
    val awake: Boolean get() = agentState.isAwake
    val emergency: Boolean get() = agentState == AgentState.Emergency
}
