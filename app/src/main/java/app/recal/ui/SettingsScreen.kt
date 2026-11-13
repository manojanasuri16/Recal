package app.recal.ui

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.recal.BuildConfig
import app.recal.data.Reminder
import app.recal.overlay.CallOverlay
import app.recal.overlay.PromptActivity

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 4.dp)) { IconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack) }
        Text("Settings", style = Type.largeTitle, color = colors.label, modifier = Modifier.padding(start = 20.dp, top = 4.dp))

        SectionHeader("Features")
        Group {
            ToggleRow("Why I Called", "After an unanswered call, ask why you called and show it when they call back.", vm.whyICalled, vm::setWhy)
            Divider(inset = 16)
            ToggleRow("Show reasons on lock screen", "Turn off to keep your reasons hidden until the phone is unlocked.", vm.lockDetails, vm::chooseLockDetails)
            Divider(inset = 16)
            ToggleRow("In-call Notes", "A small bubble during calls to read and add notes about the person.", vm.inCallNotes, vm::setNotes)
        }

        SectionHeader("Appearance")
        Group { ThemePicker(vm.themeChoice, vm::chooseTheme) }

        SectionHeader("Permissions")
        PermissionList(vm)

        SectionHeader("Reliability")
        Group {
            LinkRow(
                "Battery optimisation",
                if (vm.perms.batteryExempt) "Not optimised. Recal can always wake for calls."
                else "Set Recal to “Not optimised” if cards stop appearing.",
            ) { runCatching { context.startActivity(Perms.batteryIntent()) } }
            Divider(inset = 16)
            LinkRow("App info", oemHint()) { runCatching { context.startActivity(Perms.appInfoIntent(context)) } }
        }

        SectionHeader("Try it")
        Group {
            LinkRow("Preview the question", "What you see after an unanswered call. Nothing is saved.") {
                PromptActivity.ask(context, "+1 555 010 0199", "Alex", demo = true)
            }
            Divider(inset = 16)
            LinkRow(
                "Preview a callback",
                if (vm.perms.overlay) "The card that appears while they ring. It leaves after a few seconds."
                else "Needs “Display over other apps”.",
            ) {
                if (vm.perms.overlay) {
                    val now = System.currentTimeMillis()
                    CallOverlay.show(
                        context, "+1 555 010 0199", "Alex", incoming = true, demo = true,
                        reminders = listOf(Reminder(id = -1, canon = "15550100199", key = "0100199", number = "+1 555 010 0199", name = "Alex", reason = "Ask about Friday’s dinner plan", createdAt = now - 2 * 3_600_000L)),
                    )
                    CallOverlay.current?.autoDismiss(6_000)
                }
            }
        }

        SectionHeader("Good to know")
        Group {
            Column(Modifier.padding(16.dp)) {
                Fact("Everything stays on this phone. Recal has no internet permission.")
                Fact("Android only reveals whether a call connected after it ends, so the question appears a moment after you hang up.")
                Fact("If voicemail answers, Android counts the call as connected and Recal stays quiet. Add the reason with the + button instead.")
                Fact("The reminder also shows over the lock screen while the phone rings. Typing a note on a locked phone may need you to unlock first.")
                Fact("WhatsApp and other internet calls are invisible to Recal.", last = true)
            }
        }
        Text(
            "Recal ${BuildConfig.VERSION_NAME}", style = Type.footnote, color = colors.tertiary,
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
        )
    }
}

private fun oemHint(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> "On Xiaomi: turn on Autostart and “Display pop-up windows while running in the background”."
    "samsung" -> "On Samsung: set Battery to Unrestricted and keep Recal out of Sleeping apps."
    "oneplus", "oppo", "realme", "vivo" -> "Allow auto-launch and background activity for Recal."
    "huawei", "honor" -> "Set App launch to Manage manually and allow all three options."
    else -> "Background and pop-up settings for Recal."
}

@Composable
private fun Fact(text: String, last: Boolean = false) {
    Row(Modifier.padding(bottom = if (last) 0.dp else 10.dp)) {
        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(colors.tertiary))
        Spacer(Modifier.width(12.dp))
        Text(text, style = Type.callout, color = colors.secondary)
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 1f) { onChange(!checked) }
            .semantics { role = Role.Switch; stateDescription = if (checked) "On" else "Off" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = colors.label)
            Text(body, style = Type.footnote, color = colors.secondary)
        }
        Spacer(Modifier.width(14.dp))
        Switch(checked)
    }
}

/** A plain two-state switch whose knob springs between ends. */
@Composable
private fun Switch(checked: Boolean) {
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, Motion.smooth(), label = "knob")
    val track by animateColorAsState(if (checked) colors.accent else colors.fill, Motion.smooth(), label = "track")
    Box(Modifier.size(width = 52.dp, height = 32.dp).clip(CircleShape).background(track)) {
        Box(Modifier.offset(x = x, y = 2.dp).size(28.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(scaleTo = 0.99f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = colors.label)
            Text(body, style = Type.footnote, color = colors.secondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiary, modifier = Modifier.size(22.dp))
    }
}



/** Three-way segmented control; the selected thumb is the raised surface. */
@Composable
private fun ThemePicker(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(colors.fill).padding(3.dp)) {
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
            val on = selected == key
            Box(
                Modifier.weight(1f).pressable(scaleTo = 0.97f) { onSelect(key) }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(if (on) colors.surfaceRaised else Color.Transparent)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Type.calloutStrong, color = if (on) colors.label else colors.secondary) }
        }
    }
}
