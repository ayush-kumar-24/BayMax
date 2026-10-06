package com.ayush.baymax.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.agent.Brain
import com.ayush.baymax.agent.CareProtocol
import com.ayush.baymax.agent.ChestContent
import com.ayush.baymax.agent.HealthReadings
import com.ayush.baymax.agent.OfflineBrain
import com.ayush.baymax.agent.QuickChip
import com.ayush.baymax.agent.Reaction
import com.ayush.baymax.agent.ReminderParser
import com.ayush.baymax.agent.SafetyRules
import com.ayush.baymax.agent.Step
import com.ayush.baymax.agent.ToolAction
import com.ayush.baymax.data.AppSettings
import com.ayush.baymax.data.BaymaxRepository
import com.ayush.baymax.data.ChatMessage
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.data.InMemoryRepository
import com.ayush.baymax.data.Reminder
import com.ayush.baymax.data.ReminderScheduler
import com.ayush.baymax.data.TrustedContact
import com.ayush.baymax.voice.SilentSpeaker
import com.ayush.baymax.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the [CareProtocol] against the Home screen: turns each [Step] into UI state,
 * speech, timing, LLM calls and tools. Pure Kotlin so it runs in unit tests and the desktop
 * preview harness; Android wiring lives in [HomeViewModel].
 *
 * Interruptions: when the user says something new, the batch Baymax is in the middle of is
 * fast-forwarded (speech stops, waits and LLM calls are abandoned, but every state change is
 * still applied), then the new reply runs. So the screen always ends in the protocol's state.
 */
