package com.ayush.baymax.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.baymax.agent.CareProtocol
import com.ayush.baymax.agent.CareRecord
import com.ayush.baymax.voice.SpeechInput
import com.ayush.baymax.voice.TtsSpeaker

/**
 * Android owner of the Home screen: keeps the controller, voice and speech input alive
 * across rotation and shuts them down with the screen.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val speaker = TtsSpeaker(app)
    val speechInput = SpeechInput(app)

    /** Health log arrives in Phase 4 (Room). Until then records are kept in memory. */
    val savedRecords = mutableListOf<CareRecord>()

    /** Set by the Activity, which owns the intent to open the dialer. */
    var dialer: (String) -> Unit = {}

    val controller = HomeController(
        scope = viewModelScope,
        protocol = CareProtocol(),
        speaker = speaker,
        onSaveRecord = { savedRecords += it },
        onDial = { dialer(it) },
    )

    fun startListening(onError: (String) -> Unit) {
        if (speechInput.isListening) {
            speechInput.stop()
            controller.setListening(false)
            return
        }
        speaker.stop()
        controller.setListening(true)
        speechInput.start(object : SpeechInput.Listener {
            override fun onPartial(text: String) = controller.setPartialSpeech(text)
            override fun onFinal(text: String) = controller.send(text)
            override fun onError(message: String) = onError(message)
            override fun onEnd() = controller.setListening(false)
        })
    }

    override fun onCleared() {
        speechInput.stop()
        speaker.shutdown()
    }
}
