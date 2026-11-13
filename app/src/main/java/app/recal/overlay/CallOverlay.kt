package app.recal.overlay

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings as SystemSettings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.recal.call.Notifications
import app.recal.data.Note
import app.recal.data.PhoneNumbers
import app.recal.data.Reminder
import app.recal.data.Repo
import app.recal.data.Settings
import app.recal.ui.Motion
import app.recal.ui.RecalTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The bubble shown while a call is ringing or in progress. All entry points must be called
 * on the main thread.
 */
@SuppressLint("StaticFieldLeak")
object CallOverlay {
    var current: Bubble? = null
        private set
    fun canDraw(context: Context) = SystemSettings.canDrawOverlays(context)

    fun show(context: Context, number: String, name: String?, incoming: Boolean, reminders: List<Reminder>, demo: Boolean = false, session: Long = 0) {
        val app = context.applicationContext
        current?.let { if (PhoneNumbers.same(it.number, number) && !it.ended) return else it.removeNow() }
        current = null
        // With "show reasons on lock screen" off, a locked phone gets a private notification instead.
        val locked = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!demo && locked && !Settings(app).lockScreenDetails) {
            if (reminders.isNotEmpty()) Notifications.postCall(app, name ?: PhoneNumbers.pretty(number), reminders)
            return
        }
        if (!canDraw(app)) {
            // No overlay permission: the notification carries the reminder on its own.
            if (reminders.isNotEmpty()) Notifications.postCall(app, name ?: PhoneNumbers.pretty(number), reminders)
            return
        }
        val bubble = Bubble(app, number, name, incoming, reminders, demo, session) { if (current === it) current = null }
        if (!bubble.attach()) return
        current = bubble
        // Order matters on Android 15: a visible overlay is what permits the service start.
        OverlayService.start(app)
    }

    /** Telephony went idle; the verdict from the call log follows via [finish]. */
    fun callEnded(session: Long) {
        current?.takeIf { it.session == session }?.onCallEnded()
    }

    /** Acts only on the bubble of the call that just ended; a newer call's bubble is left alone. */
    fun finish(cleared: Int, session: Long) {
        current?.takeIf { it.session == session }?.finish(cleared)
    }

    fun dismiss(session: Long) {
        current?.takeIf { it.session == session }?.exit()
    }
}

