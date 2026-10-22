package com.shivarya.locationspeedtracker.core.share

import org.junit.Assert.assertTrue
import org.junit.Test

class AppShareTest {
    @Test
    fun `share text leads with what's new and gives the join-test link before the store link`() {
        val text = AppShare.text()
        assertTrue(text.startsWith("Try SpeedTrack"))
        assertTrue(text.contains("New: share your route as an image"))
        assertTrue(text.contains("https://play.google.com/apps/testing/com.shivarya.locationspeedtracker"))
        assertTrue(text.contains("https://play.google.com/store/apps/details?id=com.shivarya.locationspeedtracker"))
        assertTrue(text.indexOf("apps/testing") < text.indexOf("store/apps"))
    }
}
