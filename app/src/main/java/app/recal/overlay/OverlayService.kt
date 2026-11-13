package app.recal.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.recal.call.Notifications

/**
 * Holds no logic of its own. It exists so the process that owns the call bubble is a
 * foreground process for the length of the call instead of a killable background one.
 * It runs only while a bubble is on screen.
 */
class OverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = CallOverlay.current
        if (current == null) {
            // Restarted without a bubble (process death): nothing to keep alive.
            startQuietly(Notifications.call(this, "this call", emptyList()))
            stopSelf()
            return START_NOT_STICKY
        }
        startQuietly(Notifications.call(this, current.who, current.reminders.value))
        return START_NOT_STICKY
    }

    private fun startQuietly(notification: android.app.Notification) {
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, Notifications.ID_CALL, notification, type)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        /** Must be called after the overlay window is attached (Android 15 requirement). */
        fun start(context: Context) {
            try {
                startedAt = System.currentTimeMillis()
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } catch (_: Exception) {
                // Background-start not allowed on this device right now. The visible overlay
                // still keeps the process perceptible, so the bubble carries on regardless.
            }
        }

        private var startedAt = 0L

        /** Stopping right after starting can beat startForeground() and crash; give it a moment. */
        fun stop(context: Context) {
            val app = context.applicationContext
            val wait = (startedAt + 1_500L - System.currentTimeMillis()).coerceAtLeast(0)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                app.stopService(Intent(app, OverlayService::class.java))
            }, wait)
        }
    }
}
