package app.recal.call

import android.content.Context

/**
 * The little that must survive between PHONE_STATE broadcasts. It is persisted because the
 * process may be killed while a call is in progress and restarted for the next broadcast.
 */
class CallState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("call", Context.MODE_PRIVATE)

    var phase: Int
        get() = prefs.getInt("phase", IDLE)
        set(v) = prefs.edit().putInt("phase", v).apply()

    var sessionStart: Long
        get() = prefs.getLong("start", 0L)
        set(v) = prefs.edit().putLong("start", v).apply()

    var number: String?
        get() = prefs.getString("number", null)
        set(v) = prefs.edit().putString("number", v).apply()

    var incoming: Boolean
        get() = prefs.getBoolean("incoming", false)
        set(v) = prefs.edit().putBoolean("incoming", v).apply()

    /** Call-log rows up to this id have been handled. -1 means "not pinned yet". */
    var watermark: Long
        get() = prefs.getLong("watermark_id", -1L)
        set(v) = prefs.edit().putLong("watermark_id", v).apply()

    /**
     * Pins the watermark to the newest existing row the first time Recal can read the call log, so
     * history from before it was installed is never acted upon.
     */
    fun pinWatermark(context: Context): Long {
        val current = watermark
        if (current >= 0) return current
        val latest = CallLogReader.latestId(context)
        if (latest >= 0) watermark = latest
        return latest
    }

    /** A new session starts clean, even if the previous one's IDLE broadcast was never seen. */
    fun reset() {
        phase = IDLE
        number = null
    }

    companion object {
        const val IDLE = 0
        const val RINGING = 1
        const val OFFHOOK = 2
    }
}
