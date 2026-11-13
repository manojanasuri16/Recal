package app.recal.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneCallback
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recal.data.Note
import app.recal.data.Reminder
import app.recal.ui.Motion
import app.recal.ui.Type
import app.recal.ui.colors
import app.recal.ui.pressable
import app.recal.ui.reduceMotion
import app.recal.ui.relativeTime
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Shared look of every floating Recal surface: one opaque material, hairline edge, soft shadow. */
@Composable
fun FloatingSurface(modifier: Modifier = Modifier, corner: Int = 24, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(corner.dp)
    Box(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.28f))
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(0.5.dp, colors.separator, shape)
    ) { content() }
}

@Composable
fun CallBubble(bubble: Bubble) {
    val presence = bubble.presence.value
    val expanded = bubble.expanded.value
    val reduce = reduceMotion()
    val widthPx = bubble.cardWidthPx
    val swipe = bubble.swipeX.floatValue
    Box(
        Modifier
            .padding(horizontal = 10.dp, vertical = 14.dp) // room for the shadow inside the window
            .graphicsLayer {
                alpha = presence * (1f - (abs(swipe) / widthPx).coerceIn(0f, 1f) * 0.7f)
                if (!reduce) {
                    val s = 0.9f + 0.1f * presence
                    scaleX = s; scaleY = s
                    // The card grows out of the edge the pill lives on.
                    transformOrigin = if (expanded) TransformOrigin(0.5f, 0f) else TransformOrigin(1f, 0.5f)
                }
            }
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                if (reduce) fadeIn() togetherWith fadeOut()
                else (fadeIn(Motion.smooth()) + scaleIn(Motion.smooth(), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f))) togetherWith
                    fadeOut(Motion.quick())
            },
            label = "bubble",
        ) { open ->
            if (open) Card(bubble, with(LocalDensity.current) { widthPx.toDp() - 20.dp })
            else Pill(bubble)
        }
    }
}

@Composable
private fun Pill(bubble: Bubble) {
    val reminders by bubble.reminders.collectAsState()
    val cleared = bubble.cleared.intValue > 0
    FloatingSurface(
        Modifier
            .pressable(scaleTo = 0.92f) { bubble.setExpanded(true) }
            .semantics { contentDescription = if (reminders.isEmpty()) "Notes for ${bubble.who}" else "Why you called ${bubble.who}" },
        corner = 26,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (cleared) colors.positive else colors.accent), contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        cleared -> Icons.Rounded.CheckCircle
                        reminders.isEmpty() -> Icons.Rounded.EditNote
                        else -> Icons.Rounded.PhoneCallback
                    },
                    contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(bubble: Bubble, width: androidx.compose.ui.unit.Dp) {
    val reminders by bubble.reminders.collectAsState()
    val notes by bubble.notes.collectAsState(initial = emptyList())
    val cleared = bubble.cleared.intValue
    FloatingSurface(Modifier.width(width)) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint by animateFloatAsState(if (cleared > 0) 1f else 0f, Motion.smooth(), label = "tint")
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (tint > 0.5f) colors.positive else colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (cleared > 0) Icons.Rounded.CheckCircle
                        else if (reminders.isEmpty()) Icons.Rounded.EditNote else Icons.Rounded.PhoneCallback,
                        null, tint = colors.onAccent, modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        cleared > 0 -> "Cleared"
                        reminders.isEmpty() -> "Notes · ${bubble.who}"
                        bubble.incoming -> "You called ${bubble.who} about"
                        else -> "Still to cover with ${bubble.who}"
                    },
                    style = Type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = if (cleared > 0) colors.positive else colors.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(44.dp).pressable(scaleTo = 0.85f) { bubble.setExpanded(false) }
                        .semantics { contentDescription = "Minimise" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.padding(end = 10.dp)) {
                if (reminders.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                        reminders.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.padding(vertical = 9.dp).fillMaxWidth().background(colors.separator).heightIn(min = 0.5.dp))
                            ReminderLine(r, struck = cleared > 0)
                        }
                    }
                }
                if (bubble.notesEnabled && cleared == 0) {
                    NotesBlock(bubble, notes, spaced = reminders.isNotEmpty())
                }
            }
        }
    }
}

@Composable
private fun ReminderLine(r: Reminder, struck: Boolean) {
    Column {
        Text(
            r.reason,
            style = Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = if (struck) colors.secondary else colors.label,
            maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
        Text(relativeTime(r.createdAt), style = Type.footnote, color = colors.tertiary, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun NotesBlock(bubble: Bubble, notes: List<Note>, spaced: Boolean) {
    Column(Modifier.padding(top = if (spaced) 12.dp else 0.dp)) {
        if (spaced) {
            Text("NOTES", style = Type.caption, color = colors.tertiary, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (notes.isNotEmpty()) {
            Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.forEach { n ->
                    Column {
                        Text(n.text, style = Type.callout, color = colors.label, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        Text(relativeTime(n.createdAt), style = Type.footnote, color = colors.tertiary)
                    }
                }
            }
            Spacer(Modifier.padding(top = 10.dp))
        }
        NoteField(bubble, empty = notes.isEmpty())
    }
}

@Composable
private fun NoteField(bubble: Bubble, empty: Boolean) {
    val typing = bubble.typing.value
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (text.isNotBlank()) bubble.addNote(text)
        text = ""
        bubble.setTyping(false)
    }
    LaunchedEffect(typing) {
        if (typing) {
            delay(120) // the window needs a frame to become focusable before it can take the keyboard
            runCatching { focus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(colors.fill)
            .then(if (!typing) Modifier.pressable(scaleTo = 0.98f) { bubble.setTyping(true) } else Modifier)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (text.isEmpty()) {
                Text(if (empty) "Add a note about ${bubble.who}" else "Add a note", style = Type.callout, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (typing) {
                BasicTextField(
                    value = text, onValueChange = { text = it.take(500) },
                    textStyle = Type.callout.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (typing) {
            Box(
                Modifier.size(40.dp).pressable(enabled = text.isNotBlank(), scaleTo = 0.85f) { submit() }
                    .semantics { contentDescription = "Save note" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, null, tint = colors.onAccent, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}
