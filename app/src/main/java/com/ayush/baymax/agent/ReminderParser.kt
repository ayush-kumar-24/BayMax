package com.ayush.baymax.agent

/**
 * Understands simple reminder requests without the LLM, so reminders work offline (NFR-6):
 * "remind me to drink water every 2 hours", "remind me in 20 minutes to stretch",
 * "remind me to take a break every hour".
 */
object ReminderParser {

    private const val AMOUNT = """(\d+|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty five|half an?)"""
    private const val UNIT = """(minutes?|mins?|hours?|hrs?|days?)"""

    // "remind me to X every|in N unit"
    private val textFirst = Regex("""remind me (?:to )?(.+?)\s+(every|in|after)\s+(?:$AMOUNT\s+)?$UNIT\b""")
    // "remind me every|in N unit to X"
    private val timeFirst = Regex("""remind me\s+(every|in|after)\s+(?:$AMOUNT\s+)?$UNIT\s+(?:to\s+)?(.+)""")

    fun parse(text: String): ToolAction.CreateReminder? {
        val t = SafetyRules.normalize(text).trimEnd('.', '!', '?')
        textFirst.find(t)?.let { m ->
            val (what, kind, amount, unit) = m.destructured
            return build(what, kind, amount, unit)
        }
        timeFirst.find(t)?.let { m ->
            val (kind, amount, unit, what) = m.destructured
            return build(what, kind, amount, unit)
        }
        return null
    }

    private fun build(what: String, kind: String, amount: String, unit: String): ToolAction.CreateReminder? {
        val n = amountOf(amount) ?: return null
        val minutes = when {
            unit.startsWith("min") -> n
            unit.startsWith("h") -> n * 60
            unit.startsWith("day") -> n * 1440
            else -> return null
        }.toLong().coerceAtLeast(1)
        val task = what.trim().removePrefix("to ").trim().ifEmpty { return null }
        return if (kind == "every") ToolAction.CreateReminder(task, minutes, minutes) else ToolAction.CreateReminder(task, minutes, null)
    }

    private fun amountOf(a: String): Double? = when (a) {
        "" , "a", "an", "one" -> 1.0
        "half a", "half an" -> 0.5
        "two" -> 2.0; "three" -> 3.0; "four" -> 4.0; "five" -> 5.0; "six" -> 6.0
        "seven" -> 7.0; "eight" -> 8.0; "nine" -> 9.0; "ten" -> 10.0; "eleven" -> 11.0
        "twelve" -> 12.0; "fifteen" -> 15.0; "twenty" -> 20.0; "thirty" -> 30.0; "forty five" -> 45.0
        else -> a.toDoubleOrNull()
    }?.takeIf { it > 0 }

    /** "every two hours", "in twenty minutes": for Baymax's spoken confirmation. */
    fun describe(minutes: Long): String = when {
        minutes % 1440 == 0L -> plural(minutes / 1440, "day")
        minutes % 60 == 0L -> plural(minutes / 60, "hour")
        else -> plural(minutes, "minute")
    }

    private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

    private fun plural(n: Long, unit: String): String {
        val num = if (n in 0..12) WORDS[n.toInt()] else n.toString()
        return if (n == 1L) "one $unit" else "$num ${unit}s"
    }

    fun confirmation(action: ToolAction.CreateReminder): String {
        val repeat = action.repeatMinutes
        return if (repeat != null) {
            "Reminder set. I will remind you to ${action.text} every ${describe(repeat).removePrefix("one ")}."
        } else {
            "Reminder set. I will remind you to ${action.text} in ${describe(action.inMinutes)}."
        }
    }
}
