package com.ayush.baymax.agent

import com.ayush.baymax.agent.SafetyRules.RedFlag

/** Settings the protocol needs. Persisted in DataStore from Phase 4. */
data class CareConfig(
    val distressWords: Set<String> = SafetyRules.DEFAULT_DISTRESS_WORDS,
    val emergencyNumber: String = "112",
    /** India's Tele-MANAS mental health helpline; offered alongside the emergency number for self-harm. */
    val mentalHealthHelpline: String = "14416",
    val trustedContactName: String? = null,
)

/** One finished care session, handed to the health log (FR-20). */
data class CareRecord(
    val startedAt: Long,
    val endedAt: Long,
    val painLevel: Int?,
    val mood: Int?,
    val symptoms: String,
    val note: String,
    val redFlag: Boolean,
    val finalState: AgentState,
)

/** Something the UI runner must do, in order. The protocol itself never waits or touches the UI. */
sealed interface Step {
    /** Inflate out of the case. [fast] skips the slow entrance for emergencies. */
    data class Wake(val fast: Boolean = false) : Step
    data class SetState(val state: AgentState) : Step
    data class Say(val line: String, val tilt: Boolean = false, val chips: List<QuickChip> = emptyList()) : Step
    data class Chest(val content: ChestContent) : Step
    data class Pause(val ms: Long) : Step
    /** Animate the scan, read health data, then call [CareProtocol.onScanFinished]. */
    data object RunScan : Step
    data class SaveRecord(val record: CareRecord) : Step
    data class OpenDialer(val number: String) : Step
    /** Ask the LLM (Phase 5). The runner speaks its reply and runs any tools it asks for. */
    data class Think(val userText: String) : Step
    /** Run a tool locally and speak its confirmation. */
    data class Act(val action: ToolAction) : Step
    /** Deflate back into the case. */
    data object Deactivate : Step
}

/**
 * A batch of steps. [preempt] means "cut off whatever Baymax is saying": used for emergencies
 * and whenever the user speaks, so his reply always answers the latest input.
 */
data class Reaction(val steps: List<Step>, val preempt: Boolean = true)

/**
 * Baymax's care protocol (SRS 1.3): greet → pain rating → scan → symptom interview →
 * self-care advice → "Are you satisfied with your care?" Only the exit phrase ends a
 * session (FR-12); any other answer returns to care (FR-13). Red flags are checked
 * before anything else, in every state, without the network (FR-14 to FR-16).
 *
 * Pure Kotlin: no Android, no coroutines, no LLM. Phase 5 plugs the LLM in for open
 * conversation in [AgentState.Listening]; the protocol and safety stay local.
 */
