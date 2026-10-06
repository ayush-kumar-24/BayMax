package com.ayush.baymax.agent

import androidx.compose.runtime.Immutable

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
