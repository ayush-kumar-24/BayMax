package com.ayush.baymax.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.CareProtocol
import com.ayush.baymax.agent.CareRecord
import com.ayush.baymax.agent.ChestContent
import com.ayush.baymax.agent.HealthReadings
import com.ayush.baymax.agent.QuickChip
import com.ayush.baymax.agent.Reaction
import com.ayush.baymax.agent.Step
import com.ayush.baymax.voice.SilentSpeaker
import com.ayush.baymax.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One line of the conversation (FR-5). Shown in the conversation sheet from Phase 4. */
data class ChatLine(val fromUser: Boolean, val text: String)

/**
 * Runs the [CareProtocol] against the Home screen: turns each [Step] into UI state,
 * speech and timing. Pure Kotlin so it runs in unit tests and the desktop preview harness;
 * Android wiring lives in [HomeViewModel].
 *
 * Interruptions: when the user says something new, the batch Baymax is in the middle of is
 * fast-forwarded (speech stops, waits are skipped, but every state change is still applied),
 * then the new reply runs. So the screen always ends in the state the protocol is in.
 */
class HomeController(
    private val scope: CoroutineScope,
    val protocol: CareProtocol = CareProtocol(),
    private val speaker: Speaker = SilentSpeaker,
    private val healthReadings: suspend () -> HealthReadings? = { null },
    private val onSaveRecord: (CareRecord) -> Unit = {},
    private val onDial: (String) -> Unit = {},
) {
    var state by mutableStateOf(HomeUiState())
        private set

    val transcript = mutableStateListOf<ChatLine>()

    private class Batch(val steps: List<Step>) {
        @Volatile var skip = false
        var job: Job? = null
    }

    private var current: Batch? = null

    // ---------------------------------------------------------------------------------
    // User actions
    // ---------------------------------------------------------------------------------

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        userSaid(text)
        submit(protocol.onUserText(text))
    }

    fun chip(chip: QuickChip) {
        if (chip.style == QuickChip.Style.Danger) call() else send(chip.text)
    }

    fun caseTap() = submit(protocol.onCaseTap())

    fun selectPain(level: Int) {
        if (state.agentState != AgentState.PainScale) return
        userSaid("$level")
        submit(protocol.onPainSelected(level))
    }

    fun imOkay() {
        userSaid("I am okay now")
        submit(protocol.onImOkay())
    }

    fun messageFriend() = submit(protocol.onMessageFriend())

    /** Emergency call: opens the dialer with the configured number (FR-15). */
    fun call() = onDial(protocol.config.emergencyNumber)

    fun headTap() {
        update { copy(tilt = true, blinkTick = blinkTick + 1) }
        scope.launch {
            delay(900)
            update { copy(tilt = false) }
        }
    }

    fun toggleMute() {
        update { copy(muted = !muted) }
        if (state.muted) speaker.stop()
    }

    fun setListening(listening: Boolean) = update { copy(micListening = listening) }

    /** Live speech-to-text while the user is talking. */
    fun setPartialSpeech(text: String) = update { copy(userLine = text) }

    fun setLowBattery(low: Boolean) {
        if (low != state.lowBattery) update { copy(lowBattery = low) }
    }

    fun setOnline(online: Boolean) {
        if (online != state.online) update { copy(online = online) }
    }

    // ---------------------------------------------------------------------------------
    // Step runner
    // ---------------------------------------------------------------------------------

    private fun userSaid(text: String) {
        update { copy(userLine = text) }
        transcript += ChatLine(fromUser = true, text = text)
    }

    private fun submit(reaction: Reaction) {
        if (reaction.steps.isEmpty()) return
        val prev = current
        if (reaction.preempt && prev != null) {
            prev.skip = true
            speaker.stop()
        }
        val batch = Batch(reaction.steps)
        current = batch
        batch.job = scope.launch {
            prev?.job?.join()
            run(batch)
        }
    }

    private suspend fun run(batch: Batch) {
        val queue = ArrayDeque(batch.steps)
        while (queue.isNotEmpty()) {
            when (val step = queue.removeFirst()) {
                is Step.Wake -> wait(batch, if (step.fast) 300 else 1500)
                is Step.SetState -> update { copy(agentState = step.state) }
                is Step.Say -> say(batch, step)
                is Step.Chest -> update { copy(chest = step.content) }
                is Step.Pause -> wait(batch, step.ms)
                Step.RunScan -> {
                    for (p in 0..SCAN_TICKS) {
                        update { copy(chest = ChestContent.Scan(p / SCAN_TICKS.toFloat())) }
                        wait(batch, SCAN_MS / SCAN_TICKS)
                    }
                    val readings = runCatching { healthReadings() }.getOrNull()
                    // The scan ends the protocol's Scanning state; run its follow-up in this batch.
                    queue.addAll(0, protocol.onScanFinished(readings).steps)
                }
                is Step.SaveRecord -> runCatching { onSaveRecord(step.record) }
                is Step.OpenDialer -> onDial(step.number)
                Step.Deactivate -> update {
                    copy(
                        agentState = AgentState.Idle,
                        caption = "",
                        captionId = captionId + 1,
                        userLine = "",
                        chips = emptyList(),
                        chest = ChestContent.None,
                        tilt = false,
                    )
                }
            }
        }
    }

    private suspend fun say(batch: Batch, step: Step.Say) {
        // Never slur safety-critical lines.
        val styled = state.lowBattery && state.agentState != AgentState.Emergency
        val line = if (styled) lowBatteryStyle(step.line) else step.line
        transcript += ChatLine(fromUser = false, text = line)
        update {
            copy(
                caption = line,
                captionId = captionId + 1,
                tilt = step.tilt,
                blinkTick = blinkTick + 1,
                chips = emptyList(),
            )
        }
        if (!batch.skip) {
            // Wait for both the spoken line and the word-by-word caption to finish.
            val revealMs = line.split(' ').size * WORD_MS + 450L
            val speech = if (state.muted) null else scope.launch {
                runCatching { speaker.speak(line, state.lowBattery) }
            }
            var elapsed = 0L
            while (!batch.skip && (speech?.isActive == true || elapsed < revealMs)) {
                delay(TICK_MS)
                elapsed += TICK_MS
            }
            speech?.cancel()
        }
        update { copy(chips = step.chips) }
    }

    /** Counts ticks instead of reading the clock so tests can run on virtual time. */
    private suspend fun wait(batch: Batch, ms: Long) {
        var elapsed = 0L
        while (!batch.skip && elapsed < ms) {
            delay(TICK_MS)
            elapsed += TICK_MS
        }
    }

    private inline fun update(f: HomeUiState.() -> HomeUiState) {
        state = state.f()
    }

    private companion object {
        const val WORD_MS = 160L
        const val TICK_MS = 30L
        const val SCAN_MS = 2000L
        const val SCAN_TICKS = 20
    }
}

/**
 * FR-19: cosmetic "low battery" voice from the film: stretched vowels, trailing pauses,
 * lower case. Only the caption changes; the meaning is the same.
 */
internal fun lowBatteryStyle(line: String): String {
    val lower = line.lowercase()
    val vowel = lower.indexOfFirst { it in "aeiou" }
    val stretched = if (vowel < 0) lower else lower.substring(0, vowel) + lower[vowel].toString().repeat(3) + lower.substring(vowel + 1)
    return stretched.replace(". ", "… ").replace(Regex("""\.$"""), "…")
}
