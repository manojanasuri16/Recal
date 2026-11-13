package app.recal

import android.provider.CallLog
import app.recal.call.CallResolver
import app.recal.call.CallRow
import app.recal.call.Outcome
import app.recal.data.Note
import app.recal.data.PhoneNumbers
import app.recal.data.RecalDao
import app.recal.data.Reminder
import app.recal.data.Repo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reminder lifecycle, driven by call-log rows exactly as Android writes them. */
class LifecycleTest {
    private val dao = FakeDao()
    private val repo = Repo(dao)
    private val sai = "+91 98765 43210"
    private val saiLocal = "09876543210"
    private val mom = "+91 91234 56789"

    private fun out(number: String, date: Long, duration: Long) = CallRow(number, CallLog.Calls.OUTGOING_TYPE, date, duration)
    private fun inc(number: String, date: Long, duration: Long) = CallRow(number, CallLog.Calls.INCOMING_TYPE, date, duration)
    private fun missed(number: String, date: Long) = CallRow(number, CallLog.Calls.MISSED_TYPE, date, 0)
    private fun rejected(number: String, date: Long) = CallRow(number, CallLog.Calls.REJECTED_TYPE, date, 0)
    private fun resolve(vararg rows: CallRow, why: Boolean = true) =
        runBlocking { CallResolver.apply(rows.toList(), repo, why) { null } }
    private fun pending(number: String) = runBlocking { repo.pendingFor(number) }

    @Test fun unanswered_outgoing_asks_why() {
        val o = resolve(out(sai, 1_000, 0))
        assertTrue(o is Outcome.AskWhy)
        assertEquals(sai, (o as Outcome.AskWhy).number)
    }

    @Test fun answered_outgoing_with_nothing_pending_is_silent() {
        assertEquals(Outcome.Nothing, resolve(out(sai, 1_000, 42)))
    }

