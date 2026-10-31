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
import com.shivarya.locationspeedtracker.core.flight.FlightAddon
import com.shivarya.locationspeedtracker.core.units.Format
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.core.units.fmt
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val flight: FlightAddon,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                // Default importance, but silent: Android hides low-importance ("silent") notifications from the lock
                // screen by default, and the live numbers belong there. Importance can't be raised on an existing
                // channel, hence the new id (the old LOW one is deleted below).
                NotificationChannel(CHANNEL_TRACKING, context.getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_tracking_desc)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_alerts_desc)
                },
            ),
        )
        manager.deleteNotificationChannel(LEGACY_CHANNEL_TRACKING)
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
        val altitude = live.fix?.bestAltitude?.let { Format.altitude(it, units) }
        val f = flight.live.value.takeIf { a != null }
        val title = when {
            a == null -> "Starting…"
            a.paused && a.autoPaused -> "Auto-paused · ${a.activity.label}"
            a.paused -> "Paused · ${a.activity.label}"
            f != null -> "${a.activity.label} · ${f.phase.label}"
            else -> "Recording · ${a.activity.label}"
        }
        // Collapsed line (what the lock screen shows first) and the expanded detail below it.
        val text = "$speedText · $distance · $elapsed"
        val detail = buildList {
            val max = "max " + Units.speed(live.stats.maxSpeedMps, units).let { m -> "${m.value.fmt(if (f != null) 0 else 1)} ${m.unit}" }
            if (f != null) {
                if (f.takeoffAt != null) add("In the air ${Format.duration(f.airborneMsAt(now) / 1000.0)}")
                altitude?.let { add("Alt $it") }
                f.verticalSpeedMps?.let { v -> Units.verticalSpeed(v, units).let { m -> add("${if (m.value > 0) "+" else ""}${m.value.fmt(0)} ${m.unit}") } }
                add(max)
            } else {
                add(max)
                if (live.stats.movingS > 0) add("avg ${Format.speed(live.stats.avgMovingSpeedMps, units)}")
                altitude?.let { add("alt $it") }
            }
        }.joinToString(" · ")
        val builder = NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_mark)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$detail"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            // Android 16 Live Update: a status-bar chip with the current speed and the top of the lock screen
            // (ignored on older versions).
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
        const val CHANNEL_TRACKING = "tracking_live"
        /** IMPORTANCE_LOW, so hidden from the lock screen by default; deleted on start since 2.0.5. */
        private const val LEGACY_CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALERTS = "alerts"
        const val NOTIFICATION_ID = 1001
        const val RESUME_ID = 1002
    }
}
