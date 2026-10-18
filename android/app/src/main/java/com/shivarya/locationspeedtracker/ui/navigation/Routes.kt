package com.shivarya.locationspeedtracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object TrackRoute
@Serializable data object SensorsRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

@Serializable data class SessionRoute(val id: String)
@Serializable data object LiveMapRoute
@Serializable data object GpsDoctorRoute
@Serializable data object PerfRoute
@Serializable data object HudRoute
@Serializable data object OfflineMapsRoute
@Serializable data object ProfileRoute
@Serializable data object HeartRateRoute
@Serializable data object HealthRoute
@Serializable data object BackupRoute
@Serializable data object AboutRoute

/** The Expo app's custom scheme (app.json "scheme"). */
const val DEEP_LINK_BASE = "locationspeedtracker://"

/** Bottom bar (phone) / navigation rail (tablet, foldable). */
enum class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Track(TrackRoute, TrackRoute::class, "Track", Icons.Outlined.Speed, Icons.Filled.Speed),
    Sensors(SensorsRoute, SensorsRoute::class, "Sensors", Icons.Outlined.Sensors, Icons.Filled.Sensors),
    History(HistoryRoute, HistoryRoute::class, "History", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
    Settings(SettingsRoute, SettingsRoute::class, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Destinations drawn edge-to-edge without the navigation bar/rail. */
val FullScreenRoutes: Set<KClass<*>> = setOf(LiveMapRoute::class, HudRoute::class)
