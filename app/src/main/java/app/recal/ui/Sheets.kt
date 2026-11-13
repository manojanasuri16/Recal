package app.recal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.recal.data.PhoneNumbers
import kotlinx.coroutines.launch

@Composable
fun Field(
    value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    minHeight: Int = 50, maxLines: Int = 4, readOnly: Boolean = false,
) {
    Box(
        modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(14.dp)).background(colors.fill)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = Type.body, color = colors.tertiary)
        BasicTextField(
            value = value, onValueChange = onValue, readOnly = readOnly,
            textStyle = Type.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboard, maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Add a reason (or a note) by hand, for the calls Android could not classify. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var asNote by remember { mutableStateOf(false) }
    val valid = PhoneNumbers.canon(number).length >= 3 && text.isNotBlank()
    fun close() = scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        containerColor = colors.surface, contentColor = colors.label,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Text(if (asNote) "New note" else "Why did you call?", style = Type.title, color = colors.label)
            Spacer(Modifier.height(16.dp))
            if (vm.recents.isNotEmpty()) {
                Text("RECENT UNANSWERED", style = Type.caption, color = colors.secondary)
                Row(Modifier.padding(top = 8.dp, bottom = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.recents.forEach { r ->
                        val selected = PhoneNumbers.same(r.number, number)
                        PillButton(
                            r.name ?: PhoneNumbers.pretty(r.number), onClick = { number = r.number }, height = 38.dp, style = Type.calloutStrong,
                            container = if (selected) colors.accent else colors.fill,
                            content = if (selected) colors.onAccent else colors.label,
                            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        )
                    }
                }
            }
            Field(number, { number = it.filter { c -> c.isDigit() || c in "+ -()" }.take(24) }, "Phone number",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Field(text, { text = it.take(if (asNote) 500 else 240) }, if (asNote) "Something to remember about them" else "A few words, just for you", minHeight = 84)
            if (vm.inCallNotes) {
                Row(Modifier.padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).background(colors.fill).padding(3.dp)) {
                    listOf(false to "Reason", true to "Note").forEach { (note, label) ->
                        val on = asNote == note
                        Box(
                            Modifier.weight(1f).pressable(scaleTo = 0.97f) { asNote = note }.clip(RoundedCornerShape(10.dp))
                                .background(if (on) colors.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent).padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = Type.calloutStrong, color = if (on) colors.label else colors.secondary) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            PillButton("Save", enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
                vm.add(number.trim(), text, asNote); close()
            })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun TextDialog(
    title: String, initial: String, confirm: String, destructive: String,
    onConfirm: (String) -> Unit, onDestructive: () -> Unit, onDismiss: () -> Unit, readOnly: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(18.dp)) {
            Text(title, style = Type.headline, color = colors.label, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp))
            if (readOnly) {
                Text(text, style = Type.body, color = colors.label, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 2.dp))
            } else {
                Field(text, { text = it.take(240) }, "Reason", minHeight = 84)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(destructive, onClick = onDestructive, filled = false, content = colors.destructive, height = 46.dp, style = Type.calloutStrong, modifier = Modifier.weight(1f))
                PillButton(confirm, onClick = { onConfirm(text) }, enabled = readOnly || text.isNotBlank(), height = 46.dp, style = Type.calloutStrong, modifier = Modifier.weight(1f))
            }
        }
    }
}
