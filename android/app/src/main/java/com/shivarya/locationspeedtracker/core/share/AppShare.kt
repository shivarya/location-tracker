package com.shivarya.locationspeedtracker.core.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.shivarya.locationspeedtracker.R
import java.io.File

/** Recommending SpeedTrack to a friend: the logo card as the image, the pitch and both Play links as its caption. */
object AppShare {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.shivarya.locationspeedtracker"

    /**
     * While the app is in Play closed testing a friend has to join the test before the store page lets them install,
     * so both links go out (their email still has to be on the tester list in Play Console). Drop the first step once
     * the app is in production.
     */
    const val TESTING_OPT_IN_URL = "https://play.google.com/apps/testing/com.shivarya.locationspeedtracker"

    /** Lead with what's new; keep it to a few lines so it reads as a caption under the image. */
    fun text(): String = """
        |Try SpeedTrack: a GPS speedometer and route tracker for Android.
        |• New: share your route as an image, with distance, time and speed
        |• New: offline maps for a whole country, handy on flights
        |• Live speed, GPS doctor, sensors, heart rate and Health Connect, all offline, no account
        |
        |1. Join the test: $TESTING_OPT_IN_URL
        |2. Then install: $PLAY_URL
    """.trimMargin()

    fun intent(context: Context): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_SUBJECT, "Try SpeedTrack")
            .putExtra(Intent.EXTRA_TEXT, text())
        val card = cardUri(context)
        if (card != null) {
            // Chat apps send the image with the text as its caption; ClipData gives the share sheet its preview and
            // carries the read grant to whichever app is picked.
            send.setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, card)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(null, card)
        } else {
            send.setType("text/plain")
        }
        return Intent.createChooser(send, "Share SpeedTrack")
    }

    /**
     * The bundled card (res/raw/share_card.png = branding/feature-graphic.png) copied into cache/exports/, which the
     * app's FileProvider already serves. Rewritten on every share so an update's new card replaces the old one. Null
     * if the copy fails; the share then goes out as text only.
     */
    private fun cardUri(context: Context): Uri? = runCatching {
        val file = File(context.cacheDir, "exports/speedtrack-app.png")
        file.parentFile?.mkdirs()
        context.resources.openRawResource(R.raw.share_card).use { input -> file.outputStream().use { input.copyTo(it) } }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
