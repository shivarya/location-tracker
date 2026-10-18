package com.shivarya.locationspeedtracker.core.location

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class CheckLevel { Ok, Info, Warn, Fail }

/** How the doctor can fix a finding: a system dialog, a settings screen, or a permission request. */
sealed interface CheckFix {
    data class Resolve(val intentSender: IntentSender) : CheckFix
    data class OpenSettings(val intent: Intent) : CheckFix
    data class RequestPermissions(val permissions: List<String>) : CheckFix
}

data class Check(
    val id: String,
    val title: String,
    val detail: String,
    val level: CheckLevel,
    val fix: CheckFix? = null,
    val fixLabel: String? = null,
)

/**
 * Everything on the device that limits how good (or how available) a fix is — and what the user can do about it.
 * Wi-Fi and Bluetooth help even when not connected: the fused provider uses their *scans* to position, which is the
 * "use Wi-Fi/Bluetooth for better accuracy" the app can actually influence.
 */
@Singleton
class LocationDoctor @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun run(online: Boolean): List<Check> = buildList {
        add(permissionCheck())
        add(locationOnCheck())
        add(accuracyModeCheck())
        add(wifiScanCheck())
        bluetoothScanCheck()?.let { add(it) }
        batterySaverCheck()?.let { add(it) }
        backgroundRestrictionCheck()?.let { add(it) }
        add(notificationCheck())
        add(assistanceCheck(online))
    }

    private fun permissionCheck(): Check = when {
        LocationPermissions.hasFine(context) -> Check("perm", "Precise location", "Granted.", CheckLevel.Ok)
        LocationPermissions.hasAny(context) -> Check(
            "perm", "Precise location", "Only approximate location is allowed — speed and distance will be meaningless.",
            CheckLevel.Fail, CheckFix.RequestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)), "Allow precise",
        )
        else -> Check(
            "perm", "Location permission", "SpeedTrack can't use GPS without it.",
            CheckLevel.Fail, CheckFix.RequestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)), "Allow",
        )
    }

    private fun locationOnCheck(): Check =
        if (LocationPermissions.isLocationEnabled(context)) Check("on", "Device location", "On.", CheckLevel.Ok)
        else Check(
            "on", "Device location", "Location is switched off for the whole phone.",
            CheckLevel.Fail, CheckFix.OpenSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)), "Turn on",
        )

    /** Google Location Accuracy (network-assisted positioning) — Play services can switch it on in-place. */
    private suspend fun accuracyModeCheck(): Check {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build())
            .setAlwaysShow(true)
            .build()
        return try {
            val response = LocationServices.getSettingsClient(context).checkLocationSettings(request).await()
            val states = response.locationSettingsStates
            if (states?.isNetworkLocationUsable == false) {
                Check("accuracy", "Google Location Accuracy", "Network-assisted positioning is off; fixes are slower and indoor positioning is poor.", CheckLevel.Warn)
            } else {
                Check("accuracy", "High-accuracy mode", "GPS plus Wi-Fi, Bluetooth and mobile-network positioning are available.", CheckLevel.Ok)
            }
        } catch (e: ResolvableApiException) {
            Check(
                "accuracy", "High-accuracy mode", "Turn on Google Location Accuracy so Wi-Fi, Bluetooth and cell towers help pin you down.",
                CheckLevel.Warn, CheckFix.Resolve(e.resolution.intentSender), "Turn on",
            )
        } catch (e: Exception) {
            Check("accuracy", "High-accuracy mode", "Couldn't check (${e.javaClass.simpleName}). Google Play services may be missing.", CheckLevel.Info)
        }
    }

    private fun scanningSettings(): Intent {
        val scanning = Intent("android.settings.LOCATION_SCANNING_SETTINGS")
        return if (scanning.resolveActivity(context.packageManager) != null) scanning else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    private fun wifiScanCheck(): Check {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val enabled = runCatching { wifi?.isWifiEnabled == true }.getOrDefault(false)
        @Suppress("DEPRECATION")
        val scanAlways = runCatching { wifi?.isScanAlwaysAvailable == true }.getOrDefault(false)
        return when {
            enabled || scanAlways -> Check(
                "wifi", "Wi-Fi positioning",
                if (enabled) "Wi-Fi is on — nearby networks refine your position (no connection needed)." else "Wi-Fi scanning is on even with Wi-Fi off.",
                CheckLevel.Ok,
            )
            else -> Check(
                "wifi", "Wi-Fi positioning", "Wi-Fi and Wi-Fi scanning are both off. Turning on Wi-Fi scanning helps in cities and indoors, without connecting.",
                CheckLevel.Warn, CheckFix.OpenSettings(scanningSettings()), "Wi-Fi scanning",
            )
        }
    }

    private fun bluetoothScanCheck(): Check? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val on = runCatching { adapter.isEnabled }.getOrDefault(false)
        val scanAlways = Settings.Global.getInt(context.contentResolver, "ble_scan_always_enabled", 0) == 1
        return if (on || scanAlways) {
            Check("bt", "Bluetooth positioning", if (on) "Bluetooth is on — beacons help indoors." else "Bluetooth scanning is on.", CheckLevel.Ok)
        } else {
            Check(
                "bt", "Bluetooth positioning", "Bluetooth scanning is off. Beacons in malls, stations and airports can improve indoor fixes.",
                CheckLevel.Info, CheckFix.OpenSettings(scanningSettings()), "Bluetooth scanning",
            )
        }
    }

    private fun batterySaverCheck(): Check? {
        val pm = context.getSystemService(PowerManager::class.java) ?: return null
        if (!pm.isPowerSaveMode) return null
        val bad = Build.VERSION.SDK_INT >= 28 && pm.locationPowerSaveMode.let { mode ->
            mode == PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF ||
                mode == PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF ||
                (Build.VERSION.SDK_INT >= 29 && mode == PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF)
        }
        return Check(
            "saver", "Battery saver",
            if (bad) "Battery saver limits GPS while the screen is off — recordings will have gaps." else "Battery saver is on; GPS still works but fixes may be slower.",
            if (bad) CheckLevel.Fail else CheckLevel.Info,
            CheckFix.OpenSettings(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)), "Battery settings",
        )
    }

    private fun backgroundRestrictionCheck(): Check? {
        if (Build.VERSION.SDK_INT < 28) return null
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        if (!am.isBackgroundRestricted) return null
        return Check(
            "restricted", "Battery restriction", "SpeedTrack is set to \"Restricted\" battery use, so Android may stop recording with the screen off.",
            CheckLevel.Fail, CheckFix.OpenSettings(appDetails()), "App settings",
        )
    }

    private fun notificationCheck(): Check {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val needsRuntime = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        return when {
            enabled && !needsRuntime -> Check("notif", "Notifications", "Recording controls show in the notification shade.", CheckLevel.Ok)
            needsRuntime -> Check(
                "notif", "Notifications", "Allow notifications to see and control recording with the screen off.",
                CheckLevel.Warn, CheckFix.RequestPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS)), "Allow",
            )
            else -> Check("notif", "Notifications", "Notifications are blocked for SpeedTrack.", CheckLevel.Warn, CheckFix.OpenSettings(appNotifications()), "Settings")
        }
    }

    private fun assistanceCheck(online: Boolean): Check {
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        return when {
            online -> Check("agps", "Assisted GPS", "Online: satellite orbit data downloads in seconds for a fast first fix.", CheckLevel.Ok)
            airplane -> Check("agps", "Assisted GPS", "Airplane mode: GPS still works, but a cold first fix can take a few minutes under open sky.", CheckLevel.Info)
            else -> Check("agps", "Assisted GPS", "Offline: GPS works, but the first fix may take longer. Tracking itself never needs a connection.", CheckLevel.Info)
        }
    }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    private fun appNotifications() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /**
     * Ask the GNSS chip to refresh assistance data. [coldStart] first deletes cached almanac/ephemeris (a true cold
     * start — useful when a fix is stuck far from where you are). Needs ACCESS_LOCATION_EXTRA_COMMANDS.
     */
    fun refreshAssistance(coldStart: Boolean): Boolean {
        val lm = context.getSystemService(android.location.LocationManager::class.java) ?: return false
        val gps = android.location.LocationManager.GPS_PROVIDER
        return runCatching {
            if (coldStart) lm.sendExtraCommand(gps, "delete_aiding_data", null)
            lm.sendExtraCommand(gps, "force_time_injection", Bundle())
            // PSDS on Android 12+, XTRA before — send both, unknown commands are ignored.
            lm.sendExtraCommand(gps, "force_psds_injection", Bundle())
            lm.sendExtraCommand(gps, "force_xtra_injection", Bundle())
            true
        }.getOrDefault(false)
    }
}
