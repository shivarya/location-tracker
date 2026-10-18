package com.shivarya.locationspeedtracker.feature.sensors

import android.hardware.Sensor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.location.LocationPermissions
import com.shivarya.locationspeedtracker.core.location.LocationSources
import com.shivarya.locationspeedtracker.core.sensors.BaroAltimeter
import com.shivarya.locationspeedtracker.core.sensors.CadenceMeter
import com.shivarya.locationspeedtracker.core.sensors.MotionMath
import com.shivarya.locationspeedtracker.core.sensors.SensorHub
import com.shivarya.locationspeedtracker.core.sensors.SensorInfo
import com.shivarya.locationspeedtracker.core.sensors.SensorRate
import com.shivarya.locationspeedtracker.core.sensors.SensorReadings
import com.shivarya.locationspeedtracker.core.sensors.SensorSession
import com.shivarya.locationspeedtracker.core.sensors.SensorSet
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

data class GPoint(val long: Float, val lat: Float)

data class SensorsUi(
    val readings: SensorReadings = SensorReadings(),
    val gNow: GPoint = GPoint(0f, 0f),
    val trail: List<GPoint> = emptyList(),
    val maxAccel: Float = 0f,
    val maxBrake: Float = 0f,
    val maxLeft: Float = 0f,
    val maxRight: Float = 0f,
    /** True-north heading the phone faces (top edge flat, camera upright). */
    val headingDeg: Double? = null,
    val pitch: Float? = null,
    val roll: Float? = null,
    val lean: Float? = null,
    val baroAltM: Float? = null,
    val verticalSpeed: Float? = null,
    val gpsAltM: Double? = null,
    val stepsSinceOpen: Int? = null,
    val cadence: Float? = null,
    val stepsAvailable: Boolean = false,
    val stepsPermitted: Boolean = false,
    val headingFromGps: Boolean = false,
)

@HiltViewModel
class SensorsViewModel @Inject constructor(
    @param:ApplicationContext private val context: android.content.Context,
    private val hub: SensorHub,
    private val sources: LocationSources,
    private val engine: TrackingEngine,
    settings: SettingsRepository,
) : ViewModel() {
    val all: List<SensorInfo> get() = hub.all
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _ui = MutableStateFlow(SensorsUi())
    val ui: StateFlow<SensorsUi> = _ui.asStateFlow()

    private var session: SensorSession? = null
    private var jobs: Job? = null
    @Volatile private var fix: Fix? = null
    private val baro = BaroAltimeter()
    private val cadence = CadenceMeter()
    private var stepBase: Float? = null
    private var leanZero = 0f
    private var declination = 0f
    private val trail = ArrayDeque<GPoint>()
    private var peaks = FloatArray(4)

    fun start() {
        if (session != null) return
        val stepsOk = hub.canCountSteps()
        session = hub.open(SensorSet(motion = true, pressure = true, steps = true, environment = true), SensorRate.Ui) { r, type, t ->
            when (type) {
                Sensor.TYPE_PRESSURE -> r.pressureHpa?.let { baro.onPressure(it, t) }
                Sensor.TYPE_STEP_DETECTOR -> cadence.onStep(t)
                Sensor.TYPE_STEP_COUNTER -> if (stepBase == null) stepBase = r.stepCounter
            }
        }
        jobs = viewModelScope.launch {
            launch {
                if (LocationPermissions.hasAny(context)) {
                    sources.fused.updates(2000).catch { }.collect { f ->
                        fix = f
                        declination = hub.declination(f.lat, f.lon, f.altitude ?: 0.0)
                        f.altitudeMsl?.let { baro.onGnssAltitude(it, f.vAcc) }
                    }
                }
            }
            while (isActive) {
                publish(stepsOk)
                delay(100)
            }
        }
    }

    fun stop() {
        jobs?.cancel()
        jobs = null
        session?.stop()
        session = null
    }

    fun resetPeaks() {
        peaks = FloatArray(4)
        trail.clear()
    }

    fun zeroLean() {
        session?.readings?.gravity?.let { leanZero = MotionMath.lean(it) }
    }

    private fun publish(stepsOk: Boolean) {
        val r = session?.readings ?: return
        val f = engine.state.value.fix.takeIf { engine.state.value.active != null } ?: fix
        val gpsHeading = f?.bearing?.takeIf { (f.speed ?: 0.0) > 3.0 }
        val facing = r.rotation?.let { (MotionMath.facingAzimuth(it) + declination + 360) % 360 }
        val heading = gpsHeading ?: facing
        val g = r.worldLinear?.let { w -> heading?.let { MotionMath.vehicleG(w[0], w[1], it) } }?.let { (a, b) -> GPoint(a, b) }
            ?: GPoint(0f, 0f)
        trail.addLast(g)
        while (trail.size > 30) trail.removeFirst()
        peaks[0] = max(peaks[0], g.long)
        peaks[1] = max(peaks[1], -g.long)
        peaks[2] = max(peaks[2], -g.lat)
        peaks[3] = max(peaks[3], g.lat)
        val tilt = r.gravity?.let { MotionMath.tilt(it) }
        _ui.value = SensorsUi(
            readings = r,
            gNow = g,
            trail = trail.toList(),
            maxAccel = peaks[0],
            maxBrake = peaks[1],
            maxLeft = peaks[2],
            maxRight = peaks[3],
            headingDeg = facing,
            pitch = tilt?.first,
            roll = tilt?.second,
            lean = r.gravity?.let { MotionMath.lean(it) - leanZero },
            baroAltM = baro.altitude,
            verticalSpeed = baro.smoothed?.let { baro.verticalSpeed },
            gpsAltM = f?.altitudeMsl ?: f?.altitude,
            stepsSinceOpen = r.stepCounter?.let { c -> stepBase?.let { (c - it).toInt() } },
            cadence = cadence.cadence(System.currentTimeMillis()).takeIf { it > 0 },
            stepsAvailable = hub.hasStepCounter,
            stepsPermitted = stepsOk,
            headingFromGps = gpsHeading != null,
        )
    }

    override fun onCleared() = stop()
}
