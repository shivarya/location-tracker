package com.shivarya.locationspeedtracker.core.sensors

import android.hardware.Sensor
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.tracking.AddonContext
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.LiveState
import com.shivarya.locationspeedtracker.core.tracking.SessionAddon
import com.shivarya.locationspeedtracker.core.tracking.Verdict
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What the Track screen / HUD show from the phone's own sensors during a session. */
data class SensorLive(
    val gLong: Float = 0f,
    val gLat: Float = 0f,
    val peakG: Float = 0f,
    val leanDeg: Float? = null,
    val maxLeanDeg: Float = 0f,
    val baroAltM: Float? = null,
    val verticalSpeedMps: Float? = null,
    val steps: Int? = null,
    val cadence: Float? = null,
)

/**
 * Records one row per second of sensor data with each session: G-force in the vehicle frame (heading from GPS
 * bearing when moving, compass otherwise), lean angle, barometric altitude and vertical speed, steps and cadence.
 * Vehicle sessions get motion sensors at ~50 Hz; foot sessions get the step counter instead (cheaper, and G-force
 * on a phone in a pocket is meaningless). The barometer runs for both when present and also feeds elevation stats.
 */
@Singleton
class SensorRecorder @Inject constructor(private val hub: SensorHub) : SessionAddon {
    private val _live = MutableStateFlow<SensorLive?>(null)
    val live: StateFlow<SensorLive?> = _live.asStateFlow()

    private val lock = Any()
    private var session: SensorSession? = null
    private var sessionId = ""
    private var stepsEnabled = false
    private var motionEnabled = false
    private var paused = false

    private var headingDeg: Double? = null
    private var declination = 0f
    private var declinationSet = false
    private val baro = BaroAltimeter()
    private val cadence = CadenceMeter()
    private var stepBase: Float? = null
    private var steps = 0
    private var hasDetector = false

    private var bucketSecond = -1L
    private var sumLong = 0f
    private var sumLat = 0f
    private var n = 0
    private var bucketPeak = 0f
    private var lean: Float? = null
    private var maxLean = 0f
    private var peakG = 0f
    private var cadenceSum = 0.0
    private var cadenceN = 0
    private var lastPublish = 0L
    private val pending = ArrayList<MetricEntity>()

    private var leanZero = 0f

    /** Zero the lean angle at the current mount position. */
    fun calibrateLean() = synchronized(lock) {
        val g = session?.readings?.gravity ?: return@synchronized
        MotionMath.lean(g)?.let { leanZero = it }
    }

    override fun onStart(ctx: AddonContext) = synchronized(lock) {
        reset(ctx.sessionId)
        motionEnabled = !ctx.activity.onFoot
        stepsEnabled = ctx.activity.onFoot && hub.canCountSteps()
        hasDetector = hub.has(Sensor.TYPE_STEP_DETECTOR)
        val set = SensorSet(motion = motionEnabled, pressure = hub.hasBarometer, steps = stepsEnabled, environment = false)
        if (!set.motion && !set.pressure && !set.steps) return@synchronized
        session = hub.open(set, SensorRate.Fast) { r, type, t -> onReading(r, type, t) }
        _live.value = SensorLive(steps = if (stepsEnabled) 0 else null)
    }

    private fun reset(id: String) {
        sessionId = id
        paused = false
        headingDeg = null
        declinationSet = false
        stepBase = null
        steps = 0
        bucketSecond = -1
        resetBucket()
        peakG = 0f
        maxLean = 0f
        cadenceSum = 0.0
        cadenceN = 0
        pending.clear()
    }

    private fun resetBucket() {
        sumLong = 0f
        sumLat = 0f
        n = 0
        bucketPeak = 0f
    }

