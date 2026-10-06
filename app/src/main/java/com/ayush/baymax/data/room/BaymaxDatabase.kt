package com.ayush.baymax.data.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Entities mirror SRS section 7. Enums are stored as their names.

@Entity(tableName = "health_entry", indices = [Index("timestamp")])
data class HealthEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val painLevel: Int?,
    val mood: Int?,
    val symptoms: String,
    val note: String,
    val sessionId: Long?,
    val redFlag: Boolean,
)

@Entity(tableName = "care_session")
data class CareSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val finalState: String,
    val redFlag: Boolean,
)

@Entity(tableName = "chat_message", indices = [Index("timestamp")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val role: String,
    val text: String,
    val timestamp: Long,
)

@Entity(tableName = "memory_fact")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val lastUsedAt: Long,
)

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val firstTime: Long,
    val repeatIntervalMinutes: Long?,
    val active: Boolean,
)

@Entity(tableName = "trusted_contact")
data class TrustedContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val preferredApp: String,
)

@Dao
abstract class BaymaxDao {
    // Health log
    @Query("SELECT * FROM health_entry ORDER BY timestamp DESC")
    abstract fun healthEntries(): Flow<List<HealthEntryEntity>>

    @Insert
    abstract suspend fun insertSession(session: CareSessionEntity): Long

    @Insert
    abstract suspend fun insertHealthEntry(entry: HealthEntryEntity): Long

    @Transaction
    open suspend fun insertRecord(session: CareSessionEntity, entry: HealthEntryEntity): Long {
        val sid = insertSession(session)
        insertHealthEntry(entry.copy(sessionId = sid))
        return sid
    }

    @Query("DELETE FROM health_entry WHERE id = :id")
    abstract suspend fun deleteHealthEntry(id: Long)

    // Chat
    @Query("SELECT * FROM chat_message ORDER BY timestamp ASC, id ASC")
    abstract fun chat(): Flow<List<ChatMessageEntity>>

    @Insert
    abstract suspend fun insertChat(message: ChatMessageEntity): Long

    @Query("SELECT * FROM (SELECT * FROM chat_message ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC")
    abstract suspend fun recentChat(limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_message WHERE timestamp < :timestamp")
    abstract suspend fun pruneChat(timestamp: Long)

    // Memory
    @Query("SELECT * FROM memory_fact ORDER BY lastUsedAt DESC")
    abstract fun memories(): Flow<List<MemoryFactEntity>>

    @Query("SELECT COUNT(*) FROM memory_fact WHERE LOWER(text) = LOWER(:text)")
    abstract suspend fun countFact(text: String): Int

    @Insert
    abstract suspend fun insertFact(fact: MemoryFactEntity): Long

    @Query("SELECT * FROM memory_fact ORDER BY lastUsedAt DESC LIMIT :limit")
    abstract suspend fun topFacts(limit: Int): List<MemoryFactEntity>

    @Query("UPDATE memory_fact SET lastUsedAt = :now WHERE id IN (:ids)")
    abstract suspend fun touchFacts(ids: List<Long>, now: Long)

    @Query("DELETE FROM memory_fact WHERE id = :id")
    abstract suspend fun deleteFact(id: Long)

    // Reminders
    @Query("SELECT * FROM reminder ORDER BY firstTime ASC")
    abstract fun reminders(): Flow<List<ReminderEntity>>

    @Insert
    abstract suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminder WHERE id = :id")
    abstract suspend fun deleteReminder(id: Long)

    // Contacts
    @Query("SELECT * FROM trusted_contact ORDER BY name ASC")
    abstract fun contacts(): Flow<List<TrustedContactEntity>>

    @Insert
    abstract suspend fun insertContact(contact: TrustedContactEntity): Long

    @Query("DELETE FROM trusted_contact WHERE id = :id")
    abstract suspend fun deleteContact(id: Long)
}

@Database(
    entities = [
        HealthEntryEntity::class,
        CareSessionEntity::class,
        ChatMessageEntity::class,
        MemoryFactEntity::class,
        ReminderEntity::class,
        TrustedContactEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BaymaxDatabase : RoomDatabase() {
    abstract fun dao(): BaymaxDao

    companion object {
        @Volatile private var instance: BaymaxDatabase? = null

        fun get(context: Context): BaymaxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, BaymaxDatabase::class.java, "baymax.db")
                    .build()
                    .also { instance = it }
            }
    }
}
