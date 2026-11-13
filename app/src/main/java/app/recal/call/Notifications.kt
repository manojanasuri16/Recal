package app.recal.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.recal.MainActivity
import app.recal.R
import app.recal.data.PhoneNumbers
import app.recal.data.Reminder
import app.recal.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notifications are the quiet fallback for every overlay: they carry the same content when
 * "display over other apps" is not granted, when the phone is locked, or when an OEM blocks
 * background pop-ups.
 */
object Notifications {
    const val CH_CALL = "call"
    const val CH_PROMPT = "prompt"
    const val ID_CALL = 1
    const val ID_PROMPT = 2
    private const val KEY_REPLY = "reason"
    private const val EXTRA_NUMBER = "number"
    private const val EXTRA_NAME = "name"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CALL, "During calls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows why you called while a call is in progress."
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PROMPT, "Why you called", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks why you called after an unanswered call."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Ongoing notification for a call; doubles as the foreground-service notification. */
    fun call(context: Context, who: String, reminders: List<Reminder>): Notification {
        ensureChannels(context)
        val b = NotificationCompat.Builder(context, CH_CALL)
            .setSmallIcon(R.drawable.ic_stat_recal)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CH_CALL).setSmallIcon(R.drawable.ic_stat_recal)
                    .setContentTitle("Recal").setContentText("A reminder is waiting for this caller.").build()
            )
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (reminders.isEmpty()) {
            b.setContentTitle("Notes for $who").setContentText("Tap the bubble to view or add notes.")
        } else {
            val text = reminders.joinToString("\n") { if (reminders.size > 1) "• ${it.reason}" else it.reason }
            b.setContentTitle("You called $who about").setContentText(reminders.first().reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        return b.build()
    }

    fun postCall(context: Context, who: String, reminders: List<Reminder>) {
        if (!canPost(context)) return
        context.getSystemService(NotificationManager::class.java).notify(ID_CALL, call(context, who, reminders))
    }

    fun cancelCall(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ID_CALL)

    /** "Why did you call?" with an inline reply field, so it works without opening anything. */
    fun postPrompt(context: Context, number: String, name: String?) {
        if (!canPost(context)) return
        ensureChannels(context)
        val reply = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ReplyReceiver::class.java).putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_NAME, name),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_recal, "Add reason", reply)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Why did you call?").build())
            .setAllowGeneratedReplies(false)
            .build()
        val n = NotificationCompat.Builder(context, CH_PROMPT)
            .setSmallIcon(R.drawable.ic_stat_recal)
            .setContentTitle("${name ?: PhoneNumbers.pretty(number)} didn’t pick up")
            .setContentText("Why did you call? You’ll see it when they call back.")
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CH_PROMPT).setSmallIcon(R.drawable.ic_stat_recal)
                    .setContentTitle("Recal").setContentText("Add why you called.").build()
            )
            .setTimeoutAfter(10 * 60_000L)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .addAction(action)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_PROMPT, n)
    }

    fun cancelPrompt(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ID_PROMPT)

    class ReplyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
            val number = intent.getStringExtra(EXTRA_NUMBER)
            val name = intent.getStringExtra(EXTRA_NAME)
            val app = context.applicationContext
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (!text.isNullOrBlank() && number != null) Repo.get(app).addReminder(number, name, text)
                } finally {
                    cancelPrompt(app)
                    pending.finish()
                }
            }
        }
    }
}
