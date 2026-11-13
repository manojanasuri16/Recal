package app.recal.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.recal.call.CallLogReader
import app.recal.call.CallRow
import app.recal.call.CallState
import app.recal.data.Contacts
import app.recal.data.Note
import app.recal.data.PhoneNumbers
import app.recal.data.Reminder
import app.recal.data.Repo
import app.recal.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything pending for one person, newest activity first. */
data class Person(val canon: String, val number: String, val name: String?, val reminders: List<Reminder>, val notes: List<Note>) {
    val title get() = name ?: PhoneNumbers.pretty(number)
    val latest get() = maxOf(reminders.maxOfOrNull { it.createdAt } ?: 0L, notes.maxOfOrNull { it.createdAt } ?: 0L)
}

data class Recent(val number: String, val name: String?, val date: Long)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo.get(app)
    private val settings = Settings(app)

    var whyICalled by mutableStateOf(settings.whyICalled); private set
    var inCallNotes by mutableStateOf(settings.inCallNotes); private set
    var onboarded by mutableStateOf(settings.onboarded); private set
    var lockDetails by mutableStateOf(settings.lockScreenDetails); private set
    var themeChoice by mutableStateOf(settings.theme); private set
    var perms by mutableStateOf(Perms.read(app)); private set
    var recents by mutableStateOf<List<Recent>>(emptyList()); private set
    /** Whether the runtime dialog has been shown once; after a second refusal Android stops showing it. */
    var askedPhone = false
    var askedNotifications = false

    val waiting = repo.pending.map { list ->
        list.groupBy { it.canon }.map { (canon, rs) ->
            Person(canon, rs.first().number, rs.firstNotNullOfOrNull { it.name }, rs.sortedBy { it.createdAt }, emptyList())
        }.sortedByDescending { it.latest }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notes = repo.notes.map { list ->
        list.groupBy { it.canon }.map { (canon, ns) ->
            Person(canon, ns.first().number, ns.firstNotNullOfOrNull { it.name }, emptyList(), ns)
        }.sortedByDescending { it.latest }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cleared = repo.done.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refreshPerms() {
        perms = Perms.read(getApplication())
        // Start watching the call log from the moment detection becomes possible.
        if (perms.phone) CallState(getApplication()).pinWatermark(getApplication())
    }

    fun setWhy(on: Boolean) { settings.whyICalled = on; whyICalled = on }
    fun setNotes(on: Boolean) { settings.inCallNotes = on; inCallNotes = on }
    fun chooseLockDetails(on: Boolean) { settings.lockScreenDetails = on; lockDetails = on }
    fun chooseTheme(value: String) { settings.theme = value; themeChoice = value }
    fun finishOnboarding() { settings.onboarded = true; onboarded = true }

    fun loadRecents() = viewModelScope.launch {
        recents = withContext(Dispatchers.IO) {
            CallLogReader.recentUnanswered(getApplication()).map { row: CallRow ->
                Recent(row.number, Contacts.nameFor(getApplication(), row.number), row.date)
            }
        }
    }

    fun add(number: String, reason: String, asNote: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val name = Contacts.nameFor(getApplication(), number)
        if (asNote) repo.addNote(number, name, reason) else repo.addReminder(number, name, reason)
    }

    fun markDone(id: Long) = viewModelScope.launch { repo.markDone(listOf(id)) }
    fun restore(id: Long) = viewModelScope.launch { repo.restore(id) }
    fun edit(id: Long, reason: String) = viewModelScope.launch { repo.updateReason(id, reason) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteReminder(id) }
    fun deleteNote(id: Long) = viewModelScope.launch { repo.deleteNote(id) }
    fun clearHistory() = viewModelScope.launch { repo.clearDone() }
}
