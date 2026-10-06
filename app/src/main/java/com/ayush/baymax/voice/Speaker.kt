package com.ayush.baymax.voice

/** Text-to-speech seam so the controller can be tested without Android (FR-3). */
interface Speaker {
    /** Speaks [text] and suspends until done. Cancelling the caller stops speech. */
    suspend fun speak(text: String, lowBattery: Boolean)

    /** Stops any current speech immediately. */
    fun stop()
}

/** Silent speaker for tests, previews and devices without a TTS engine. */
object SilentSpeaker : Speaker {
    override suspend fun speak(text: String, lowBattery: Boolean) = Unit
    override fun stop() = Unit
}

/** "Call 112" should be read as "one one two", not "one hundred twelve". */
fun speakableNumbers(text: String): String =
    text.replace(Regex("""\d{3,}""")) { m -> m.value.toCharArray().joinToString(" ") }
