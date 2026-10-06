package com.ayush.baymax.data.room

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ayush.baymax.agent.CareRecord
import com.ayush.baymax.data.AppSettings
import com.ayush.baymax.data.BaymaxRepository
import com.ayush.baymax.data.CareSession
import com.ayush.baymax.data.ChatMessage
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.data.HealthEntry
import com.ayush.baymax.data.MemoryFact
import com.ayush.baymax.data.Mode
import com.ayush.baymax.data.Reminder
import com.ayush.baymax.data.ThemeMode
import com.ayush.baymax.data.TrustedContact
import com.ayush.baymax.data.toHealthEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Room for entities, Preferences DataStore for settings (SRS 7). Nothing leaves the phone. */
class RoomRepository(context: Context, scope: CoroutineScope) : BaymaxRepository {

    private val db = BaymaxDatabase.get(context)
    private val dao = db.dao()
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val voiceOn = booleanPreferencesKey("voiceOn")
        val speechRate = floatPreferencesKey("speechRate")
        val pitch = floatPreferencesKey("pitch")
        val distressWords = stringSetPreferencesKey("distressWords")
        val emergencyNumber = stringPreferencesKey("emergencyNumber")
        val enabledChips = stringSetPreferencesKey("enabledChips")
        val theme = stringPreferencesKey("theme")
        val disclaimerAccepted = booleanPreferencesKey("disclaimerAccepted")
    }

    private val loaded = MutableStateFlow(false)
    override val settingsLoaded: StateFlow<Boolean> = loaded.asStateFlow()

    override val settings: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .onEach { loaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            voiceOn = this[Keys.voiceOn] ?: d.voiceOn,
            speechRate = this[Keys.speechRate] ?: d.speechRate,
            pitch = this[Keys.pitch] ?: d.pitch,
            distressWords = this[Keys.distressWords] ?: d.distressWords,
            emergencyNumber = this[Keys.emergencyNumber] ?: d.emergencyNumber,
            enabledChips = this[Keys.enabledChips].orEmpty().mapNotNull { n -> Mode.entries.firstOrNull { it.name == n } }.toSet(),
            theme = this[Keys.theme]?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: d.theme,
            disclaimerAccepted = this[Keys.disclaimerAccepted] ?: d.disclaimerAccepted,
        )
    }

    override suspend fun updateSettings(change: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = change(prefs.toSettings())
            prefs[Keys.voiceOn] = s.voiceOn
            prefs[Keys.speechRate] = s.speechRate
            prefs[Keys.pitch] = s.pitch
            prefs[Keys.distressWords] = s.distressWords
            prefs[Keys.emergencyNumber] = s.emergencyNumber
            prefs[Keys.enabledChips] = s.enabledChips.map { it.name }.toSet()
            prefs[Keys.theme] = s.theme.name
            prefs[Keys.disclaimerAccepted] = s.disclaimerAccepted
        }
    }

    // --- health log --------------------------------------------------------------------

    override val healthEntries: Flow<List<HealthEntry>> = dao.healthEntries().map { list ->
        list.map { HealthEntry(it.id, it.timestamp, it.painLevel, it.mood, it.symptoms, it.note, it.sessionId, it.redFlag) }
    }

    override suspend fun saveCareRecord(record: CareRecord): Long {
        val s = CareSession(startedAt = record.startedAt, endedAt = record.endedAt, finalState = record.finalState.name, redFlag = record.redFlag)
        val e = record.toHealthEntry(sessionId = null)
        return dao.insertRecord(
            CareSessionEntity(startedAt = s.startedAt, endedAt = s.endedAt, finalState = s.finalState, redFlag = s.redFlag),
            HealthEntryEntity(timestamp = e.timestamp, painLevel = e.painLevel, mood = e.mood, symptoms = e.symptoms, note = e.note, sessionId = null, redFlag = e.redFlag),
        )
    }

    override suspend fun deleteHealthEntry(id: Long) = dao.deleteHealthEntry(id)

    // --- chat ----------------------------------------------------------------------------

    override val chat: Flow<List<ChatMessage>> = dao.chat().map { list -> list.map { it.toModel() } }

    private fun ChatMessageEntity.toModel() = ChatMessage(
        id, sessionId, ChatMessage.Role.entries.firstOrNull { it.name == role } ?: ChatMessage.Role.Agent, text, timestamp,
    )

    override suspend fun addChat(message: ChatMessage) {
        dao.insertChat(ChatMessageEntity(sessionId = message.sessionId, role = message.role.name, text = message.text, timestamp = message.timestamp))
    }

    override suspend fun recentChat(limit: Int) = dao.recentChat(limit).map { it.toModel() }

    override suspend fun pruneChatOlderThan(timestamp: Long) = dao.pruneChat(timestamp)

    // --- memory --------------------------------------------------------------------------

    override val memories: Flow<List<MemoryFact>> = dao.memories().map { list ->
        list.map { MemoryFact(it.id, it.text, it.createdAt, it.lastUsedAt) }
    }

    override suspend fun remember(text: String, now: Long) {
        val clean = text.trim()
        if (clean.isEmpty() || dao.countFact(clean) > 0) return
        dao.insertFact(MemoryFactEntity(text = clean, createdAt = now, lastUsedAt = now))
    }

    override suspend fun factsForPrompt(limit: Int, now: Long): List<MemoryFact> {
        val top = dao.topFacts(limit)
        if (top.isNotEmpty()) dao.touchFacts(top.map { it.id }, now)
        return top.map { MemoryFact(it.id, it.text, it.createdAt, it.lastUsedAt) }
    }

    override suspend fun deleteMemory(id: Long) = dao.deleteFact(id)

    // --- reminders -----------------------------------------------------------------------

    override val reminders: Flow<List<Reminder>> = dao.reminders().map { list ->
        list.map { Reminder(it.id, it.text, it.firstTime, it.repeatIntervalMinutes, it.active) }
    }

    override suspend fun addReminder(reminder: Reminder): Long =
        dao.insertReminder(ReminderEntity(text = reminder.text, firstTime = reminder.firstTime, repeatIntervalMinutes = reminder.repeatIntervalMinutes, active = reminder.active))

    override suspend fun deleteReminder(id: Long) = dao.deleteReminder(id)

    // --- contacts ------------------------------------------------------------------------

    override val contacts: Flow<List<TrustedContact>> = dao.contacts().map { list ->
        list.map { c -> TrustedContact(c.id, c.name, c.phone, ContactApp.entries.firstOrNull { it.name == c.preferredApp } ?: ContactApp.Sms) }
    }

    override suspend fun addContact(contact: TrustedContact): Long =
        dao.insertContact(TrustedContactEntity(name = contact.name, phone = contact.phone, preferredApp = contact.preferredApp.name))

    override suspend fun deleteContact(id: Long) = dao.deleteContact(id)

    // --- forget everything ---------------------------------------------------------------

    override suspend fun forgetEverything() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        store.edit { it.clear() }
    }
}
