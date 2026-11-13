package app.recal.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recal.data.Note
import app.recal.data.PhoneNumbers
import app.recal.data.Reminder
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(vm: MainViewModel, onSettings: () -> Unit) {
    val waiting by vm.waiting.collectAsState()
    val notes by vm.notes.collectAsState()
    val cleared by vm.cleared.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var viewingNote by remember { mutableStateOf<Note?>(null) }
    val context = LocalContext.current
    val showNotes = vm.inCallNotes && notes.isNotEmpty()
    val people = waiting

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recal", style = Type.largeTitle, color = colors.label, modifier = Modifier.weight(1f))
                    IconButton(Icons.Rounded.Settings, "Settings", onSettings)
                }
            }
            if (!vm.perms.complete) {
                item(key = "setup") { SetupBanner(vm.perms, onSettings) }
            }
            if (people != null && people.isEmpty()) {
                item(key = "empty") { EmptyState(vm.whyICalled) }
            }
            if (!people.isNullOrEmpty()) {
                item(key = "h-waiting") { SectionHeader("Waiting for a callback") }
                items(people, key = { "p-" + it.canon }) { person ->
                    PersonCard(
                        person, Modifier.animateItem(),
                        onCall = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(person.number)))) }
                        },
                        onDone = { vm.markDone(it.id) },
                        onEdit = { editing = it },
                    )
                }
            }
            if (showNotes) {
                item(key = "h-notes") { SectionHeader("Notes") }
                items(notes, key = { "n-" + it.canon }) { person ->
                    NotesCard(person, Modifier.animateItem(), onOpen = { viewingNote = it })
                }
            }
            if (cleared.isNotEmpty()) {
                item(key = "h-cleared") {
                    SectionHeader("Cleared") {
                        Text("Delete all", style = Type.footnote, color = colors.accent,
                            modifier = Modifier.pressable { vm.clearHistory() }.padding(vertical = 6.dp, horizontal = 4.dp))
                    }
                }
                item(key = "cleared") {
                    Group {
                        cleared.take(12).forEachIndexed { i, r ->
                            if (i > 0) Divider(inset = 52)
                            ClearedRow(r, onRestore = { vm.restore(r.id) })
                        }
                    }
                }
            }
        }

        // The one primary action, within thumb reach.
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)) {
            Box(
                Modifier.pressable(scaleTo = 0.92f) { vm.loadRecents(); adding = true }
                    .size(58.dp).clip(CircleShape).background(colors.accent)
                    .semantics { contentDescription = "Add a reason" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = colors.onAccent, modifier = Modifier.size(28.dp)) }
        }
    }

    if (adding) AddSheet(vm, onDismiss = { adding = false })
    editing?.let { r ->
        TextDialog(
            title = "Edit reason", initial = r.reason, confirm = "Save", destructive = "Delete",
            onConfirm = { vm.edit(r.id, it); editing = null },
            onDestructive = { vm.delete(r.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    viewingNote?.let { n ->
        TextDialog(
            title = n.name ?: PhoneNumbers.pretty(n.number), initial = n.text, confirm = "Done", destructive = "Delete", readOnly = true,
            onConfirm = { viewingNote = null },
            onDestructive = { vm.deleteNote(n.id); viewingNote = null },
            onDismiss = { viewingNote = null },
        )
    }
}

@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).pressable(scaleTo = 0.88f, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.label, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SetupBanner(perms: Perms, onOpen: () -> Unit) {
    val (title, body) = when {
        !perms.phone -> "Recal can’t see your calls yet" to "Allow phone and call log access to start."
        !perms.canShow -> "Recal can’t show reminders yet" to "Allow the card over calls, or notifications."
        !perms.overlay -> "Showing as notifications only" to "Allow display over other apps for the card on the call screen."
        else -> "Notifications are off" to "They’re the fallback when the card can’t appear."
    }
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().pressable(scaleTo = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(20.dp)).background(colors.accentSoft).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = colors.label)
            Text(body, style = Type.footnote, color = colors.secondary)
        }
        Spacer(Modifier.width(12.dp))
        Text("Fix", style = Type.calloutStrong, color = colors.accent)
    }
}

@Composable
private fun EmptyState(enabled: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhoneCallback, null, tint = colors.secondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Nobody owes you a callback", style = Type.title, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            if (enabled) "When a call goes unanswered, Recal asks why you called and shows it when they ring back."
            else "Why I Called is turned off in Settings.",
            style = Type.callout, color = colors.secondary, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PersonCard(person: Person, modifier: Modifier, onCall: () -> Unit, onDone: (Reminder) -> Unit, onEdit: (Reminder) -> Unit) {
    Group(modifier.padding(bottom = 12.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.title, style = Type.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (person.name != null) PhoneNumbers.pretty(person.number) + " · " else "") + "called " + relativeTime(person.latest),
                    style = Type.footnote, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.size(48.dp).pressable(scaleTo = 0.88f, onClick = onCall).semantics { contentDescription = "Call ${person.title}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Phone, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        person.reminders.forEach { r ->
            androidx.compose.runtime.key(r.id) { ReminderRow(r, onDone = { onDone(r) }, onEdit = { onEdit(r) }) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A row in the manner of a to-do: the circle on the left clears it. */
@Composable
private fun ReminderRow(r: Reminder, onDone: () -> Unit, onEdit: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(if (checked) 1f else 0f, Motion.flick(), label = "check")
    val textColor by animateColorAsState(if (checked) colors.tertiary else colors.label, Motion.smooth(), label = "text")
    LaunchedEffect(checked) {
        if (checked) { delay(420); onDone() }
    }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(48.dp).pressable(scaleTo = 0.85f) { checked = !checked }
                .semantics { contentDescription = "Mark as done" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, if (checked) colors.accent else colors.tertiary, CircleShape), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(24.dp).graphicsLayer { scaleX = fill; scaleY = fill; alpha = fill.coerceIn(0f, 1f) }
                        .clip(CircleShape).background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = colors.onAccent, modifier = Modifier.size(15.dp)) }
            }
        }
        Text(
            r.reason, style = Type.body, color = textColor,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).pressable(scaleTo = 0.99f, onClick = onEdit).padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun NotesCard(person: Person, modifier: Modifier, onOpen: (Note) -> Unit) {
    Group(modifier.padding(bottom = 12.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.title, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(person.title, style = Type.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        person.notes.forEachIndexed { i, n ->
            if (i > 0) Divider(inset = 16)
            Column(Modifier.fillMaxWidth().pressable(scaleTo = 0.99f) { onOpen(n) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(n.text, style = Type.callout, color = colors.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(relativeTime(n.createdAt), style = Type.footnote, color = colors.tertiary)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ClearedRow(r: Reminder, onRestore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).pressable(scaleTo = 0.85f, onClick = onRestore).semantics { contentDescription = "Restore reminder" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = colors.secondary, modifier = Modifier.size(15.dp))
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
            Text(r.reason, style = Type.callout, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${r.name ?: PhoneNumbers.pretty(r.number)} · cleared ${relativeTime(r.doneAt ?: r.createdAt)}", style = Type.footnote, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
