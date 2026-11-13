package app.recal.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import app.recal.BuildConfig
import app.recal.data.Contacts
import app.recal.data.Repo
import app.recal.data.Settings
import app.recal.overlay.CallOverlay
import app.recal.overlay.PromptActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point for everything Recal does. PHONE_STATE is exempt from the implicit-broadcast
 * restrictions, so this manifest receiver wakes the app for each call without any
 * always-running service.
 *
 * With READ_CALL_LOG granted the system sends each state twice, once with the number and
 * once without, in no guaranteed order. Every branch below is therefore idempotent.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val app = context.applicationContext
        val settings = Settings(app)
        val state = CallState(app)
        val extra = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val enabled = settings.whyICalled || settings.inCallNotes

        // A call that started while Recal was on must still be closed out if it was switched off mid-call.
        if (!enabled && state.phase == CallState.IDLE) return

        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        if (BuildConfig.DEBUG) Log.d("Recal", "state=$extra hasNumber=${number != null} phase=${state.phase}")

        // If an IDLE broadcast was ever lost (reboot mid-call), don't let a dead session poison the next call.
        if (state.phase != CallState.IDLE && now - state.sessionStart > STALE_SESSION_MS) state.reset()

        when (extra) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!enabled) return
                if (state.phase == CallState.IDLE) {
                    state.pinWatermark(app)
                    state.sessionStart = now
                    state.incoming = true
                    state.number = null
                    state.phase = CallState.RINGING
                }
                if (number != null && state.phase == CallState.RINGING) announce(app, state, number, incoming = true)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!enabled) return
                if (state.phase == CallState.IDLE) {
                    state.pinWatermark(app)
                    state.sessionStart = now
                    state.incoming = false
                    state.number = null
                }
                state.phase = CallState.OFFHOOK
                // For a call-waiting second caller we keep the bubble of the first call.
                if (number != null && state.number == null) announce(app, state, number, state.incoming)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (state.phase == CallState.IDLE) return
                state.phase = CallState.IDLE
                val started = state.sessionStart
                CallOverlay.callEnded(started)
                Notifications.cancelCall(app)
                val pending = goAsync()
                scope.launch {
                    val outcome = try {
                        CallResolver.resolve(app, started)
                    } finally {
                        // Resolution is done; the receiver must not be held for the UI work below.
                        pending.finish()
                    }
                    if (BuildConfig.DEBUG) Log.d("Recal", "outcome=${outcome::class.simpleName}")
                    withContext(Dispatchers.Main) { present(app, outcome, started) }
                    if (outcome is Outcome.AskWhy) {
                        // If the banner could not come up (no overlay permission, or an OEM
                        // blocking background pop-ups), ask through a notification instead.
                        delay(1_500)
                        if (!PromptActivity.visible) Notifications.postPrompt(app, outcome.number, outcome.name)
                    }
                }
            }
        }
    }

    /** A call with a known number has started: show the bubble if there is something to show. */
    private fun announce(app: Context, state: CallState, number: String, incoming: Boolean) {
        state.number = number
        val session = state.sessionStart
        val settings = Settings(app)
        val pending = goAsync()
        scope.launch {
            try {
                val repo = Repo.get(app)
                val reminders = if (settings.whyICalled) repo.pendingFor(number) else emptyList()
                if (reminders.isEmpty() && !settings.inCallNotes) return@launch
                val name = Contacts.nameFor(app, number) ?: reminders.firstNotNullOfOrNull { it.name }
                withContext(Dispatchers.Main) {
                    // The call may have ended (or another begun) while we were reading the database.
                    val now = CallState(app)
                    if (now.phase == CallState.IDLE || now.sessionStart != session) return@withContext
                    CallOverlay.show(app, number, name, incoming, reminders, session = session)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun present(app: Context, outcome: Outcome, session: Long) {
        // Another call is already underway: never put a banner over it or touch its bubble.
        val busy = CallState(app).let { it.phase != CallState.IDLE && it.sessionStart != session }
        when (outcome) {
            is Outcome.AskWhy -> {
                if (busy) { Notifications.postPrompt(app, outcome.number, outcome.name); return }
                CallOverlay.dismiss(session)
                PromptActivity.ask(app, outcome.number, outcome.name)
            }
            is Outcome.AskCovered -> {
                if (busy) return
                CallOverlay.dismiss(session)
                PromptActivity.covered(app, outcome.number, outcome.name, outcome.pending.map { it.id })
            }
            is Outcome.Cleared -> CallOverlay.finish(cleared = outcome.count, session = session)
            Outcome.Nothing -> CallOverlay.finish(cleared = 0, session = session)
        }
    }

    private companion object {
        const val STALE_SESSION_MS = 4 * 60 * 60 * 1000L
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w("Recal", "call handling failed", e) }
        )
    }
}
