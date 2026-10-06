package com.ayush.baymax.data

import com.ayush.baymax.agent.CareRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything Baymax keeps on the phone. Room + DataStore on Android, in memory for tests. */
interface BaymaxRepository {
    val healthEntries: Flow<List<HealthEntry>>
    val chat: Flow<List<ChatMessage>>
    val memories: Flow<List<MemoryFact>>
    val reminders: Flow<List<Reminder>>
    val contacts: Flow<List<TrustedContact>>
    val settings: StateFlow<AppSettings>
    /** False until stored settings have been read, so the first-launch notice never flashes. */
    val settingsLoaded: StateFlow<Boolean>

    /** Saves the care session and its health entry together (FR-20). */
    suspend fun saveCareRecord(record: CareRecord): Long
    suspend fun deleteHealthEntry(id: Long)

    suspend fun addChat(message: ChatMessage)
    suspend fun recentChat(limit: Int): List<ChatMessage>
    /** ChatMessage retention is 30 days (SRS 7). */
    suspend fun pruneChatOlderThan(timestamp: Long)

    /** Stores a fact unless an equivalent one exists (FR-22). */
    suspend fun remember(text: String, now: Long)
    /** Up to [limit] facts, most recently used first; marks them used (NFR-9). */
    suspend fun factsForPrompt(limit: Int, now: Long): List<MemoryFact>
    suspend fun deleteMemory(id: Long)

    suspend fun addReminder(reminder: Reminder): Long
    suspend fun deleteReminder(id: Long)

    suspend fun addContact(contact: TrustedContact): Long
    suspend fun deleteContact(id: Long)

    suspend fun updateSettings(change: (AppSettings) -> AppSettings)

    /** FR-23: erases every entity and every setting. */
    suspend fun forgetEverything()
}

fun CareRecord.toHealthEntry(sessionId: Long?) = HealthEntry(
    timestamp = endedAt,
    painLevel = painLevel,
    mood = mood,
    symptoms = symptoms,
    note = note,
    sessionId = sessionId,
    redFlag = redFlag,
)

/** Thread-safe enough for tests and previews; Android uses Room. */
class InMemoryRepository(initial: AppSettings = AppSettings()) : BaymaxRepository {
    private var nextId = 1L
    private fun id() = nextId++

    private val _health = MutableStateFlow<List<HealthEntry>>(emptyList())
    private val _sessions = MutableStateFlow<List<CareSession>>(emptyList())
    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryFact>>(emptyList())
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    private val _contacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    private val _settings = MutableStateFlow(initial)

    override val healthEntries: Flow<List<HealthEntry>> = _health.asStateFlow()
    override val chat: Flow<List<ChatMessage>> = _chat.asStateFlow()
    override val memories: Flow<List<MemoryFact>> = _memories.asStateFlow()
    override val reminders: Flow<List<Reminder>> = _reminders.asStateFlow()
    override val contacts: Flow<List<TrustedContact>> = _contacts.asStateFlow()
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    override val settingsLoaded: StateFlow<Boolean> = MutableStateFlow(true)

    val sessions: List<CareSession> get() = _sessions.value

    override suspend fun saveCareRecord(record: CareRecord): Long {
        val sid = id()
        _sessions.update { it + CareSession(sid, record.startedAt, record.endedAt, record.finalState.name, record.redFlag) }
        _health.update { listOf(record.toHealthEntry(sid).copy(id = id())) + it }
        return sid
    }

    override suspend fun deleteHealthEntry(id: Long) = _health.update { list -> list.filterNot { it.id == id } }

    override suspend fun addChat(message: ChatMessage) = _chat.update { it + message.copy(id = id()) }

    override suspend fun recentChat(limit: Int) = _chat.value.takeLast(limit)

    override suspend fun pruneChatOlderThan(timestamp: Long) = _chat.update { list -> list.filter { it.timestamp >= timestamp } }

    override suspend fun remember(text: String, now: Long) {
        val clean = text.trim()
        if (clean.isEmpty() || _memories.value.any { it.text.equals(clean, ignoreCase = true) }) return
        _memories.update { it + MemoryFact(id(), clean, now, now) }
    }

    override suspend fun factsForPrompt(limit: Int, now: Long): List<MemoryFact> {
        val top = _memories.value.sortedByDescending { it.lastUsedAt }.take(limit)
        val ids = top.map { it.id }.toSet()
        _memories.update { list -> list.map { if (it.id in ids) it.copy(lastUsedAt = now) else it } }
        return top
    }

    override suspend fun deleteMemory(id: Long) = _memories.update { list -> list.filterNot { it.id == id } }

    override suspend fun addReminder(reminder: Reminder): Long {
        val rid = id()
        _reminders.update { it + reminder.copy(id = rid) }
        return rid
    }

    override suspend fun deleteReminder(id: Long) = _reminders.update { list -> list.filterNot { it.id == id } }

    override suspend fun addContact(contact: TrustedContact): Long {
        val cid = id()
        _contacts.update { it + contact.copy(id = cid) }
        return cid
    }

    override suspend fun deleteContact(id: Long) = _contacts.update { list -> list.filterNot { it.id == id } }

    override suspend fun updateSettings(change: (AppSettings) -> AppSettings) = _settings.update(change)

    override suspend fun forgetEverything() {
        _health.value = emptyList()
        _sessions.value = emptyList()
        _chat.value = emptyList()
        _memories.value = emptyList()
        _reminders.value = emptyList()
        _contacts.value = emptyList()
        _settings.value = AppSettings()
    }
}
