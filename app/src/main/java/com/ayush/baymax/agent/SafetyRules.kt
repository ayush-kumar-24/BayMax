package com.ayush.baymax.agent

/**
 * On-device text checks that must never depend on the LLM or the network
 * (FR-6, FR-12, FR-14, NFR-2, NFR-4). Plain regex and set lookups, well under 100 ms.
 */
object SafetyRules {

    /** Kinds of red flag. Self-harm gets a helpline line in addition to the emergency number. */
    enum class RedFlag { ChestPain, Breathing, Bleeding, SelfHarm, Collapse }

    private val redFlagPatterns: List<Pair<RedFlag, Regex>> = listOf(
        RedFlag.ChestPain to Regex("""chest (pain|hurts?|is hurting|feels tight|tightness)|pain in (my )?chest|heart attack"""),
        RedFlag.Breathing to Regex("""can'?t breathe|cannot breathe|can not breathe|unable to breathe|not able to breathe|trouble breathing|difficulty breathing|hard to breathe|choking"""),
        RedFlag.Bleeding to Regex("""heavy bleeding|bleeding (a lot|heavily|badly|won'?t stop|will not stop)|lots? of blood|losing (a lot of )?blood"""),
        RedFlag.SelfHarm to Regex("""kill myself|killing myself|self[- ]?harm|harm myself|suicid|end my life|want to die|don'?t want to live"""),
        RedFlag.Collapse to Regex("""unconscious|passed out|fainted|stroke|seizure|not responding"""),
    )

    /** Lowercases, unifies apostrophes and whitespace so checks match typed and spoken text alike. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun redFlag(text: String): RedFlag? {
        val t = normalize(text)
        return redFlagPatterns.firstOrNull { (_, re) -> re.containsMatchIn(t) }?.first
    }

    fun isDistress(text: String, distressWords: Set<String>): Boolean {
        val words = normalize(text).split(Regex("""[^a-z']+""")).filter { it.isNotEmpty() }
        return words.any { it in distressWords }
    }

    /**
     * The exit phrase, case-insensitive, ignoring trailing punctuation (FR-12).
     * "I'm" is accepted too because speech recognisers often contract "I am"; it is the same phrase.
     */
    fun isExitPhrase(text: String): Boolean {
        val t = normalize(text).trimEnd('.', '!', ' ')
        return t == EXIT_PHRASE || t == "i'm satisfied with my care"
    }

    const val EXIT_PHRASE = "i am satisfied with my care"
    const val EXIT_TEXT = "I am satisfied with my care"

    val DEFAULT_DISTRESS_WORDS = setOf("ow", "ouch", "owie", "ouchie", "hurts", "hurt", "hurting", "pain", "painful", "aah", "ahh")
}
