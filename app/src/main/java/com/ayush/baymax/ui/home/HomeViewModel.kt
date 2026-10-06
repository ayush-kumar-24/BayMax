package com.ayush.baymax.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.baymax.BuildConfig
import com.ayush.baymax.agent.CareProtocol
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.data.TrustedContact
import com.ayush.baymax.data.room.RoomRepository
import com.ayush.baymax.llm.FirebaseGeminiClient
import com.ayush.baymax.llm.GeminiRestClient
import com.ayush.baymax.llm.GroqClient
import com.ayush.baymax.llm.LlmBrain
import com.ayush.baymax.llm.LlmRouter
import com.ayush.baymax.platform.HealthConnectReader
import com.ayush.baymax.platform.WorkManagerReminderScheduler
import com.ayush.baymax.ui.settings.HealthConnectStatus
import com.ayush.baymax.voice.SpeechInput
import com.ayush.baymax.voice.TtsSpeaker
import kotlinx.coroutines.launch

/**
 * Android owner of the app: storage, voice, LLM providers, reminders and Health Connect.
 * Survives rotation; the Activity only supplies things that need an Activity (intents,
 * permission prompts).
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val repository = RoomRepository(app, viewModelScope)
    val healthReader = HealthConnectReader(app)
    val speechInput = SpeechInput(app)

    /** Set by the Activity. */
    var dialer: (String) -> Unit = {}
    var messenger: (TrustedContact, String, ContactApp) -> Unit = { _, _, _ -> }
    var requestNotificationPermission: () -> Unit = {}

    val reminders = WorkManagerReminderScheduler(app) { requestNotificationPermission() }

    private val speaker = TtsSpeaker(app) { repository.settings.value.let { it.speechRate to it.pitch } }

    // Primary: Gemini via Firebase AI Logic (needs google-services.json). Same model over REST
    // if only an AI Studio key is set. Fallback: Groq (SRS 4.3, FR-28).
    private val clients = listOf(
        FirebaseGeminiClient(app),
        GeminiRestClient(BuildConfig.GEMINI_API_KEY),
        GroqClient(BuildConfig.GROQ_API_KEY),
    )
    private val router = LlmRouter(clients)

    val brainStatus: String = clients.filter { it.isConfigured }.let { ok ->
        when {
            ok.isEmpty() -> "No language model configured. Add a key in secrets.properties or google-services.json. Care and safety work offline."
            else -> ok.joinToString(", then ") { c -> if (c is FirebaseGeminiClient) "Gemini (Firebase)" else c.name } + ". Offline features always work."
        }
    }

    var healthStatus by mutableStateOf(HealthConnectStatus.Unavailable)
        private set

    val controller = HomeController(
        scope = viewModelScope,
        protocol = CareProtocol(),
        speaker = speaker,
        brain = LlmBrain(router, repository),
        repository = repository,
        healthReadings = { healthReader.read() },
        reminders = reminders,
        onDial = { dialer(it) },
        onSendMessage = { c, t, a -> messenger(c, t, a) },
    )

    init {
        refreshHealthStatus()
    }

    fun refreshHealthStatus() {
        viewModelScope.launch { healthStatus = healthReader.status() }
    }

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