class CareProtocol(
    var config: CareConfig = CareConfig(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state: AgentState = AgentState.Idle
        private set

    private var session: Session? = null
    private var comfortIndex = 0

    private class Session(val startedAt: Long) {
        var pain: Int? = null
        var mood: Int? = null
        val answers = mutableListOf<String>()
        var questionIndex = 0
        var advice = ""
        var redFlag = false
        var saved = false
        var offeredDraft = false
    }

    // ---------------------------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------------------------

    fun onUserText(raw: String): Reaction {
        val text = raw.trim()
        if (text.isEmpty()) return Reaction(emptyList(), preempt = false)

        // 1. Safety first, in every state (FR-14).
        SafetyRules.redFlag(text)?.let { return emergency(it) }

        // 2. The exit phrase is the only way back to Idle (FR-12).
        if (SafetyRules.isExitPhrase(text)) {
            return if (state.isAwake) deactivate() else Reaction(emptyList(), preempt = false)
        }

        val t = SafetyRules.normalize(text)
        val distress = SafetyRules.isDistress(text, config.distressWords)

        return when (state) {
            AgentState.Idle -> wake(distress, t)
            AgentState.Waking, AgentState.Listening -> when {
                distress -> startCare(greet = false)
                else -> converse(t)
            }
            AgentState.PainScale -> {
                val n = parsePain(t)
                if (n != null) onPainSelected(n)
                else react(Step.Say("Please choose a number from one to ten. One is no pain. Ten is the worst pain.", tilt = true))
            }
            AgentState.Scanning -> react(Step.Say("Please hold still. I am almost done."), preempt = false)
            AgentState.Interview -> answer(text)
            AgentState.Care, AgentState.Satisfaction -> continueCare()
            AgentState.MoodCheck -> mood(t)
            AgentState.Emergency -> emergencyReply(t)
        }
    }

    /** Tapping the red case wakes Baymax without a complaint. */
    fun onCaseTap(): Reaction =
        if (state == AgentState.Idle) wake(distress = false, t = "") else Reaction(emptyList(), preempt = false)

    fun onPainSelected(level: Int): Reaction {
        if (state != AgentState.PainScale || level !in 1..10) return Reaction(emptyList(), preempt = false)
        session?.pain = level
        state = AgentState.Scanning
        val line = when {
            level >= 8 -> "A ${words(level)}. That is a lot of pain. I will scan you now."
            level <= 2 -> "A ${words(level)}. That is good. I will scan you now, to be sure."
            else -> "A ${words(level)}. I will scan you now."
        }
        return react(
            Step.Chest(ChestContent.PainScale(level)),
            Step.Pause(450),
            Step.Chest(ChestContent.None),
            Step.Say(line),
            Step.SetState(AgentState.Scanning),
            Step.RunScan,
        )
    }

    /** Called by the runner once the scan animation is done. */
    fun onScanFinished(readings: HealthReadings?): Reaction {
        if (state != AgentState.Scanning) return Reaction(emptyList(), preempt = false)
        state = AgentState.Interview
        session?.questionIndex = 0
        return react(
            Step.Chest(ChestContent.Scan(1f, readings)),
            Step.Say("Scan complete."),
            Step.Pause(900),
            Step.Chest(ChestContent.None),
            Step.SetState(AgentState.Interview),
            askQuestion(0),
            preempt = false,
        )
    }

    /** FR-26: draft a message to a trusted contact; the user confirms before it is sent. */
    fun onMessageFriend(): Reaction =
        react(Step.Act(ToolAction.DraftMessage(config.trustedContactName, null, urgent = state == AgentState.Emergency)), preempt = false)

    /** "I am okay now" from the emergency panel. */
    fun onImOkay(): Reaction = onUserText("I am okay now")

    // ---------------------------------------------------------------------------------
    // Flow
    // ---------------------------------------------------------------------------------

    private fun wake(distress: Boolean, t: String): Reaction {
        val steps = mutableListOf<Step>(Step.SetState(AgentState.Waking), Step.Wake())
        steps += Step.Say("Hello. I am Baymax, your personal healthcare companion.")
        state = AgentState.Listening
        return if (distress) {
            steps += Step.Say("I was alerted to the need for medical attention.")
            Reaction(steps + startCare(greet = false).steps)
        } else {
            steps += Step.SetState(AgentState.Listening)
            Reaction(steps + converse(t).steps)
        }
    }

    private fun startCare(greet: Boolean): Reaction {
        session = Session(now())
        state = AgentState.PainScale
        val steps = mutableListOf<Step>()
        if (greet) steps += Step.Say("Hello. I am Baymax, your personal healthcare companion.")
        steps += Step.SetState(AgentState.PainScale)
        steps += Step.Say("On a scale of one to ten, how would you rate your pain?", tilt = true)
        steps += Step.Chest(ChestContent.PainScale())
        return Reaction(steps)
    }

    private fun askQuestion(i: Int): Step.Say {
        val q = QUESTIONS[i]
        return Step.Say(q.text, tilt = true, chips = q.chips.map { QuickChip(it) })
    }

    private fun answer(text: String): Reaction {
        val s = session ?: return startCare(greet = false)
        s.answers += text
        s.questionIndex++
        return if (s.questionIndex < QUESTIONS.size) react(askQuestion(s.questionIndex)) else giveCare(s)
    }

    private fun giveCare(s: Session): Reaction {
        state = AgentState.Care
        val area = BodyArea.from(s.answers.getOrNull(0).orEmpty())
        val worse = s.answers.getOrNull(2)?.let { SafetyRules.normalize(it) }.orEmpty()
        val gettingWorse = worse.startsWith("yes") || worse.contains("worse")
        val longTime = s.answers.getOrNull(1)?.let { SafetyRules.normalize(it) }.orEmpty().contains("days")

        val steps = mutableListOf<Step>(Step.SetState(AgentState.Care), Step.Say(area.advice))
        if ((s.pain ?: 0) >= 8 || gettingWorse || longTime) {
            steps += Step.Say("Because ${reasonFor(s.pain, gettingWorse, longTime)}, I recommend that you see a doctor soon.")
        }
        s.advice = area.advice
        steps += saveIfNeeded(s, AgentState.Care)
        steps += satisfactionSteps()
        return Reaction(steps)
    }

    private fun reasonFor(pain: Int?, worse: Boolean, long: Boolean) = when {
        (pain ?: 0) >= 8 -> "your pain is high"
        worse -> "it is getting worse"
        long -> "it has lasted several days"
        else -> "of your symptoms"
    }

    /** FR-13: any answer other than the exit phrase returns to care. */
    private fun continueCare(): Reaction {
        state = AgentState.Care
        val comfort = COMFORT[comfortIndex++ % COMFORT.size]
        return Reaction(
            listOf(Step.SetState(AgentState.Care), Step.Say("I will continue to care for you."), Step.Say(comfort)) + satisfactionSteps(),
        )
    }

    private fun satisfactionSteps(): List<Step> {
        state = AgentState.Satisfaction
        return listOf(
            Step.SetState(AgentState.Satisfaction),
            Step.Say(
                "Are you satisfied with your care?",
                tilt = true,
                chips = listOf(QuickChip(SafetyRules.EXIT_TEXT, QuickChip.Style.Primary), QuickChip("Not yet")),
            ),
        )
    }

    private fun converse(t: String): Reaction = when {
        t.isEmpty() || GREETING.containsMatchIn(t) ->
            react(Step.Say("How can I help you today?", chips = DEFAULT_CHIPS))
        LOW_MOOD.containsMatchIn(t) -> {
            session = Session(now())
            state = AgentState.MoodCheck
            react(
                Step.SetState(AgentState.MoodCheck),
                Step.Say("I am sorry you feel this way. On a scale of one to five, how is your mood?", tilt = true, chips = (1..5).map { QuickChip("$it") }),
            )
        }
        LEAVE.containsMatchIn(t) ->
            react(Step.Say("I cannot deactivate until you say that you are satisfied with your care.", chips = listOf(QuickChip(SafetyRules.EXIT_TEXT, QuickChip.Style.Primary))))
        ReminderParser.parse(t) != null -> react(Step.Act(ReminderParser.parse(t)!!))
        NAME.find(t) != null -> {
            val name = NAME.find(t)!!.groupValues[1].replaceFirstChar { it.uppercase() }
            react(Step.Act(ToolAction.Remember("The user's name is $name.")), Step.Say("Hello, $name. I will remember your name."))
        }
        // Everything else is open conversation for the LLM (FR-4, FR-22, FR-24 to FR-26).
        else -> react(Step.Think(t))
    }

    private fun mood(t: String): Reaction {
        if (session?.offeredDraft == true) return draftAnswer(t)
        val n = t.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..5 }
            ?: return react(Step.Say("Please choose a number from one to five.", tilt = true, chips = (1..5).map { QuickChip("$it") }))
        val s = session ?: Session(now()).also { session = it }
        s.mood = n
        s.answers += "Mood check"
        val steps = mutableListOf<Step>()
        steps += Step.Say(
            if (n <= 2) "Thank you for telling me. Feeling low is hard. You do not have to face it alone."
            else "Thank you for telling me. I am glad you shared how you feel.",
        )
        val friend = config.trustedContactName
        s.advice = "Suggested talking to a trusted friend."
        steps += saveIfNeeded(s, AgentState.MoodCheck)
        if (friend != null) {
            // UC-3: offer to draft a message; the user confirms before anything is sent.
            s.offeredDraft = true
            steps += Step.Say(
                "Talking to someone you trust can help. Would you like me to draft a message to $friend?",
                tilt = true,
                chips = listOf(QuickChip("Yes, please", QuickChip.Style.Primary), QuickChip("No, thank you")),
            )
            return Reaction(steps)
        }
        steps += Step.Say("Talking to someone you trust can help. You could call a friend.")
        steps += satisfactionSteps()
        return Reaction(steps)
    }

    private fun draftAnswer(t: String): Reaction {
        session?.offeredDraft = false
        val yes = YES.containsMatchIn(t) && !Regex("""^(no|nope|not)\b""").containsMatchIn(t)
        val steps = mutableListOf<Step>()
        if (yes) steps += Step.Act(ToolAction.DraftMessage(config.trustedContactName, null))
        else steps += Step.Say("That is okay. I am here with you.")
        return Reaction(steps + satisfactionSteps())
    }

    private fun emergency(flag: RedFlag): Reaction {
        val wasIdle = state == AgentState.Idle
        state = AgentState.Emergency
        val s = session ?: Session(now()).also { session = it }
        s.redFlag = true
        s.answers += "Red flag: ${flag.name}"
        val number = config.emergencyNumber
        val steps = mutableListOf<Step>()
        if (wasIdle) steps += Step.Wake(fast = true)
        steps += Step.SetState(AgentState.Emergency)
        steps += Step.Chest(ChestContent.Emergency(number, config.trustedContactName))
        steps += Step.Say(
            "I have detected signs of a possible emergency. Please call $number now. I will stay with you.",
            chips = listOf(
                QuickChip("Call $number", QuickChip.Style.Danger),
                QuickChip("I am okay now"),
            ),
        )
        if (flag == RedFlag.SelfHarm) {
            steps += Step.Say("You can also call the Tele MANAS helpline at ${config.mentalHealthHelpline}. You matter to me.")
        }
        return Reaction(steps)
    }

    private fun emergencyReply(t: String): Reaction {
        val number = config.emergencyNumber
        return when {
            t.startsWith("call") -> react(Step.OpenDialer(number), preempt = false)
            OKAY.containsMatchIn(t) -> {
                val s = session
                val steps = mutableListOf<Step>(Step.Chest(ChestContent.None), Step.Say("I am glad. I will keep watching over you."))
                if (s != null) {
                    s.advice = "Emergency guidance shown. Advised to call $number."
                    steps += saveIfNeeded(s, AgentState.Emergency)
                }
                Reaction(steps + satisfactionSteps())
            }
            else -> react(Step.Say("I will stay with you. Please call $number now."))
        }
    }

    private fun deactivate(): Reaction {
        val steps = mutableListOf<Step>()
        session?.let { steps += saveIfNeeded(it, state) }
        session = null
        state = AgentState.Idle
        steps += Step.Deactivate
        return Reaction(steps)
    }

    private fun saveIfNeeded(s: Session, finalState: AgentState): List<Step> {
        if (s.saved) return emptyList()
        if (s.pain == null && s.mood == null && !s.redFlag) return emptyList()
        s.saved = true
        val symptoms = s.answers.joinToString(" · ")
        return listOf(
            Step.SaveRecord(
                CareRecord(
                    startedAt = s.startedAt,
                    endedAt = now(),
                    painLevel = s.pain,
                    mood = s.mood,
                    symptoms = symptoms,
                    note = s.advice,
                    redFlag = s.redFlag,
                    finalState = finalState,
                ),
            ),
        )
    }

    private fun react(vararg steps: Step, preempt: Boolean = true) = Reaction(steps.toList(), preempt)

    // ---------------------------------------------------------------------------------
    // Content
    // ---------------------------------------------------------------------------------

    private data class Question(val text: String, val chips: List<String>)

    /** Symptom interview, one question at a time (FR-9). */
    private enum class BodyArea(val keywords: List<String>, val advice: String) {
        Head(
            listOf("head", "headache", "migraine", "forehead", "temple"),
            "Drink a glass of water. Rest in a quiet, dim room. Limit screen time for one hour.",
        ),
        Stomach(
            listOf("stomach", "belly", "tummy", "abdomen", "gut", "nausea", "vomit"),
            "Sip warm water slowly. Avoid heavy or spicy food for a few hours. Rest on your side.",
        ),
        Throat(
            listOf("throat", "cough", "cold", "fever"),
            "Drink warm fluids. Gargle with warm salt water. Rest your voice and sleep well tonight.",
        ),
        Arm(
            listOf("arm", "hand", "wrist", "elbow", "finger", "shoulder"),
            "Your injury appears minor. Apply a cold pack for fifteen minutes. Rest the arm.",
        ),
        Leg(
            listOf("leg", "knee", "ankle", "foot", "toe", "hip", "thigh"),
            "Your injury appears minor. Apply a cold pack for fifteen minutes. Keep the leg raised.",
        ),
        Back(
            listOf("back", "spine", "neck"),
            "Rest on a firm surface. Gentle stretching may help. Avoid lifting heavy things today.",
        ),
        Other(
            emptyList(),
            "Rest for a while and drink some water. I will stay with you. Tell me if anything changes.",
        );

        companion object {
            fun from(answer: String): BodyArea {
                val words = SafetyRules.normalize(answer).split(Regex("""[^a-z]+"""))
                return entries.firstOrNull { area -> area.keywords.any { it in words } } ?: Other
            }
        }
    }

    private companion object {
        val QUESTIONS = listOf(
            Question("Where does it hurt?", listOf("Head", "Stomach", "Throat", "Arm", "Leg", "Back")),
            Question("When did it start?", listOf("Just now", "Earlier today", "A few days ago")),
            Question("Is it getting worse?", listOf("No", "A little", "Yes")),
        )

        val COMFORT = listOf(
            "Take a slow, deep breath. In through your nose, and out through your mouth.",
            "Try to rest. A warm drink may help you feel more comfortable.",
            "You are doing well. Healing takes time. I am here.",
        )

        val DEFAULT_CHIPS = listOf(QuickChip("ow"), QuickChip("I feel low"), QuickChip(SafetyRules.EXIT_TEXT, QuickChip.Style.Primary))

        val GREETING = Regex("""^(hi|hello|hey|hii+|namaste|good (morning|afternoon|evening))\b""")
        val LOW_MOOD = Regex("""\b(low|sad|lonely|depressed|down|anxious|stressed|upset|unhappy|crying)\b""")
        val LEAVE = Regex("""\b(bye|goodbye|go away|deactivate|shut down|turn off|leave me)\b""")
        val YES = Regex("""\b(yes|yeah|yep|sure|please|okay|ok)\b""")
        val NAME = Regex("""\bmy name is ([a-z][a-z'-]{1,30})""")
        val OKAY = Regex("""\b(okay|ok|fine|better|safe|alright)\b""")

        val NUMBER_WORDS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

        fun words(n: Int) = NUMBER_WORDS[n - 1]

        /** "4", "four", "I think a 7" → 4 / 4 / 7. */
        fun parsePain(t: String): Int? {
            Regex("""\b(10|[1-9])\b""").find(t)?.let { return it.value.toInt() }
            val tokens = t.split(Regex("""[^a-z]+"""))
            val i = NUMBER_WORDS.indexOfFirst { it in tokens }
            return if (i >= 0) i + 1 else null
        }
    }
}
