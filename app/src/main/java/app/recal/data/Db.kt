package app.recal.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One "why I called" entry. Pending while [doneAt] is null. */
@Entity(tableName = "reminders", indices = [Index("key"), Index("doneAt")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Canonical digits shared by every entry for the same person. */
    val canon: String,
    val key: String,
    /** The number as it was dialled, for display and for calling back. */
    val number: String,
    val name: String?,
    val reason: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "NULL") val doneAt: Long? = null,
)

/** A free-form note about a contact, shown by the optional in-call notes bubble. */
@Entity(tableName = "notes", indices = [Index("key")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canon: String,
    val key: String,
    val number: String,
    val name: String?,
    val text: String,
    val createdAt: Long,
)

@Dao
interface RecalDao {
    @Insert suspend fun insert(reminder: Reminder): Long
    @Insert suspend fun insert(note: Note): Long

    @Query("SELECT * FROM reminders WHERE doneAt IS NULL ORDER BY createdAt DESC")
    fun pending(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE doneAt IS NOT NULL ORDER BY doneAt DESC LIMIT 50")
    fun done(): Flow<List<Reminder>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun notes(): Flow<List<Note>>

    @Query("SELECT * FROM reminders WHERE key = :key AND doneAt IS NULL ORDER BY createdAt ASC")
    suspend fun pendingByKey(key: String): List<Reminder>

    @Query("SELECT * FROM notes WHERE key = :key ORDER BY createdAt DESC")
    suspend fun notesByKey(key: String): List<Note>

    @Query("SELECT canon FROM reminders WHERE key = :key UNION SELECT canon FROM notes WHERE key = :key")
    suspend fun canonsByKey(key: String): List<String>

    @Query("UPDATE reminders SET doneAt = :at WHERE id IN (:ids) AND doneAt IS NULL")
    suspend fun markDone(ids: List<Long>, at: Long)

    @Query("UPDATE reminders SET doneAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE reminders SET reason = :reason WHERE id = :id")
    suspend fun updateReason(id: Long, reason: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("DELETE FROM reminders WHERE doneAt IS NOT NULL")
    suspend fun clearDone()

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("UPDATE reminders SET name = :name WHERE canon = :canon AND (name IS NULL OR name != :name)")
    suspend fun renameReminders(canon: String, name: String)

    @Query("UPDATE notes SET name = :name WHERE canon = :canon AND (name IS NULL OR name != :name)")
    suspend fun renameNotes(canon: String, name: String)
}

@Database(entities = [Reminder::class, Note::class], version = 1, exportSchema = true)
abstract class RecalDb : RoomDatabase() {
    abstract fun dao(): RecalDao

    companion object {
        @Volatile private var instance: RecalDb? = null

        fun get(context: Context): RecalDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RecalDb::class.java, "recal.db")
                .build().also { instance = it }
        }
    }
}
