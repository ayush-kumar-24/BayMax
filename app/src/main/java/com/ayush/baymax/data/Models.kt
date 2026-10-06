package com.ayush.baymax.data

import com.ayush.baymax.agent.SafetyRules

/** SRS section 7. All stored on the device only (NFR-8). */

data class HealthEntry(
    val id: Long = 0,
    val timestamp: Long,
    /** 1–10, or null for a mood-only check. */
    val painLevel: Int?,
    /** 1–5, or null when not asked. */
    val mood: Int?,
    val symptoms: String,
    val note: String,
    val sessionId: Long?,
    val redFlag: Boolean = false,
)

data class CareSession(
    val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val finalState: String,
    val redFlag: Boolean,
)

data class ChatMessage(
    val id: Long = 0,
    val sessionId: Long? = null,
    val role: Role,
    val text: String,
    val timestamp: Long,
) {
    enum class Role { User, Agent, Tool }
}

data class MemoryFact(
    val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val lastUsedAt: Long,
)

data class Reminder(
    val id: Long = 0,
    val text: String,
    val firstTime: Long,
    /** Null for a one-time reminder. */
    val repeatIntervalMinutes: Long?,
    val active: Boolean = true,
)

data class TrustedContact(
    val id: Long = 0,
    val name: String,
    val phone: String,
    val preferredApp: ContactApp = ContactApp.Sms,
)

enum class ContactApp(val label: String) { Sms("SMS"), WhatsApp("WhatsApp") }

/** Optional chips layered on the always-on Care mode (FR-30). */
enum class Mode(val label: String, val promptHint: String) {
    Fitness("Fitness", "Fitness mode is on: gently encourage movement, stretching and water."),
    Sleep("Sleep", "Sleep mode is on: speak softly about rest and a calm bedtime routine."),
    Study("Study", "Study mode is on: suggest short breaks, posture and focus."),
}

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val voiceOn: Boolean = true,
    val speechRate: Float = 0.85f,
    val pitch: Float = 0.8f,
    val distressWords: Set<String> = SafetyRules.DEFAULT_DISTRESS_WORDS,
    val emergencyNumber: String = "112",
    val enabledChips: Set<Mode> = emptySet(),
    val theme: ThemeMode = ThemeMode.System,
    val disclaimerAccepted: Boolean = false,
)

/** Delivers reminders as notifications (FR-25). WorkManager on Android. */
interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(id: Long)
    fun cancelAll()

    object None : ReminderScheduler {
        override fun schedule(reminder: Reminder) = Unit
        override fun cancel(id: Long) = Unit
        override fun cancelAll() = Unit
    }
}