class HomeController(
    private val scope: CoroutineScope,
    val protocol: CareProtocol = CareProtocol(),
    private val speaker: Speaker = SilentSpeaker,
    private val brain: Brain = OfflineBrain,
    val repository: BaymaxRepository = InMemoryRepository(),
    private val healthReadings: suspend () -> HealthReadings? = { null },
    private val reminders: ReminderScheduler = ReminderScheduler.None,
    private val onDial: (String) -> Unit = {},
    private val onSendMessage: (TrustedContact, String, ContactApp) -> Unit = { _, _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state by mutableStateOf(HomeUiState())
        private set

    private var contacts: List<TrustedContact> = emptyList()

    private class Batch(val steps: List<Step>) {
        @Volatile var skip = false
        var job: Job? = null
    }

    private var current: Batch? = null

    init {
        scope.launch { repository.pruneChatOlderThan(now() - CHAT_RETENTION_MS) }
        scope.launch { repository.settings.collect(::applySettings) }
        scope.launch {
            repository.contacts.collect { list ->
                contacts = list
                protocol.config = protocol.config.copy(trustedContactName = list.firstOrNull()?.name)
            }
        }
    }

    private fun applySettings(s: AppSettings) {
        protocol.config = protocol.config.copy(distressWords = s.distressWords, emergencyNumber = s.emergencyNumber)
        if (state.muted == s.voiceOn) update { copy(muted = !s.voiceOn) }
    }

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

    /** The user confirmed the drafted message (FR-26). */
    fun sendDraft(text: String, app: ContactApp) {
        val draft = state.draft ?: return
        update { copy(draft = null) }
        onSendMessage(draft.contact, text, app)
        log(ChatMessage.Role.Tool, "Message to ${draft.contact.name} opened in ${app.label}")
    }

    fun cancelDraft() = update { copy(draft = null) }

    fun headTap() {
        update { copy(tilt = true, blinkTick = blinkTick + 1) }
        scope.launch {
            delay(900)
            update { copy(tilt = false) }
        }
    }

    fun toggleMute() {
        val muted = !state.muted
        update { copy(muted = muted) }
        if (muted) speaker.stop()
        scope.launch { repository.updateSettings { it.copy(voiceOn = !muted) } }
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

    /** Home-screen widget and notification taps open straight into a care session (FR-32). */
    fun startCareFromShortcut() {
        if (state.agentState == AgentState.Idle) send("ow")
    }

    // ---------------------------------------------------------------------------------
    // Step runner
    // ---------------------------------------------------------------------------------

    private fun userSaid(text: String) {
        update { copy(userLine = text) }
        log(ChatMessage.Role.User, text)
    }

    private fun log(role: ChatMessage.Role, text: String) {
        val msg = ChatMessage(role = role, text = text, timestamp = now())
        scope.launch { runCatching { repository.addChat(msg) } }
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
                is Step.SaveRecord -> runCatching { repository.saveCareRecord(step.record) }
                is Step.OpenDialer -> onDial(step.number)
                is Step.Think -> think(batch, step)
                is Step.Act -> act(step.action, silentSuccess = false)?.let { say(batch, Step.Say(it, chips = DEFAULT_CHIPS)) }
                Step.Deactivate -> update {
                    copy(
                        agentState = AgentState.Idle,
                        caption = "",
                        captionId = captionId + 1,
                        userLine = "",
                        chips = emptyList(),
                        chest = ChestContent.None,
                        tilt = false,
                        thinking = false,
                    )
                }
            }
        }
    }

    /** Open conversation through the LLM, abandoned if the user speaks again (FR-28, FR-29). */
    private suspend fun think(batch: Batch, step: Step.Think) {
        update { copy(thinking = true, tilt = true, blinkTick = blinkTick + 1) }
        val pending = scope.async { runCatching { brain.think(step.userText, state.agentState, state.lowBattery) }.getOrNull() }
        while (pending.isActive && !batch.skip) delay(TICK_MS)
        update { copy(thinking = false, tilt = false) }
        if (batch.skip) {
            pending.cancel()
            return
        }
        val thought = pending.await()
        if (thought == null || thought.unavailable) {
            say(batch, Step.Say(OFFLINE_LINE, chips = DEFAULT_CHIPS))
            return
        }
        val line = thought.line
        if (line != null) say(batch, Step.Say(line, chips = if (thought.actions.isEmpty()) DEFAULT_CHIPS else emptyList()))
        thought.actions.forEach { action ->
            act(action, silentSuccess = line != null)?.let { say(batch, Step.Say(it, chips = DEFAULT_CHIPS)) }
        }
        if (line == null && thought.actions.isEmpty()) {
            say(batch, Step.Say("I am listening. Please tell me more.", chips = DEFAULT_CHIPS))
        }
    }

    /**
     * Runs a tool and returns what Baymax should say about it, or null to stay quiet.
     * [silentSuccess] is set when the LLM already said something about it.
     */
    private suspend fun act(action: ToolAction, silentSuccess: Boolean): String? = when (action) {
        is ToolAction.CreateReminder -> runCatching {
            val reminder = Reminder(
                text = action.text,
                firstTime = now() + action.inMinutes * 60_000,
                repeatIntervalMinutes = action.repeatMinutes,
            )
            val id = repository.addReminder(reminder)
            reminders.schedule(reminder.copy(id = id))
            log(ChatMessage.Role.Tool, "create_reminder: ${action.text}")
            if (silentSuccess) null else ReminderParser.confirmation(action)
        }.getOrElse { "I could not set that reminder. Please try again." }

        is ToolAction.Remember -> {
            runCatching { repository.remember(action.fact, now()) }
            null
        }

        ToolAction.ReadHealth -> describeHealth(runCatching { healthReadings() }.getOrNull())

        is ToolAction.DraftMessage -> {
            val contact = action.contactName?.let { name -> contacts.firstOrNull { it.name.contains(name, ignoreCase = true) } }
                ?: contacts.firstOrNull()
            if (contact == null) {
                "Please add a trusted contact in Settings first. Then I can draft a message for you."
            } else {
                val text = action.message?.takeIf { it.isNotBlank() } ?: defaultMessage(contact, action.urgent)
                update { copy(draft = MessageDraft(contact, text)) }
                if (silentSuccess) null else "I have drafted a message to ${contact.name}. Please check it before sending."
            }
        }
    }

    private suspend fun say(batch: Batch, step: Step.Say) {
        // Never slur safety-critical lines.
        val styled = state.lowBattery && state.agentState != AgentState.Emergency
        val line = if (styled) lowBatteryStyle(step.line) else step.line
        log(ChatMessage.Role.Agent, line)
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

    companion object {
        const val WORD_MS = 160L
        const val TICK_MS = 30L
        const val SCAN_MS = 2000L
        const val SCAN_TICKS = 20
        const val CHAT_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        /** FR-29, in persona. */
        const val OFFLINE_LINE =
            "I cannot think clearly right now. My pain scale, health log, reminders and emergency button still work."

        val DEFAULT_CHIPS = listOf(QuickChip("ow"), QuickChip("I feel low"), QuickChip(SafetyRules.EXIT_TEXT, QuickChip.Style.Primary))

        fun defaultMessage(contact: TrustedContact, urgent: Boolean) =
            if (urgent) "Hi ${contact.name}, I am not feeling well and may need help. Can you call me or come over?"
            else "Hi ${contact.name}, I am feeling a bit low today. Could we talk for a few minutes?"

        fun describeHealth(r: HealthReadings?): String {
            if (r == null || (r.stepsToday == null && r.heartRateBpm == null && r.sleepMinutes == null)) {
                return "I cannot read your health data yet. Please connect Health Connect in Settings."
            }
            val parts = buildList {
                r.stepsToday?.let { add("Today you have walked ${"%,d".format(it)} steps.") }
                r.sleepMinutes?.let { add("You slept ${it / 60} hours and ${it % 60} minutes.") }
                r.heartRateBpm?.let { add("Your latest heart rate is $it beats per minute.") }
            }
            return parts.joinToString(" ")
        }
    }
}

/** FR-19: cosmetic "low battery" voice from the film. Only the caption changes. */
internal fun lowBatteryStyle(line: String): String {
    val lower = line.lowercase()
    val vowel = lower.indexOfFirst { it in "aeiou" }
    val stretched = if (vowel < 0) lower else lower.substring(0, vowel) + lower[vowel].toString().repeat(3) + lower.substring(vowel + 1)
    return stretched.replace(". ", "… ").replace(Regex("""\.$"""), "…")
}
