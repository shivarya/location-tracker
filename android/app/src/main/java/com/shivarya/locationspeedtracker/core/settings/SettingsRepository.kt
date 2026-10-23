package com.shivarya.locationspeedtracker.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val stored: String, val label: String) {
    System("auto", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun from(stored: String?) = entries.firstOrNull { it.stored == stored }
    }
}

/** Classic = the Expo app's navy + cyan. Dynamic = Material You wallpaper colours (Android 12+). */
enum class Palette { Classic, Dynamic }

enum class MapType(val label: String) { Normal("Map"), Satellite("Satellite"), Terrain("Terrain"), Hybrid("Hybrid") }

/** Which renderer draws maps. Auto = Google online, offline MapLibre regions without a connection. */
enum class MapEngine(val label: String) { Auto("Automatic"), Google("Google Maps"), Offline("Offline maps (OpenStreetMap)") }

enum class LocationSourceMode(val label: String, val detail: String) {
    Fused("Fused (recommended)", "GPS + Wi-Fi + Bluetooth + cell — fastest, most accurate fix"),
    Gnss("Satellites only", "Raw GPS/GNSS chip — no network assistance"),
}

enum class Sex { Unspecified, Female, Male }

data class Profile(
    val weightKg: Double = 70.0,
    val heightCm: Double = 170.0,
    val birthYear: Int = 1990,
    val sex: Sex = Sex.Unspecified,
    /** 0 = estimate from age (220 − age). */
    val maxHrOverride: Int = 0,
    val restingHr: Int = 60,
) {
    fun maxHr(currentYear: Int): Int = if (maxHrOverride > 0) maxHrOverride else (220 - (currentYear - birthYear)).coerceIn(140, 220)
}

