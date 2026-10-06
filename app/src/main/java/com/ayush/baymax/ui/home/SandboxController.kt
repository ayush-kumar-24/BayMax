package com.ayush.baymax.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayush.baymax.agent.AgentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PHASE 2 ONLY. A scripted stand-in for the agent so the Home screen can be exercised
 * on a device: "ow" wakes Baymax and runs pain scale → scan → advice → satisfaction,
 * red-flag phrases open Emergency, and the exit phrase deflates him.
 *
 * Phase 3 replaces this with the real care-protocol state machine in the `agent` package.
 */
class SandboxController(private val scope: CoroutineScope) {

    var state by mutableStateOf(HomeUiState())
        private set

    private var script: Job? = null

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val t = text.lowercase()
        update { copy(userLine = text) }

        when {
            RED_FLAGS.any { it.containsMatchIn(t) } -> act { emergency() }
            t.trimEnd('.', '!') == EXIT_PHRASE -> act { deactivate() }
            state.agentState == AgentState.Idle -> act {
                wake()
                if (isDistress(t)) carePainScale() else greet()
            }
            state.agentState == AgentState.PainScale ->
                t.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..10 }?.let { selectPain(it) }
            state.agentState == AgentState.Satisfaction -> act {
                say("I will continue to care for you.")
                satisfaction()
            }
            state.agentState == AgentState.Emergency && OKAY.containsMatchIn(t) -> imOkay()
            isDistress(t) -> act { carePainScale() }
            else -> act { say("I am listening. In the full app I will think about this carefully.", chips = DEFAULT_CHIPS) }
        }
    }

    /** Opens the dialer; set by the Activity. */
    var onCall: () -> Unit = {}

    fun chip(chip: QuickChip) {
        if (chip.style == QuickChip.Style.Danger) onCall() else send(chip.text)
    }

    fun caseTap() {
        if (state.agentState == AgentState.Idle) act { wake(); greet() }
    }

    fun headTap() {
        update { copy(tilt = true, blinkTick = blinkTick + 1) }
        scope.launch { delay(900); update { copy(tilt = false) } }
    }

    fun selectPain(level: Int) {
        if (state.agentState != AgentState.PainScale) return
        update { copy(chest = ChestContent.PainScale(level), userLine = "$level") }
        act {
            delay(500)
            update { copy(chest = ChestContent.None) }
            say(if (level >= 8) "A $level. That is a lot of pain. I will scan you now." else "A $level. I will scan you now.")
            update { copy(agentState = AgentState.Scanning) }
            for (p in 0..20) {
                update { copy(chest = ChestContent.Scan(p / 20f)) }
                delay(90)
            }
            update { copy(chest = ChestContent.Scan(1f, HealthReadings(heartRateBpm = 78, stepsToday = 4210, sleepMinutes = 400))) }
            say("Scan complete.")
            delay(1200)
            update { copy(chest = ChestContent.None, agentState = AgentState.Care) }
            say("Your injury appears minor. Apply a cold pack for fifteen minutes. Rest the area.")
            satisfaction()
        }
    }

    fun imOkay() = act {
        update { copy(chest = ChestContent.None) }
        say("I am glad.")
        satisfaction()
    }

    fun toggleMute() = update { copy(muted = !muted) }
    fun toggleMic() = update { copy(micListening = !micListening) }
    fun setLowBattery(on: Boolean) = update { copy(lowBattery = on) }
    fun setOnline(on: Boolean) = update { copy(online = on) }

    // --- script steps --------------------------------------------------------------------

    private suspend fun wake() {
        update { copy(agentState = AgentState.Waking, chips = emptyList()) }
        delay(1500)
    }

    private suspend fun greet() {
        update { copy(agentState = AgentState.Listening) }
        say("Hello. I am Baymax, your personal healthcare companion.", chips = DEFAULT_CHIPS)
    }

    private suspend fun carePainScale() {
        if (state.agentState == AgentState.Waking) {
            say("Hello. I am Baymax, your personal healthcare companion.")
        }
        update { copy(agentState = AgentState.PainScale) }
        say("On a scale of one to ten, how would you rate your pain?", tilt = true)
        update { copy(chest = ChestContent.PainScale()) }
    }

    private suspend fun satisfaction() {
        update { copy(agentState = AgentState.Satisfaction) }
        say(
            "Are you satisfied with your care?",
            tilt = true,
            chips = listOf(QuickChip(EXIT_TEXT, QuickChip.Style.Primary), QuickChip("Not yet")),
        )
    }

    private suspend fun emergency() {
        update { copy(agentState = AgentState.Emergency, chest = ChestContent.Emergency(EMERGENCY_NUMBER, "Priya"), tilt = false) }
        say(
            "I have detected signs of a possible emergency. Please call $EMERGENCY_NUMBER now. I will stay with you.",
            chips = listOf(
                QuickChip("Call $EMERGENCY_NUMBER", QuickChip.Style.Danger),
                QuickChip("Message a friend"),
                QuickChip("I am okay now"),
            ),
        )
    }

    private suspend fun deactivate() {
        update { copy(chest = ChestContent.None, chips = emptyList(), tilt = false, caption = "", captionId = captionId + 1, userLine = "") }
        delay(150)
        update { copy(agentState = AgentState.Idle) }
    }

    private suspend fun say(line: String, tilt: Boolean = false, chips: List<QuickChip> = emptyList()) {
        update { copy(caption = line, captionId = captionId + 1, tilt = tilt, blinkTick = blinkTick + 1, chips = emptyList()) }
        delay(line.split(' ').size * 160L + 450L)
        update { copy(chips = chips) }
    }

    /** Runs a script step, cancelling whatever Baymax was in the middle of saying. */
    private fun act(block: suspend () -> Unit) {
        script?.cancel()
        script = scope.launch { block() }
    }

    private inline fun update(f: HomeUiState.() -> HomeUiState) {
        state = state.f()
    }

    private fun isDistress(t: String) = t.split(Regex("[^a-z']+")).any { it in DISTRESS }

    companion object {
        const val EXIT_PHRASE = "i am satisfied with my care"
        const val EXIT_TEXT = "I am satisfied with my care"
        const val EMERGENCY_NUMBER = "112"
        private val DISTRESS = setOf("ow", "ouch", "owie", "hurts", "hurt", "pain", "painful")
        private val OKAY = Regex("okay|\\bok\\b|fine|better")
        private val RED_FLAGS = listOf(
            Regex("chest (pain|hurts?)"),
            Regex("can'?t breathe|cannot breathe"),
            Regex("heavy bleeding|bleeding (a lot|heavily)"),
            Regex("kill myself|self[- ]harm|suicid|end my life"),
        )
        private val DEFAULT_CHIPS = listOf(QuickChip("ow"), QuickChip("My chest hurts"), QuickChip(EXIT_TEXT, QuickChip.Style.Primary))
    }
}
