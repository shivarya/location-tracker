package com.shivarya.locationspeedtracker.core.flight

import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.AddonContext
import com.shivarya.locationspeedtracker.core.tracking.Fix
import com.shivarya.locationspeedtracker.core.tracking.LiveState
import com.shivarya.locationspeedtracker.core.tracking.SessionAddon
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
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

/** Live [FlightLog] for Flight sessions. The saved session's summary is rebuilt from its points, so nothing is stored. */
@Singleton
class FlightAddon @Inject constructor() : SessionAddon {
    private val _live = MutableStateFlow<FlightSummary?>(null)
    val live: StateFlow<FlightSummary?> = _live.asStateFlow()

    private var log: FlightLog? = null
    private var seeded = false

    override fun onStart(ctx: AddonContext) {
        log = if (ctx.activity == ActivityType.Flight) FlightLog() else null
        seeded = false
        _live.value = null
    }

    override fun onFix(fix: Fix, accepted: Verdict.Accepted?, live: LiveState) {
        val l = log ?: return
        if (accepted == null) return
        if (!seeded) {
            // After a process restart the session resumes mid-flight: replay what was already recorded.
            seeded = true
            val track = live.track
            (track.chunks.flatten() + track.tail).dropLast(1).forEach(l::add)
        }
        l.add(TrackSample(fix.time, fix.lat, fix.lon, accepted.speedMps, fix.bestAltitude, 0, live.stats.distanceM))
        _live.value = l.summary()
    }

    override fun onStop() {
        log = null
        _live.value = null
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FlightAddonModule {
    @Binds @IntoSet abstract fun flightAddon(a: FlightAddon): SessionAddon
}
