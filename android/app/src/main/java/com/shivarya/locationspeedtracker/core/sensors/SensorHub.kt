package com.shivarya.locationspeedtracker.core.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class SensorInfo(
    val type: Int,
    val typeName: String,
    val name: String,
    val vendor: String,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val wakeUp: Boolean,
)

/** Latest values of everything the dashboard and recorder use; all optional (sensor may be absent). */
data class SensorReadings(
    val accel: FloatArray? = null,
    val linear: FloatArray? = null,
    val gravity: FloatArray? = null,
    val gyro: FloatArray? = null,
    val magnetic: FloatArray? = null,
    /** Row-major device→world rotation matrix from the rotation vector. */
    val rotation: FloatArray? = null,
    val pressureHpa: Float? = null,
    val lightLux: Float? = null,
    val temperatureC: Float? = null,
    val humidity: Float? = null,
    val proximityCm: Float? = null,
    val stepCounter: Float? = null,
    val stepDetectorCount: Int = 0,
) {
    /** World-frame linear acceleration (East, North, Up) in m/s². */
    val worldLinear: FloatArray? get() = if (linear != null && rotation != null) MotionMath.toWorld(rotation, linear) else null
    val gyroDegPerS: FloatArray? get() = gyro?.let { g -> FloatArray(3) { Math.toDegrees(g[it].toDouble()).toFloat() } }
    val gyroMagnitudeDeg: Float? get() = gyroDegPerS?.let { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
}

enum class SensorRate(val delay: Int) {
    /** ~50 Hz: G-force, lean, gyro graphs. */
    Fast(SensorManager.SENSOR_DELAY_GAME),
    /** ~16 Hz: dashboard compass etc. */
    Ui(SensorManager.SENSOR_DELAY_UI),
    /** ~5 Hz: barometer and environment only. */
    Slow(SensorManager.SENSOR_DELAY_NORMAL),
}

/** Which groups a [SensorSession] listens to. */
data class SensorSet(val motion: Boolean, val pressure: Boolean, val steps: Boolean, val environment: Boolean)

@Singleton
class SensorHub @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val sm: SensorManager? = context.getSystemService(SensorManager::class.java)

    val all: List<SensorInfo> by lazy {
        sm?.getSensorList(Sensor.TYPE_ALL).orEmpty().map {
            SensorInfo(
                it.type, it.stringType.substringAfterLast('.').replace('_', ' '), it.name, it.vendor,
                it.maximumRange, it.resolution, it.power, it.minDelay, it.isWakeUpSensor,
            )
        }.sortedBy { it.typeName }
    }

    fun has(type: Int) = sm?.getDefaultSensor(type) != null
    val hasBarometer get() = has(Sensor.TYPE_PRESSURE)
    val hasStepCounter get() = has(Sensor.TYPE_STEP_COUNTER) || has(Sensor.TYPE_STEP_DETECTOR)

    fun canCountSteps(): Boolean = hasStepCounter && (
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        )

    /** Magnetic declination at a position, to turn magnetic compass headings into true ones. */
    fun declination(lat: Double, lon: Double, altM: Double): Float =
        GeomagneticField(lat.toFloat(), lon.toFloat(), altM.toFloat(), System.currentTimeMillis()).declination

    /**
     * Start listening. [onEvent] runs on a dedicated sensor thread with the merged latest readings and the event
     * that changed them (timestamps converted to wall-clock millis). Call [SensorSession.stop] to release.
     */
    fun open(set: SensorSet, rate: SensorRate, onEvent: (SensorReadings, Int, Long) -> Unit): SensorSession =
        SensorSession(sm, set, rate, if (set.steps) canCountSteps() else false, onEvent)
}

class SensorSession internal constructor(
    private val sm: SensorManager?,
    set: SensorSet,
    rate: SensorRate,
    stepsAllowed: Boolean,
    private val onEvent: (SensorReadings, Int, Long) -> Unit,
) : SensorEventListener {
    private val thread = HandlerThread("sensors").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile var readings = SensorReadings(); private set
    private val bootToWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    init {
        val types = buildList {
            if (set.motion) addAll(listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR))
            if (set.pressure) add(Sensor.TYPE_PRESSURE)
            if (set.steps && stepsAllowed) addAll(listOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR))
            if (set.environment) addAll(listOf(Sensor.TYPE_LIGHT, Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_PROXIMITY))
        }
        for (t in types) {
            val sensor = sm?.getDefaultSensor(t) ?: continue
            val delay = when (t) {
                Sensor.TYPE_PRESSURE, Sensor.TYPE_LIGHT, Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY,
                Sensor.TYPE_PROXIMITY, Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> SensorManager.SENSOR_DELAY_NORMAL
                else -> rate.delay
            }
            // 2 s batching lets the sensor hub buffer while the CPU sleeps (screen off); values still arrive in order.
            sm.registerListener(this, sensor, delay, 2_000_000, handler)
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val v = e.values
        val r = readings
        readings = when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> r.copy(accel = v.copyOf(3))
            Sensor.TYPE_LINEAR_ACCELERATION -> r.copy(linear = v.copyOf(3))
            Sensor.TYPE_GRAVITY -> r.copy(gravity = v.copyOf(3))
            Sensor.TYPE_GYROSCOPE -> r.copy(gyro = v.copyOf(3))
            Sensor.TYPE_MAGNETIC_FIELD -> r.copy(magnetic = v.copyOf(3))
            Sensor.TYPE_ROTATION_VECTOR -> r.copy(rotation = FloatArray(9).also { SensorManager.getRotationMatrixFromVector(it, v) })
            Sensor.TYPE_PRESSURE -> r.copy(pressureHpa = v[0])
            Sensor.TYPE_LIGHT -> r.copy(lightLux = v[0])
            Sensor.TYPE_AMBIENT_TEMPERATURE -> r.copy(temperatureC = v[0])
            Sensor.TYPE_RELATIVE_HUMIDITY -> r.copy(humidity = v[0])
            Sensor.TYPE_PROXIMITY -> r.copy(proximityCm = v[0])
            Sensor.TYPE_STEP_COUNTER -> r.copy(stepCounter = v[0])
            Sensor.TYPE_STEP_DETECTOR -> r.copy(stepDetectorCount = r.stepDetectorCount + 1)
            else -> r
        }
        // Devices without a linear-acceleration sensor: derive it from accelerometer − gravity.
        val cur = readings
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER && sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) == null && cur.gravity != null) {
            readings = cur.copy(linear = FloatArray(3) { cur.accel!![it] - cur.gravity[it] })
        }
        onEvent(readings, e.sensor.type, bootToWall + e.timestamp / 1_000_000)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun stop() {
        sm?.unregisterListener(this)
        thread.quitSafely()
    }
}
