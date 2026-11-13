package app.recal.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Tiny synchronous settings store: the call receiver must read these without suspending. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Core feature, on by default. */
    var whyICalled: Boolean
        get() = prefs.getBoolean(WHY, true)
        set(v) = prefs.edit().putBoolean(WHY, v).apply()

    /** Optional feature, off by default. */
    var inCallNotes: Boolean
        get() = prefs.getBoolean(NOTES, false)
        set(v) = prefs.edit().putBoolean(NOTES, v).apply()

    /** Whether reminder text may appear while the phone is locked. */
    var lockScreenDetails: Boolean
        get() = prefs.getBoolean(LOCK_DETAILS, true)
        set(v) = prefs.edit().putBoolean(LOCK_DETAILS, v).apply()

    /** "system", "light" or "dark". */
    var theme: String
        get() = prefs.getString(THEME, "system") ?: "system"
        set(v) = prefs.edit().putString(THEME, v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(ONBOARDED, v).apply()

    /** Vertical resting place of the in-call bubble, as a fraction of screen height. */
    var bubbleY: Float
        get() = prefs.getFloat(BUBBLE_Y, 0.30f)
        set(v) = prefs.edit().putFloat(BUBBLE_Y, v).apply()

    var bubbleOnRight: Boolean
        get() = prefs.getBoolean(BUBBLE_RIGHT, true)
        set(v) = prefs.edit().putBoolean(BUBBLE_RIGHT, v).apply()

    /** Re-emits whenever any setting changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    private companion object {
        const val WHY = "why_i_called"
        const val NOTES = "in_call_notes"
        const val LOCK_DETAILS = "lock_details"
        const val THEME = "theme"
        const val ONBOARDED = "onboarded"
        const val BUBBLE_Y = "bubble_y"
        const val BUBBLE_RIGHT = "bubble_right"
    }
}
