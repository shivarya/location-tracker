package com.shivarya.locationspeedtracker.core.flight

import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.tracking.Fix

/**
 * Notices a flight being recorded as some other activity: Doppler ground speed at or above
 * [ActivityType.AIRBORNE_MPS] (≈ 450 km/h, faster than any train or car) held for [SUSTAIN_MS]. Raw fixes are fed in,
 * accepted or not, so a filter that rejects the speeds can't hide them.
 */
class AirborneDetector {
    private var since: Long? = null
    private var last: Long? = null

    /** True once the speed has been sustained; stays true after that. */
    fun offer(fix: Fix): Boolean {
        val fast = (fix.speed ?: 0.0) >= ActivityType.AIRBORNE_MPS &&
            (fix.speedAcc ?: 0.0) <= MAX_SPEED_ACC_MPS &&
            (fix.speed ?: 0.0) <= ActivityType.ABSOLUTE_MAX_MPS
        val prev = last
        last = fix.time
        if (!fast || (prev != null && fix.time - prev > MAX_GAP_MS)) {
            since = if (fast) fix.time else null
            return false
        }
        val start = since ?: fix.time.also { since = it }
        return fix.time - start >= SUSTAIN_MS
    }

    companion object {
        const val SUSTAIN_MS = 20_000L
        private const val MAX_GAP_MS = 10_000L
        private const val MAX_SPEED_ACC_MPS = 5.0
    }
}
