package com.ayush.baymax.agent

/** States of the Baymax care protocol (SRS 1.3, FR-6 to FR-15). */
enum class AgentState(val label: String) {
    Idle("Idle"),
    Waking("Waking"),
    Listening("Listening"),
    PainScale("Pain scale"),
    Scanning("Scanning"),
    Interview("Care"),
    Care("Care"),
    Satisfaction("Care"),
    MoodCheck("Care"),
    Emergency("Emergency"),
    ;

    /** Baymax is out of his case in every state except Idle. */
    val isAwake: Boolean get() = this != Idle
}
