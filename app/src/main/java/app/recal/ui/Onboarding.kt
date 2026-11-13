package app.recal.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun Welcome(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(56.dp))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(colors.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhoneCallback, null, tint = colors.onAccent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Remember why\nyou called.", style = Type.largeTitle, color = colors.label)
            Spacer(Modifier.height(36.dp))
            Step(Icons.Rounded.PhoneMissed, "They don’t pick up", "Recal asks why you called. A few words is enough.")
            Step(Icons.Rounded.PhoneCallback, "They call back", "Your reason is on screen before you answer.")
            Step(Icons.Rounded.Check, "You talk", "The reminder clears itself. Miss the call and it stays.")
            Step(Icons.Rounded.WifiOff, "Stays on this phone", "No account, no internet access, no ads.")
            Spacer(Modifier.height(16.dp))
        }
        PillButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(bottom = 22.dp)) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = Type.headline, color = colors.label)
            Text(body, style = Type.callout, color = colors.secondary)
        }
    }
}

@Composable
fun SetupScreen(vm: MainViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(56.dp))
            Text("Three permissions", style = Type.largeTitle, color = colors.label, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Recal only works with what Android lets it see. Each of these is used on this phone and nowhere else.",
                style = Type.body, color = colors.secondary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            )
            PermissionList(vm)
            Text(
                if (Build.VERSION.SDK_INT >= 33)
                    "If Android says a setting is restricted, open App info for Recal, tap the menu in the corner and choose “Allow restricted settings”."
                else "You can change any of these later in Settings.",
                style = Type.footnote, color = colors.secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
        val ready = vm.perms.ready
        if (!ready) {
            Text(
                if (!vm.perms.phone) "Recal can’t notice calls without phone and call log access."
                else "Recal has no way to show reminders yet.",
                style = Type.footnote, color = colors.secondary,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 10.dp),
            )
        }
        PillButton(
            if (ready) "Done" else "Continue anyway", onClick = onDone, filled = ready,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp),
        )
    }
}

/** The grouped list of permissions; shared by first-run setup and Settings. */
@Composable
fun PermissionList(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val perms = vm.perms
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPerms() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshPerms() }
    fun open(intent: android.content.Intent) = runCatching { settings.launch(intent) }
        .onFailure { runCatching { settings.launch(Perms.appInfoIntent(context)) } }

    Group(modifier) {
        PermissionRow(
            Icons.Rounded.Phone, "Phone & call log",
            "To notice when a call goes unanswered and who is calling back.",
            granted = perms.phone,
        ) {
            val activity = context as? android.app.Activity
            val blocked = activity != null && Perms.PHONE.none { activity.shouldShowRequestPermissionRationale(it) } && vm.askedPhone
            if (blocked) open(Perms.appInfoIntent(context)) else { vm.askedPhone = true; request.launch(Perms.PHONE) }
        }
        Divider()
        PermissionRow(
            Icons.Rounded.Layers, "Display over other apps",
            "To show a small card on top of the call screen.",
            granted = perms.overlay,
        ) { open(Perms.overlayIntent(context)) }
        if (Build.VERSION.SDK_INT >= 33) {
            Divider()
            PermissionRow(
                Icons.Rounded.Notifications, "Notifications",
                "The fallback when the card can’t appear, such as on the lock screen.",
                granted = perms.notifications,
            ) {
                val activity = context as? android.app.Activity
                val blocked = activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) && vm.askedNotifications
                if (blocked) open(Perms.appInfoIntent(context))
                else { vm.askedNotifications = true; request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
            }
        }
        Divider()
        PermissionRow(
            Icons.Rounded.Contacts, "Contacts",
            "Optional. Shows names instead of numbers.",
            granted = perms.contacts,
        ) { request.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)) { content() }
}

@Composable
fun Divider(inset: Int = 60) {
    Box(Modifier.padding(start = inset.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, body: String, granted: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = colors.label)
            Text(body, style = Type.footnote, color = colors.secondary)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(colors.positive), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Allowed", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
        } else {
            PillButton("Allow", onClick = onAllow, height = 36.dp, style = Type.calloutStrong, container = colors.accentSoft, content = colors.accent,
                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp))
        }
    }
}

