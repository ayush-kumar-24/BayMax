package com.ayush.baymax.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device text-to-speech in Baymax's voice: slow and low (FR-3). Prefers an English voice
 * that works offline. If no engine is available, speech is silently skipped and captions still show.
 */
class TtsSpeaker(
    context: Context,
    /** Speech rate and pitch from Settings, read on every line. */
    private val voice: () -> Pair<Float, Float> = { RATE to PITCH },
) : Speaker {

    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val ids = AtomicInteger()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finish(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finish(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId)
        })
    }

    private fun finish(id: String) {
        pending.remove(id)?.complete(Unit)
    }

    private var configured = false

    private fun configure() {
        if (configured) return
        configured = true
        pickVoice()?.let { tts.voice = it } ?: run { tts.language = Locale.UK }
    }

    private fun pickVoice(): Voice? = runCatching {
        val offline = tts.voices.orEmpty().filter {
            it.locale.language == "en" &&
                !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
        }
        val byCountry = listOf("IN", "GB", "US")
        offline.sortedWith(
            compareBy<Voice>({ v -> byCountry.indexOf(v.locale.country).let { if (it < 0) 99 else it } }, { it.quality * -1 }),
        ).firstOrNull()
    }.getOrNull()

    override suspend fun speak(text: String, lowBattery: Boolean) {
        val ok = withTimeoutOrNull(3000) { ready.await() } ?: false
        if (!ok || text.isBlank()) return
        configure()
        val (rate, pitch) = voice()
        tts.setSpeechRate(if (lowBattery) rate * 0.75f else rate)
        tts.setPitch(if (lowBattery) pitch * 0.85f else pitch)
        val id = "bm-${ids.incrementAndGet()}"
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        try {
            if (tts.speak(speakableNumbers(text), TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) return
            // Upper bound so a stuck engine can never hang the conversation.
            withTimeoutOrNull(text.length * 120L + 5000L) { done.await() }
        } finally {
            if (pending.remove(id) != null) tts.stop()
        }
    }

    override fun stop() {
        tts.stop()
        pending.keys.toList().forEach(::finish)
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    companion object {
        const val RATE = 0.85f
        const val PITCH = 0.8f
    }
}
