package app.recal.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Everything Recal stores lives in one local SQLite file; nothing leaves the device. */
class Repo(private val dao: RecalDao) {

    val pending: Flow<List<Reminder>> = dao.pending()
    val done: Flow<List<Reminder>> = dao.done()
    val notes: Flow<List<Note>> = dao.notes()

    suspend fun pendingFor(number: String?): List<Reminder> {
        if (PhoneNumbers.canon(number).isEmpty()) return emptyList()
        return dao.pendingByKey(PhoneNumbers.key(number)).filter { PhoneNumbers.same(it.canon, number) }
    }

    suspend fun notesFor(number: String?): List<Note> {
        if (PhoneNumbers.canon(number).isEmpty()) return emptyList()
        return dao.notesByKey(PhoneNumbers.key(number)).filter { PhoneNumbers.same(it.canon, number) }
    }

    /**
     * Reuse the canonical form already on file for this person so "+91 98765 43210"
     * and "098765 43210" land in the same group.
     */
    private suspend fun canonFor(number: String): String {
        val own = PhoneNumbers.canon(number)
        return dao.canonsByKey(PhoneNumbers.key(number)).firstOrNull { PhoneNumbers.same(it, own) } ?: own
    }

    suspend fun addReminder(number: String, name: String?, reason: String, at: Long = System.currentTimeMillis()): Long? {
        val text = reason.trim()
        if (text.isEmpty() || PhoneNumbers.canon(number).isEmpty()) return null
        val canon = canonFor(number)
        if (name != null) dao.renameReminders(canon, name)
        return dao.insert(
            Reminder(canon = canon, key = PhoneNumbers.key(number), number = number, name = name, reason = text, createdAt = at)
        )
    }

    suspend fun addNote(number: String, name: String?, text: String, at: Long = System.currentTimeMillis()): Long? {
        val body = text.trim()
        if (body.isEmpty() || PhoneNumbers.canon(number).isEmpty()) return null
        val canon = canonFor(number)
        if (name != null) dao.renameNotes(canon, name)
        return dao.insert(
            Note(canon = canon, key = PhoneNumbers.key(number), number = number, name = name, text = body, createdAt = at)
        )
    }

    suspend fun markDone(ids: List<Long>, at: Long = System.currentTimeMillis()) {
        if (ids.isNotEmpty()) dao.markDone(ids, at)
    }

    suspend fun restore(id: Long) = dao.restore(id)
    suspend fun updateReason(id: Long, reason: String) {
        if (reason.isNotBlank()) dao.updateReason(id, reason.trim())
    }
    suspend fun deleteReminder(id: Long) = dao.deleteReminder(id)
    suspend fun clearDone() = dao.clearDone()
    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(RecalDb.get(context).dao()).also { instance = it }
        }
    }
}
