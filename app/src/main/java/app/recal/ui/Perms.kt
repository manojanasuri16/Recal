package app.recal.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class Perms(
    val phone: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val contacts: Boolean,
    val batteryExempt: Boolean,
) {
    /** Without these two nothing can be detected at all. */
    val canDetect get() = phone
    /** Some way of getting in front of the user. */
    val canShow get() = overlay || notifications
    val ready get() = canDetect && canShow
    val complete get() = phone && overlay && notifications

    companion object {
        val PHONE = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)

        fun read(context: Context): Perms {
            fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            return Perms(
                phone = PHONE.all(::has),
                overlay = Settings.canDrawOverlays(context),
                notifications = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS),
                contacts = has(Manifest.permission.READ_CONTACTS),
                batteryExempt = context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName),
            )
        }

        fun overlayIntent(context: Context) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

        fun appInfoIntent(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

        fun batteryIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