/** One overlay window plus the state its Compose content reads. */
class Bubble(
    private val context: Context,
    val number: String,
    val name: String?,
    val incoming: Boolean,
    initial: List<Reminder>,
    val demo: Boolean,
    val session: Long,
    private val onGone: (Bubble) -> Unit,
) {
    val who: String = name ?: PhoneNumbers.pretty(number)
    private val wm = context.getSystemService(WindowManager::class.java)
    private val settings = Settings(context)
    private val repo = Repo.get(context)
    // AndroidUiDispatcher supplies the frame clock that Animatable needs outside a composition.
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    private val density = context.resources.displayMetrics.density

    val reminders: StateFlow<List<Reminder>> = MutableStateFlow(initial)
    val notesEnabled = settings.inCallNotes
    val notes = repo.notes.map { all -> all.filter { PhoneNumbers.same(it.canon, number) } }

    /** Reminders get the card up front; notes alone start as the small pill. */
    val expanded = mutableStateOf(initial.isNotEmpty())
    val typing = mutableStateOf(false)
    val cleared = mutableIntStateOf(0)
    /** 0 = gone, 1 = fully present. Drives the enter/exit animation. */
    val presence = Animatable(0f)
    /** Horizontal swipe progress in px while the card is being thrown away. */
    val swipeX = mutableFloatStateOf(0f)
    val cardWidthPx: Int
        get() = minOf(screenWidth() - (24 * density).toInt(), (372 * density).toInt())

    private val owner = OverlayOwner()
    private var root: Root? = null
    private var removed = false
    /** True once the call has ended and the bubble is only waiting out its farewell. */
    var ended = false
        private set
    private var y = Animatable(0f)
    private var x = Animatable(0f)
    private var restoreY: Float? = null
    private var motion: Job? = null
    private var endTimeout: Job? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        IDLE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        title = "Recal"
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun screenWidth() = context.resources.displayMetrics.widthPixels
    private fun screenHeight() = context.resources.displayMetrics.heightPixels
    private fun minY() = 28 * density
    private fun maxY() = (screenHeight() - (root?.height ?: 0) - 24 * density).coerceAtLeast(minY())

    fun attach(): Boolean {
        val view = Root(context)
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { RecalTheme { CallBubble(this@Bubble) } }
        }
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.addView(compose)
        root = view
        owner.resume()
        applyGravity()
        params.y = (settings.bubbleY.coerceIn(0.04f, 0.8f) * screenHeight()).toInt()
        return try {
            wm.addView(view, params)
            scope.launch { y.snapTo(params.y.toFloat()) }
            scope.launch { presence.animateTo(1f, Motion.smooth()) }
            true
        } catch (_: Exception) {
            owner.destroy()
            scope.cancel()
            false
        }
    }

    private fun applyGravity() {
        params.gravity = if (expanded.value) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.END
        params.x = 0
    }

    private fun push() {
        val view = root ?: return
        if (removed) return
        try {
            wm.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    fun setExpanded(value: Boolean) {
        if (expanded.value == value) return
        if (!value) setTyping(false)
        expanded.value = value
        swipeX.floatValue = 0f
        applyGravity()
        push()
    }

    /** Text entry needs a focusable window; the rest of the time the bubble never takes focus. */
    fun setTyping(value: Boolean) {
        if (typing.value == value) return
        typing.value = value
        params.flags = if (value) TYPING_FLAGS else IDLE_FLAGS
        push()
        if (value) {
            // Lift the card clear of the keyboard, and remember where it was.
            restoreY = y.value
            glideY(minY())
        } else {
            restoreY?.let { glideY(it) }
            restoreY = null
        }
    }

    private fun glideY(target: Float, velocity: Float = 0f) {
        motion?.cancel()
        motion = scope.launch {
            y.animateTo(target, if (abs(velocity) > 600f) Motion.flick() else Motion.smooth(), velocity) {
                params.y = value.toInt(); push()
            }
        }
    }

    fun addNote(text: String) {
        if (demo || text.isBlank()) return
        scope.launch(Dispatchers.IO) { repo.addNote(number, name, text) }
    }

    fun onCallEnded() {
        ended = true
        setTyping(false)
        endTimeout?.cancel()
        endTimeout = scope.launch { delay(12_000); exit() }
    }

    /** The call log has been read. Acknowledge consumed reminders briefly, then leave. */
    fun finish(clearedCount: Int) {
        endTimeout?.cancel()
        if (clearedCount > 0 && reminders.value.isNotEmpty()) {
            cleared.intValue = clearedCount
            root?.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY
            )
            scope.launch { delay(1_700); exit() }
        } else {
            exit()
        }
    }

    /** Used by the in-app preview, where no call will end to take the bubble away. */
    fun autoDismiss(afterMs: Long) {
        scope.launch { delay(afterMs); exit() }
    }

    /** Animated exit along the path it came in on. */
    fun exit() {
        if (removed) return
        motion?.cancel()
        scope.launch {
            presence.animateTo(0f, Motion.smooth())
            removeNow()
        }
    }

    fun removeNow() {
        if (removed) return
        removed = true
        try {
            root?.let { wm.removeViewImmediate(it) }
        } catch (_: Exception) {
        }
        root = null
        owner.destroy()
        scope.cancel()
        OverlayService.stop(context)
        onGone(this)
    }

    // --- Dragging -------------------------------------------------------------------------
    // Handled on the View with raw screen coordinates: the window itself moves under the
    // finger, so window-relative positions (what Compose sees) would cancel out.

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var horizontal = false

    private fun dragStart(horizontalIntent: Boolean) {
        motion?.cancel()
        horizontal = horizontalIntent && expanded.value && !typing.value
        dragStartX = x.value
        dragStartY = y.value
    }

    private fun dragBy(dx: Float, dy: Float) {
        if (horizontal) {
            val nx = dragStartX + dx
            scope.launch { x.snapTo(nx) }
            swipeX.floatValue = nx
            params.x = nx.toInt()
        } else {
            val raw = dragStartY + dy
            val lo = minY(); val hi = maxY()
            val ny = when {
                raw < lo -> lo - rubberBand(lo - raw)
                raw > hi -> hi + rubberBand(raw - hi)
                else -> raw
            }
            scope.launch { y.snapTo(ny) }
            params.y = ny.toInt()
        }
        push()
    }

    private fun dragEnd(vx: Float, vy: Float) {
        if (horizontal) {
            val projected = x.value + project(vx)
            if (abs(projected) > cardWidthPx * 0.45f) {
                // Thrown away: keep the finger's velocity and leave in that direction.
                val target = if (projected > 0) screenWidth().toFloat() else -screenWidth().toFloat()
                motion?.cancel()
                motion = scope.launch {
                    x.animateTo(target, Motion.smooth(), vx) {
                        params.x = value.toInt(); swipeX.floatValue = value; push()
                    }
                    removeNow()
                }
            } else {
                motion?.cancel()
                motion = scope.launch {
                    x.animateTo(0f, Motion.flick(), vx) {
                        params.x = value.toInt(); swipeX.floatValue = value; push()
                    }
                }
            }
        } else {
            val target = (y.value + project(vy)).coerceIn(minY(), maxY())
            if (!typing.value) settings.bubbleY = target / screenHeight()
            glideY(target, vy)
        }
    }

    /** Where a flick at [velocity] px/s would come to rest (scroll-style exponential decay). */
    private fun project(velocity: Float, rate: Float = 0.994f) = (velocity / 1000f) * rate / (1f - rate)

    /** Progressive resistance past an edge: follows less the further it is pulled. */
    private fun rubberBand(over: Float): Float {
        val d = screenHeight().toFloat()
        return (over * d * 0.55f) / (d + 0.55f * over)
    }

    @SuppressLint("ViewConstructor")
    private inner class Root(context: Context) : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null

        private fun track(ev: MotionEvent) {
            val t = tracker ?: VelocityTracker.obtain().also { tracker = it }
            val copy = MotionEvent.obtain(ev)
            copy.setLocation(ev.rawX, ev.rawY)
            t.addMovement(copy)
            copy.recycle()
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; dragging = false
                    tracker?.recycle(); tracker = null
                    track(ev)
                }
                MotionEvent.ACTION_MOVE -> {
                    track(ev)
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        dragStart(horizontalIntent = abs(dx) > abs(dy))
                        // Re-base so the element does not jump by the slop distance.
                        downX = ev.rawX; downY = ev.rawY
                        return true
                    }
                }
            }
            return dragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> { setTyping(false); return false }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    track(ev)
                    dragBy(ev.rawX - downX, ev.rawY - downY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    track(ev)
                    val t = tracker
                    t?.computeCurrentVelocity(1000)
                    dragEnd(t?.xVelocity ?: 0f, t?.yVelocity ?: 0f)
                    t?.recycle(); tracker = null
                    dragging = false
                }
            }
            return true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && typing.value) {
                if (event.action == KeyEvent.ACTION_UP) setTyping(false)
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        /**
         * FLAG_SHOW_WHEN_LOCKED is deprecated for activities but is still what lets a window draw over
         * the keyguard: without it Android hides application overlays whenever the phone is locked.
         */
        @Suppress("DEPRECATION")
        const val IDLE_FLAGS = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        @Suppress("DEPRECATION")
        const val TYPING_FLAGS = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }
}

/** Minimal owner trio so a ComposeView can live in a bare WindowManager window. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        saved.performRestore(null)
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