    @Test fun answered_callback_consumes_the_reminder() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        val o = resolve(inc(saiLocal, 5_000, 63))
        assertEquals(Outcome.Cleared(1), o)
        assertTrue(pending(sai).isEmpty())
        assertEquals(1, dao.reminders.count { it.doneAt != null })
    }

    @Test fun missed_or_rejected_callback_keeps_the_reminder() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        assertEquals(Outcome.Nothing, resolve(missed(sai, 5_000)))
        assertEquals(Outcome.Nothing, resolve(rejected(sai, 6_000)))
        // Picked up but dropped before a second elapsed: Android logs a duration of zero.
        assertEquals(Outcome.Nothing, resolve(inc(sai, 7_000, 0)))
        assertEquals(1, pending(sai).size)
    }

    @Test fun multiple_pending_reminders_are_all_consumed_together() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        repo.addReminder(saiLocal, null, "Return the charger", at = 2_000)
        repo.addReminder(mom, null, "Doctor appointment", at = 2_500)
        assertEquals(2, pending(sai).size)
        assertEquals(Outcome.Cleared(2), resolve(inc(sai, 5_000, 120)))
        assertTrue(pending(sai).isEmpty())
        assertEquals("someone else's reminder is untouched", 1, pending(mom).size)
    }

    @Test fun a_fresh_unanswered_call_after_consumption_starts_a_new_reminder() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        resolve(inc(sai, 5_000, 30))
        val o = resolve(out(sai, 9_000, 0))
        assertTrue(o is Outcome.AskWhy)
        assertTrue("consumed reminders never come back", (o as Outcome.AskWhy).existing.isEmpty())
        repo.addReminder(sai, null, "Forgot to mention the keys", at = 9_500)
        assertEquals(listOf("Forgot to mention the keys"), pending(sai).map { it.reason })
    }

    @Test fun second_unanswered_call_lists_what_is_already_waiting() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        val o = resolve(out(sai, 3_000, 0)) as Outcome.AskWhy
        assertEquals(listOf("Ask about Friday"), o.existing.map { it.reason })
    }

    @Test fun reminder_written_during_the_callback_is_not_consumed_by_it() = runBlocking {
        repo.addReminder(sai, null, "Old", at = 1_000)
        repo.addReminder(sai, null, "Added mid-call", at = 6_000)
        resolve(inc(sai, 5_000, 300))
        assertEquals(listOf("Added mid-call"), pending(sai).map { it.reason })
    }

    @Test fun reaching_them_yourself_asks_before_clearing() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        val o = resolve(out(sai, 4_000, 95))
        assertTrue(o is Outcome.AskCovered)
        assertEquals("nothing is cleared without the user saying so", 1, pending(sai).size)
    }

    @Test fun no_answer_then_immediate_success_does_not_ask_why() = runBlocking {
        assertEquals(Outcome.Nothing, resolve(out(sai, 1_000, 0), out(sai, 2_000, 60)))
        assertEquals(Outcome.Nothing, resolve(out(sai, 1_000, 0), inc(sai, 2_000, 60)))
    }

    @Test fun service_codes_and_hidden_numbers_are_ignored() {
        assertEquals(Outcome.Nothing, resolve(out("*123#", 1_000, 0)))
        assertEquals(Outcome.Nothing, resolve(out("", 1_000, 0)))
        assertEquals(Outcome.Nothing, resolve(inc("", 1_000, 50)))
    }

    @Test fun switched_off_never_asks_but_still_clears() = runBlocking {
        repo.addReminder(sai, null, "Ask about Friday", at = 1_000)
        assertEquals(Outcome.Nothing, resolve(out(mom, 2_000, 0), why = false))
        assertEquals(Outcome.Cleared(1), resolve(inc(sai, 5_000, 20), why = false))
    }

    /** In-memory stand-in for Room with the same query semantics. */
    private class FakeDao : RecalDao {
        val reminders = ArrayList<Reminder>()
        val notes = ArrayList<Note>()
        private var nextId = 1L
        override suspend fun insert(reminder: Reminder): Long { val id = nextId++; reminders += reminder.copy(id = id); return id }
        override suspend fun insert(note: Note): Long { val id = nextId++; notes += note.copy(id = id); return id }
        override fun pending(): Flow<List<Reminder>> = flowOf(reminders.filter { it.doneAt == null })
        override fun done(): Flow<List<Reminder>> = flowOf(reminders.filter { it.doneAt != null })
        override fun notes(): Flow<List<Note>> = flowOf(notes)
        override suspend fun pendingByKey(key: String) = reminders.filter { it.key == key && it.doneAt == null }.sortedBy { it.createdAt }
        override suspend fun notesByKey(key: String) = notes.filter { it.key == key }
        override suspend fun canonsByKey(key: String) = (reminders.filter { it.key == key }.map { it.canon } + notes.filter { it.key == key }.map { it.canon }).distinct()
        override suspend fun markDone(ids: List<Long>, at: Long) { reminders.replaceAll { if (it.id in ids && it.doneAt == null) it.copy(doneAt = at) else it } }
        override suspend fun restore(id: Long) { reminders.replaceAll { if (it.id == id) it.copy(doneAt = null) else it } }
        override suspend fun updateReason(id: Long, reason: String) { reminders.replaceAll { if (it.id == id) it.copy(reason = reason) else it } }
        override suspend fun deleteReminder(id: Long) { reminders.removeAll { it.id == id } }
        override suspend fun clearDone() { reminders.removeAll { it.doneAt != null } }
        override suspend fun deleteNote(id: Long) { notes.removeAll { it.id == id } }
        override suspend fun renameReminders(canon: String, name: String) { reminders.replaceAll { if (it.canon == canon) it.copy(name = name) else it } }
        override suspend fun renameNotes(canon: String, name: String) { notes.replaceAll { if (it.canon == canon) it.copy(name = name) else it } }
    }

    @Test fun both_number_forms_share_one_person() = runBlocking {
        repo.addReminder(sai, null, "a", at = 1)
        repo.addReminder(saiLocal, null, "b", at = 2)
        assertEquals(1, dao.reminders.map { it.canon }.distinct().size)
        assertTrue(PhoneNumbers.same(dao.reminders[0].canon, saiLocal))
    }
}
