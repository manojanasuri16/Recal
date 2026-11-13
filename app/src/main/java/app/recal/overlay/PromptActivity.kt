package app.recal.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recal.call.Notifications
import app.recal.data.PhoneNumbers
import app.recal.data.Reminder
import app.recal.data.Repo
import app.recal.ui.Avatar
import app.recal.ui.Motion
import app.recal.ui.PillButton
import app.recal.ui.RecalTheme
import app.recal.ui.Type
import app.recal.ui.colors
import app.recal.ui.pressable
import app.recal.ui.reduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The compact banner shown after a call. It is an Activity rather than a raw overlay window
 * so the keyboard, focus and Back all behave normally, but it is laid out as a small
 * top-anchored floating window: nothing is dimmed and touches outside it reach the app below.
 */
class PromptActivity : ComponentActivity() {

    private data class Request(val mode: String, val number: String, val name: String?, val ids: List<Long>, val demo: Boolean)

    private var request by mutableStateOf<Request?>(null)
    private var generation by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones are usually locked when a call ends. Show over the keyguard without dismissing it.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        read(intent)
        setContent {
            RecalTheme {
                val r = request
                if (r != null) {
                    androidx.compose.runtime.key(generation) {
                        Prompt(r, onGone = { close() })
                    }
                }
            }
        }
        shapeWindow()
    }

    /** A floating window resets its own layout when content is installed, so size it afterwards. */
    private fun shapeWindow() {
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.TOP)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        val number = intent.getStringExtra(X_NUMBER)
        if (number == null) { close(); return }
        request = Request(
            mode = intent.getStringExtra(X_MODE) ?: ASK,
            number = number,
            name = intent.getStringExtra(X_NAME),
            ids = intent.getLongArrayExtra(X_IDS)?.toList().orEmpty(),
            demo = intent.getBooleanExtra(X_DEMO, false),
        )
        generation++
        Notifications.cancelPrompt(this)
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() { super.onStop(); visible = false }

    private fun close() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    @Composable
    private fun Prompt(r: Request, onGone: () -> Unit) {
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        val reduce = reduceMotion()
        val repo = remember { Repo.get(applicationContext) }
        val who = r.name ?: PhoneNumbers.pretty(r.number)
        // Earlier reasons stay off a locked screen if the user asked for that.
        val hideDetails = remember {
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked &&
                !app.recal.data.Settings(applicationContext).lockScreenDetails
        }

        var heightPx by remember { mutableIntStateOf(0) }
        // Offset from the resting position; negative is up, off the top edge it came from.
        val offset = remember { Animatable(-600f) }
        val fade = remember { Animatable(0f) }
        var engaged by remember { mutableStateOf(false) }
        var done by remember { mutableStateOf<String?>(null) }
        var leaving by remember { mutableStateOf(false) }
        var text by remember { mutableStateOf("") }
        var existing by remember { mutableStateOf<List<Reminder>>(emptyList()) }
        val countdown = remember { Animatable(1f) }

        fun leave(velocity: Float = 0f) {
            if (leaving) return
            leaving = true
            scope.launch {
                if (reduce) fade.animateTo(0f, tween(160))
                else {
                    launch { fade.animateTo(0f, Motion.smooth()) }
                    offset.animateTo(-(heightPx + 80f), Motion.smooth(), velocity)
                }
                onGone()
            }
        }

        fun confirm(message: String) {
            done = message
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            )
            scope.launch { delay(1_300); leave() }
        }

        var saving by remember { mutableStateOf(false) }
        fun save() {
            val reason = text.trim()
            if (reason.isEmpty() || done != null || saving) return
            saving = true // set before the suspend call so a fast double-tap cannot insert twice
            engaged = true
            scope.launch {
                if (!r.demo) repo.addReminder(r.number, r.name, reason)
                confirm("Saved. You’ll see it when $who calls back.")
            }
        }

        BackHandler { leave() }

        LaunchedEffect(Unit) {
            existing = if (r.mode == ASK && !r.demo) repo.pendingFor(r.number) else if (r.mode == COVERED) {
                repo.pendingFor(r.number).filter { it.id in r.ids }
            } else emptyList()
            if (reduce) { offset.snapTo(0f); fade.animateTo(1f, tween(160)) }
            else {
                launch { fade.animateTo(1f, Motion.smooth()) }
                offset.animateTo(0f, Motion.smooth())
            }
        }
        // Left alone, the banner removes itself. Any interaction keeps it for good.
        LaunchedEffect(engaged) {
            if (engaged) { countdown.stop(); return@LaunchedEffect }
            countdown.animateTo(0f, tween(AUTO_DISMISS_MS, easing = LinearEasing))
            leave()
        }

        val drag = rememberDraggableState { delta ->
            engaged = true
            // Follows the finger 1:1 upward; resists progressively when pulled down.
            val next = if (offset.value + delta > 0f) offset.value + delta * 0.3f else offset.value + delta
            scope.launch { offset.snapTo(next.coerceAtMost(56f)) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 28.dp)
                .graphicsLayer { translationY = offset.value; alpha = fade.value }
                .onSizeChanged { heightPx = it.height }
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    enabled = !leaving,
                    onDragStopped = { velocity ->
                        // Decide from where the flick is heading, not from where it was released.
                        val projected = offset.value + (velocity / 1000f) * 0.994f / (1f - 0.994f)
                        if (projected < -heightPx * 0.35f) leave(velocity)
                        else scope.launch { offset.animateTo(0f, Motion.flick(), velocity) }
                    },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            FloatingSurface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), corner = 26) {
                Column {
                    AnimatedContent(
                        targetState = done,
                        transitionSpec = { fadeIn(Motion.smooth()) togetherWith fadeOut(Motion.quick()) },
                        label = "prompt",
                    ) { message ->
                        if (message != null) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.positive, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(message, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = colors.label)
                            }
                        } else {
                            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(who, size = 38.dp)
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (r.mode == ASK) "$who didn’t pick up" else "You reached $who",
                                            style = Type.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (r.mode == ASK) "Why did you call?" else "Did you cover this?",
                                            style = Type.callout, color = colors.secondary,
                                        )
                                    }
                                    Box(
                                        Modifier.size(44.dp).pressable(scaleTo = 0.85f) { leave() }
                                            .semantics { contentDescription = if (r.mode == ASK) "Skip" else "Keep for later" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Close, null, tint = colors.secondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Column(Modifier.padding(end = 8.dp)) {
                                    if (r.mode == ASK) {
                                        ReasonField(
                                            text = text,
                                            onText = { text = it.take(240); engaged = true },
                                            onFocus = { engaged = true },
                                            onSave = { save() },
                                        )
                                        if (existing.isNotEmpty() && !hideDetails) {
                                            Text(
                                                "Already waiting: " + existing.joinToString(" · ") { it.reason },
                                                style = Type.footnote, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 9.dp, start = 2.dp),
                                            )
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            existing.take(4).forEach {
                                                Text(if (hideDetails) "A reminder you saved" else "“${it.reason}”", style = Type.body.copy(fontWeight = FontWeight.Medium), color = colors.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PillButton("Keep", onClick = { leave() }, filled = false, height = 44.dp, style = Type.calloutStrong, modifier = Modifier.weight(1f))
                                            PillButton(
                                                "Yes, clear", height = 44.dp, style = Type.calloutStrong, modifier = Modifier.weight(1f),
                                                onClick = {
                                                    if (done == null) {
                                                        engaged = true
                                                        scope.launch {
                                                            if (!r.demo) repo.markDone(r.ids)
                                                            confirm("Cleared.")
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Quiet status: how long until the banner leaves by itself.
                    if (!engaged && done == null) {
                        Box(Modifier.fillMaxWidth().height(2.dp)) {
                            Box(Modifier.fillMaxWidth(countdown.value).height(2.dp).background(colors.tertiary))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ReasonField(text: String, onText: (String) -> Unit, onFocus: () -> Unit, onSave: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(15.dp)).background(colors.fill)
                .padding(start = 13.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
                if (text.isEmpty()) Text("A few words, just for you", style = Type.body, color = colors.tertiary, maxLines = 1)
                BasicTextField(
                    value = text, onValueChange = onText,
                    textStyle = Type.body.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }
                        // A physical Enter key saves, the same as the keyboard's Done action.
                        .onPreviewKeyEvent { e ->
                            if (e.key == Key.Enter && !e.isShiftPressed) { if (e.type == KeyEventType.KeyUp) onSave(); true } else false
                        },
                )
            }
            Box(
                Modifier.size(42.dp).pressable(enabled = text.isNotBlank(), scaleTo = 0.85f, onClick = onSave)
                    .semantics { contentDescription = "Save reason" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    companion object {
        private const val ASK = "ask"
        private const val COVERED = "covered"
        private const val X_MODE = "mode"
        private const val X_NUMBER = "number"
        private const val X_NAME = "name"
        private const val X_IDS = "ids"
        private const val X_DEMO = "demo"
        private const val AUTO_DISMISS_MS = 20_000

        /** True while the banner is actually on screen; lets callers fall back to a notification. */
        @Volatile var visible = false
            private set

        private fun launch(context: Context, mode: String, number: String, name: String?, ids: List<Long>, demo: Boolean) {
            try {
                context.startActivity(
                    Intent(context, PromptActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra(X_MODE, mode).putExtra(X_NUMBER, number).putExtra(X_NAME, name)
                        .putExtra(X_IDS, ids.toLongArray()).putExtra(X_DEMO, demo)
                )
            } catch (_: Exception) {
            }
        }

        fun ask(context: Context, number: String, name: String?, demo: Boolean = false) =
            launch(context, ASK, number, name, emptyList(), demo)

        fun covered(context: Context, number: String, name: String?, ids: List<Long>, demo: Boolean = false) =
            launch(context, COVERED, number, name, ids, demo)
    }
}
