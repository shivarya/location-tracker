package com.shivarya.locationspeedtracker.core.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shivarya.locationspeedtracker.MainActivity
import com.shivarya.locationspeedtracker.R
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TRACKING, context.getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_tracking_desc)
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_alerts_desc)
                },
            ),
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, TrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun build(live: LiveState, units: UnitSystem, now: Long = System.currentTimeMillis()): Notification {
        val a = live.active
        val speed = live.speedMps?.let { if (it < 0.3) 0.0 else it }
        val speedText = speed?.let { Units.speed(it, units).let { m -> "${m.value.fmt(0)} ${m.unit}" } } ?: "–"
        val elapsed = a?.let { Format.duration(it.elapsedMs(now) / 1000.0) } ?: "0:00"
        val distance = Format.distance(live.stats.distanceM, units)
        val title = when {
            a == null -> "Starting…"
            a.paused && a.autoPaused -> "Auto-paused · ${a.activity.label}"
            a.paused -> "Paused · ${a.activity.label}"
            else -> "Recording · ${a.activity.label}"
        }
        val text = "$speedText · $distance · $elapsed"
        val builder = NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_mark)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            // Android 16 Live Update: a status-bar chip with the current speed (ignored on older versions).
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(if (a?.paused == true) "Paused" else speedText)
        if (a != null) {
            if (a.paused) builder.addAction(0, "Resume", action(TrackingService.ACTION_RESUME, 2))
            else builder.addAction(0, "Pause", action(TrackingService.ACTION_PAUSE, 1))
            builder.addAction(0, "Lap", action(TrackingService.ACTION_LAP, 3))
            builder.addAction(0, "Stop", action(TrackingService.ACTION_STOP, 4))
        }
        return builder.build()
    }

    fun update(notification: Notification) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** The system restarted the service but it can't use location from the background: ask the user to reopen. */
    fun postResumePrompt() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_mark)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle("Recording was interrupted")
            .setContentText("Tap to reopen SpeedTrack and continue your session.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(RESUME_ID, n)
    }

    fun cancelResumePrompt() = manager.cancel(RESUME_ID)

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALERTS = "alerts"
        const val NOTIFICATION_ID = 1001
        const val RESUME_ID = 1002
    }
}
