package com.capecter.atlayaswitch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Periodischer Hintergrund-Update-Check (siehe SettingsActivity, Schalter
 * "Im Hintergrund regelmäßig prüfen") - läuft auch wenn die App geschlossen ist, im
 * Unterschied zum bisherigen Vordergrund-Check (checkForUpdates() in SettingsActivity,
 * nur beim manuellen Klick oder beim Öffnen der Einstellungen). Nutzt bewusst dieselbe
 * UpdateChecker-Logik/denselben Feed statt einer eigenen Kopie.
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "update_check_background"
        const val NOTIFICATION_CHANNEL_ID = "update_available"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val result = suspendCancellableCoroutine<UpdateChecker.UpdateResult?> { continuation ->
            UpdateChecker.check(
                applicationContext,
                onResult = { if (continuation.isActive) continuation.resume(it) },
                onError = { if (continuation.isActive) continuation.resume(null) }
            )
        } ?: return Result.retry()

        if (result.updateAvailable) {
            showUpdateNotification(result.latestVersion, result.downloadUrl)
        }
        return Result.success()
    }

    private fun showUpdateNotification(latestVersion: String, downloadUrl: String) {
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.update_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        // Ab Android 13 (API 33) muss die Berechtigung zur Laufzeit erteilt sein - der
        // Schalter in SettingsActivity fragt sie beim Aktivieren ab, aber sie kann jederzeit
        // in den System-Einstellungen wieder entzogen worden sein.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, latestVersion))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