data class AppSettings(
    val unitSystem: UnitSystem = UnitSystem.Metric,
    // The Expo app was always dark; upgraded users keep that look unless they choose otherwise.
    val themeMode: ThemeMode = ThemeMode.Dark,
    val palette: Palette = Palette.Classic,
    val mapType: MapType = MapType.Normal,
    val mapEngine: MapEngine = MapEngine.Auto,
    val locationSource: LocationSourceMode = LocationSourceMode.Fused,
    val intervalMs: Long = 1000,
    val accuracyThresholdM: Int = 40,
    val keepScreenOn: Boolean = true,
    val showPace: Boolean = true,
    val autoSplits: Boolean = true,
    val autoPause: Boolean = false,
    val lastActivity: ActivityType = ActivityType.Other,
    val profile: Profile = Profile(),
    val hrDeviceAddress: String? = null,
    val hrDeviceName: String? = null,
    val healthConnectSync: Boolean = false,
    val perfRollout: Boolean = true,
    val perfDisclaimerAccepted: Boolean = false,
    /** A session's share image carries a QR code to the app and its message the Play links; the share sheet's switch. */
    val shareAppLinks: Boolean = true,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.settingsStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            unitSystem = UnitSystem.from(p[Keys.UNITS]),
            themeMode = ThemeMode.from(p[Keys.THEME]) ?: d.themeMode,
            palette = p[Keys.PALETTE].enumOr(d.palette),
            mapType = p[Keys.MAP_TYPE].enumOr(d.mapType),
            mapEngine = p[Keys.MAP_ENGINE].enumOr(d.mapEngine),
            locationSource = p[Keys.LOCATION_SOURCE].enumOr(d.locationSource),
            intervalMs = p[Keys.INTERVAL] ?: d.intervalMs,
            accuracyThresholdM = p[Keys.ACCURACY] ?: d.accuracyThresholdM,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOn,
            showPace = p[Keys.SHOW_PACE] ?: d.showPace,
            autoSplits = p[Keys.AUTO_SPLITS] ?: d.autoSplits,
            autoPause = p[Keys.AUTO_PAUSE] ?: d.autoPause,
            lastActivity = ActivityType.from(p[Keys.LAST_ACTIVITY]),
            profile = Profile(
                weightKg = p[Keys.WEIGHT] ?: d.profile.weightKg,
                heightCm = p[Keys.HEIGHT] ?: d.profile.heightCm,
                birthYear = p[Keys.BIRTH_YEAR] ?: d.profile.birthYear,
                sex = p[Keys.SEX].enumOr(d.profile.sex),
                maxHrOverride = p[Keys.MAX_HR] ?: d.profile.maxHrOverride,
                restingHr = p[Keys.RESTING_HR] ?: d.profile.restingHr,
            ),
            hrDeviceAddress = p[Keys.HR_ADDRESS],
            hrDeviceName = p[Keys.HR_NAME],
            healthConnectSync = p[Keys.HEALTH_SYNC] ?: d.healthConnectSync,
            perfRollout = p[Keys.PERF_ROLLOUT] ?: d.perfRollout,
            perfDisclaimerAccepted = p[Keys.PERF_DISCLAIMER] ?: d.perfDisclaimerAccepted,
            shareAppLinks = p[Keys.SHARE_APP_LINKS] ?: d.shareAppLinks,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setUnitSystem(v: UnitSystem) = store.edit { it[Keys.UNITS] = v.stored }
    suspend fun setThemeMode(v: ThemeMode) = store.edit { it[Keys.THEME] = v.stored }
    suspend fun setPalette(v: Palette) = store.edit { it[Keys.PALETTE] = v.name }
    suspend fun setMapType(v: MapType) = store.edit { it[Keys.MAP_TYPE] = v.name }
    suspend fun setMapEngine(v: MapEngine) = store.edit { it[Keys.MAP_ENGINE] = v.name }
    suspend fun setLocationSource(v: LocationSourceMode) = store.edit { it[Keys.LOCATION_SOURCE] = v.name }
    suspend fun setIntervalMs(v: Long) = store.edit { it[Keys.INTERVAL] = v }
    suspend fun setAccuracyThreshold(v: Int) = store.edit { it[Keys.ACCURACY] = v }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setShowPace(v: Boolean) = store.edit { it[Keys.SHOW_PACE] = v }
    suspend fun setAutoSplits(v: Boolean) = store.edit { it[Keys.AUTO_SPLITS] = v }
    suspend fun setAutoPause(v: Boolean) = store.edit { it[Keys.AUTO_PAUSE] = v }
    suspend fun setLastActivity(v: ActivityType) = store.edit { it[Keys.LAST_ACTIVITY] = v.stored }
    suspend fun setHealthConnectSync(v: Boolean) = store.edit { it[Keys.HEALTH_SYNC] = v }
    suspend fun setPerfRollout(v: Boolean) = store.edit { it[Keys.PERF_ROLLOUT] = v }
    suspend fun setPerfDisclaimerAccepted() = store.edit { it[Keys.PERF_DISCLAIMER] = true }
    suspend fun setShareAppLinks(v: Boolean) = store.edit { it[Keys.SHARE_APP_LINKS] = v }

    suspend fun setProfile(p: Profile) = store.edit {
        it[Keys.WEIGHT] = p.weightKg
        it[Keys.HEIGHT] = p.heightCm
        it[Keys.BIRTH_YEAR] = p.birthYear
        it[Keys.SEX] = p.sex.name
        it[Keys.MAX_HR] = p.maxHrOverride
        it[Keys.RESTING_HR] = p.restingHr
    }

    suspend fun setHrDevice(address: String?, name: String?) = store.edit {
        if (address == null) it.remove(Keys.HR_ADDRESS) else it[Keys.HR_ADDRESS] = address
        if (name == null) it.remove(Keys.HR_NAME) else it[Keys.HR_NAME] = name
    }

    // ---- Expo-app migration -------------------------------------------------------------------------------------

    suspend fun isLegacyMigrated(): Boolean = store.data.first()[Keys.LEGACY_MIGRATED] ?: false
    suspend fun legacyAttempts(): Int = store.data.first()[Keys.LEGACY_ATTEMPTS] ?: 0
    suspend fun recordLegacyAttempt() = store.edit { it[Keys.LEGACY_ATTEMPTS] = (it[Keys.LEGACY_ATTEMPTS] ?: 0) + 1 }

    /** Marks the import done; imports the unit choice unless the user already picked one in this app. */
    suspend fun applyLegacy(unitPreference: String?, importedSessions: Int) = store.edit { p ->
        if (unitPreference != null && p[Keys.UNITS] == null) p[Keys.UNITS] = UnitSystem.from(unitPreference).stored
        p[Keys.LEGACY_MIGRATED] = true
        p[Keys.LEGACY_IMPORTED] = importedSessions
        p[Keys.LEGACY_AT] = System.currentTimeMillis()
    }

    val legacyImported: Flow<Int?> = store.data.map { it[Keys.LEGACY_IMPORTED] }

    /** Preferences as typed strings for a backup file ("b:true", "i:40", "s:metric"…); migration bookkeeping excluded. */
    suspend fun exportMap(): Map<String, String> = store.data.first().asMap().mapNotNull { (k, v) ->
        if (k.name.startsWith("legacy_")) return@mapNotNull null
        val enc = when (v) {
            is Boolean -> "b:$v"
            is Int -> "i:$v"
            is Long -> "l:$v"
            is Double -> "d:$v"
            is Float -> "f:$v"
            is String -> "s:$v"
            else -> return@mapNotNull null
        }
        k.name to enc
    }.toMap()

    suspend fun restoreMap(map: Map<String, String>) = store.edit { p ->
        map.forEach { (name, enc) ->
            if (name.startsWith("legacy_")) return@forEach
            val value = enc.substringAfter(':', "")
            when (enc.substringBefore(':')) {
                "b" -> value.toBooleanStrictOrNull()?.let { p[booleanPreferencesKey(name)] = it }
                "i" -> value.toIntOrNull()?.let { p[intPreferencesKey(name)] = it }
                "l" -> value.toLongOrNull()?.let { p[longPreferencesKey(name)] = it }
                "d" -> value.toDoubleOrNull()?.let { p[doublePreferencesKey(name)] = it }
                "s" -> p[stringPreferencesKey(name)] = value
            }
        }
    }

    private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
        this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private object Keys {
        // Same key name and values as the Expo app's AsyncStorage `unit_preference`.
        val UNITS = stringPreferencesKey("unit_preference")
        val THEME = stringPreferencesKey("theme_preference")
        val PALETTE = stringPreferencesKey("palette")
        val MAP_TYPE = stringPreferencesKey("map_type")
        val MAP_ENGINE = stringPreferencesKey("map_engine")
        val LOCATION_SOURCE = stringPreferencesKey("location_source")
        val INTERVAL = longPreferencesKey("interval_ms")
        val ACCURACY = intPreferencesKey("accuracy_threshold_m")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_PACE = booleanPreferencesKey("show_pace")
        val AUTO_SPLITS = booleanPreferencesKey("auto_splits")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val LAST_ACTIVITY = stringPreferencesKey("last_activity")
        val WEIGHT = doublePreferencesKey("profile_weight_kg")
        val HEIGHT = doublePreferencesKey("profile_height_cm")
        val BIRTH_YEAR = intPreferencesKey("profile_birth_year")
        val SEX = stringPreferencesKey("profile_sex")
        val MAX_HR = intPreferencesKey("profile_max_hr")
        val RESTING_HR = intPreferencesKey("profile_resting_hr")
        val HR_ADDRESS = stringPreferencesKey("hr_device_address")
        val HR_NAME = stringPreferencesKey("hr_device_name")
        val HEALTH_SYNC = booleanPreferencesKey("health_connect_sync")
        val PERF_ROLLOUT = booleanPreferencesKey("perf_rollout")
        val PERF_DISCLAIMER = booleanPreferencesKey("perf_disclaimer")
        val SHARE_APP_LINKS = booleanPreferencesKey("share_app_links")
        val LEGACY_MIGRATED = booleanPreferencesKey("legacy_migrated")
        val LEGACY_ATTEMPTS = intPreferencesKey("legacy_attempts")
        val LEGACY_IMPORTED = intPreferencesKey("legacy_imported_sessions")
        val LEGACY_AT = longPreferencesKey("legacy_migrated_at")
    }
}