    private fun onReading(r: SensorReadings, type: Int, t: Long) = synchronized(lock) {
        when (type) {
            Sensor.TYPE_PRESSURE -> r.pressureHpa?.let { baro.onPressure(it, t) }
            Sensor.TYPE_STEP_COUNTER -> r.stepCounter?.let { c ->
                val base = stepBase ?: c.also { stepBase = it }
                val now = (c - base).toInt()
                if (!hasDetector) repeat(min(max(now - steps, 0), 8)) { cadence.onStep(t) }
                steps = now
            }
            Sensor.TYPE_STEP_DETECTOR -> cadence.onStep(t)
            Sensor.TYPE_GRAVITY -> r.gravity?.let { g ->
                val l = MotionMath.lean(g)?.let { MotionMath.angleDelta(it, leanZero) }
                lean = l
                if (!paused && l != null) maxLean = max(maxLean, abs(l))
            }
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> if (!paused && motionEnabled) {
                val w = r.worldLinear
                val heading = headingDeg ?: r.rotation?.let { MotionMath.facingAzimuth(it) + declination }
                if (w != null && heading != null) {
                    val (gl, gt) = MotionMath.vehicleG(w[0], w[1], heading)
                    sumLong += gl
                    sumLat += gt
                    n++
                    val h = MotionMath.horizontal(w[0], w[1]) / STANDARD_GRAVITY
                    bucketPeak = max(bucketPeak, h)
                }
            }
        }
        val sec = t / 1000
        if (bucketSecond < 0) bucketSecond = sec
        if (sec > bucketSecond) {
            closeBucket(bucketSecond * 1000, t)
            bucketSecond = sec
        }
        if (t - lastPublish >= 100) {
            lastPublish = t
            _live.value = SensorLive(
                gLong = if (n > 0) sumLong / n else _live.value?.gLong ?: 0f,
                gLat = if (n > 0) sumLat / n else _live.value?.gLat ?: 0f,
                peakG = peakG,
                leanDeg = lean,
                maxLeanDeg = maxLean,
                baroAltM = baro.altitude,
                verticalSpeedMps = baro.smoothed?.let { baro.verticalSpeed },
                steps = if (stepsEnabled) steps else null,
                cadence = if (stepsEnabled) cadence.cadence(t) else null,
            )
        }
    }

    private fun closeBucket(time: Long, now: Long) {
        if (paused) {
            resetBucket()
            return
        }
        // Session "peak G" is the strongest 1-second average — a pothole or a fumbled phone is a single spike.
        if (n > 0) peakG = max(peakG, hypot(sumLong / n, sumLat / n))
        val cad = if (stepsEnabled) cadence.cadence(now) else null
        if (cad != null && cad > 20f) {
            cadenceSum += cad
            cadenceN++
        }
        pending += MetricEntity(
            sessionId = sessionId,
            time = time,
            gLong = if (motionEnabled && n > 0) sumLong / n else null,
            gLat = if (motionEnabled && n > 0) sumLat / n else null,
            gPeak = if (motionEnabled && n > 0) bucketPeak else null,
            pressureHpa = session?.readings?.pressureHpa,
            baroAltM = baro.altitude,
            verticalSpeedMps = baro.smoothed?.let { baro.verticalSpeed },
            leanDeg = if (motionEnabled) lean else null,
            cadence = cad?.takeIf { it > 0 },
            stepsTotal = if (stepsEnabled) steps else null,
        )
        resetBucket()
    }

    override fun onFix(fix: Fix, accepted: Verdict.Accepted?, live: LiveState): Unit = synchronized(lock) {
        val speed = fix.speed ?: 0.0
        headingDeg = if (speed > 3.0 && fix.bearing != null) fix.bearing else null
        if (!declinationSet) {
            declination = hub.declination(fix.lat, fix.lon, fix.altitude ?: 0.0)
            declinationSet = true
        }
        val msl = fix.altitudeMsl
        if (msl != null) baro.onGnssAltitude(msl, fix.vAcc)
    }

    override fun onPause() = synchronized(lock) { paused = true }
    override fun onResume() = synchronized(lock) { paused = false }

    override fun drainMetrics(): List<MetricEntity> = synchronized(lock) {
        if (pending.isEmpty()) emptyList() else ArrayList(pending).also { pending.clear() }
    }

    override fun altitudeOverride(): Double? = synchronized(lock) {
        if (session != null && baro.calibrated) baro.altitude?.toDouble() else null
    }

    override fun finish(session: SessionEntity): SessionEntity = synchronized(lock) {
        session.copy(
            maxG = peakG.takeIf { motionEnabled && it > 0.01f }?.toDouble() ?: session.maxG,
            steps = if (stepsEnabled) steps else session.steps,
            avgCadence = if (cadenceN > 0) cadenceSum / cadenceN else session.avgCadence,
        )
    }

    override fun onStop() = synchronized(lock) {
        session?.stop()
        session = null
        _live.value = null
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorAddonModule {
    @Binds @IntoSet abstract fun sensorRecorder(r: SensorRecorder): SessionAddon
}
