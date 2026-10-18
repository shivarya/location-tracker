package com.shivarya.locationspeedtracker.core.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What Google Play services thinks you're doing; [type] null = standing still. */
data class DetectedMotion(val type: ActivityType?, val at: Long)

/**
 * Activity Recognition transitions (walking / running / cycling / in a vehicle / still). Used to suggest the right
 * activity type before starting — it needs the physical-activity permission and is skipped silently without it.
 */
@Singleton
class ActivityRecognizer @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val _detected = MutableStateFlow<DetectedMotion?>(null)
    val detected: StateFlow<DetectedMotion?> = _detected.asStateFlow()
    private var users = 0

    private val pendingIntent: PendingIntent by lazy {
        // Mutable: Play services fills in the transition result.
        PendingIntent.getBroadcast(
            context, 7, Intent(context, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
    }

    fun permitted() = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun acquire() {
        if (!permitted()) return
        if (users++ > 0) return
        val transitions = listOf(
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.RUNNING,
            DetectedActivity.WALKING, DetectedActivity.STILL,
        ).map { ActivityTransition.Builder().setActivityType(it).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build() }
        runCatching {
            ActivityRecognition.getClient(context).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent)
        }
    }

    @SuppressLint("MissingPermission")
    fun release() {
        if (users == 0) return
        if (--users > 0) return
        runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent) }
    }

    internal fun onResult(result: ActivityTransitionResult) {
        val last = result.transitionEvents.lastOrNull { it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER } ?: return
        val type = when (last.activityType) {
            DetectedActivity.IN_VEHICLE -> ActivityType.Drive
            DetectedActivity.ON_BICYCLE -> ActivityType.Cycle
            DetectedActivity.RUNNING -> ActivityType.Run
            DetectedActivity.WALKING -> ActivityType.Walk
            else -> null
        }
        _detected.value = DetectedMotion(type, System.currentTimeMillis())
    }
}

class ActivityTransitionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun recognizer(): ActivityRecognizer
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        EntryPointAccessors.fromApplication(context, Deps::class.java).recognizer().onResult(result)
    }
}
