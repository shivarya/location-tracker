package com.shivarya.locationspeedtracker.ui.navigation

import android.content.Intent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.shivarya.locationspeedtracker.feature.gps.GpsDoctorScreen
import com.shivarya.locationspeedtracker.feature.health.HealthScreen
import com.shivarya.locationspeedtracker.feature.history.HistoryScreen
import com.shivarya.locationspeedtracker.feature.hr.HeartRateScreen
import com.shivarya.locationspeedtracker.feature.hud.HudScreen
import com.shivarya.locationspeedtracker.feature.map.LiveMapScreen
import com.shivarya.locationspeedtracker.feature.map.ProvideMapEnvironment
import com.shivarya.locationspeedtracker.feature.offline.OfflineMapsScreen
import com.shivarya.locationspeedtracker.feature.perf.PerfScreen
import com.shivarya.locationspeedtracker.feature.profile.ProfileScreen
import com.shivarya.locationspeedtracker.feature.sensors.SensorsScreen
import com.shivarya.locationspeedtracker.feature.session.SessionScreen
import com.shivarya.locationspeedtracker.feature.settings.AboutScreen
import com.shivarya.locationspeedtracker.feature.settings.BackupScreen
import com.shivarya.locationspeedtracker.feature.settings.SettingsNav
import com.shivarya.locationspeedtracker.feature.settings.SettingsScreen
import com.shivarya.locationspeedtracker.feature.track.TrackNav
import com.shivarya.locationspeedtracker.feature.track.TrackScreen
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

/** Intent extra set by app shortcuts / the widget to open a screen directly. */
const val EXTRA_OPEN = "open"
const val OPEN_HUD = "hud"
const val OPEN_TRACK = "track"

@Composable
fun AppScaffold(newIntents: SharedFlow<Intent>, initialIntent: Intent?, startupMessage: String?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(startupMessage) { startupMessage?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(nav) {
        initialIntent?.let { nav.handleAppIntent(it) }
        newIntents.collect { nav.handleAppIntent(it) }
    }

    val fullScreen = FullScreenRoutes.any { destination?.hasRoute(it) == true }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = when {
        fullScreen -> NavigationSuiteType.None
        // A phone on its side (often in a car mount): a bottom bar would take a fifth of the height, so use a rail.
        !adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.WideNavigationRailCollapsed
        else -> NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)
    }

    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val back: () -> Unit = { nav.popBackStack() }

    CompositionLocalProvider(LocalSnackbar provides snackbar) { ProvideMapEnvironment {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                TopLevel.entries.forEach { tab ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                    item(
                        selected = selected,
                        onClick = { nav.navigateTopLevel(tab) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                tab.label,
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        },
                    )
                }
            },
        ) {
            NavHost(navController = nav, startDestination = TrackRoute) {
                composable<TrackRoute> {
                    TrackScreen(
                        TrackNav(
                            openSession = { nav.navigate(SessionRoute(it)) },
                            openMap = { nav.navigate(LiveMapRoute) },
                            openGps = { nav.navigate(GpsDoctorRoute) },
                            openPerf = { nav.navigate(PerfRoute) },
                            openHud = { nav.navigate(HudRoute) },
                            showMessage = showMessage,
                        ),
                    )
                }
                composable<SensorsRoute> { SensorsScreen() }
                composable<HistoryRoute> { HistoryScreen(onOpenSession = { nav.navigate(SessionRoute(it)) }) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        SettingsNav(
                            openGps = { nav.navigate(GpsDoctorRoute) },
                            openOffline = { nav.navigate(OfflineMapsRoute) },
                            openProfile = { nav.navigate(ProfileRoute) },
                            openHeartRate = { nav.navigate(HeartRateRoute) },
                            openHealth = { nav.navigate(HealthRoute) },
                            openBackup = { nav.navigate(BackupRoute) },
                            openSensors = { nav.navigateTopLevel(TopLevel.Sensors) },
                            openAbout = { nav.navigate(AboutRoute) },
                        ),
                    )
                }
                composable<SessionRoute> { SessionScreen(onBack = back) }
                composable<LiveMapRoute> { LiveMapScreen(onBack = back) }
                composable<GpsDoctorRoute> { GpsDoctorScreen(onBack = back) }
                composable<PerfRoute> { PerfScreen(onBack = back) }
                composable<HudRoute> { HudScreen(onBack = back) }
                composable<OfflineMapsRoute> { OfflineMapsScreen(onBack = back) }
                composable<ProfileRoute> { ProfileScreen(onBack = back) }
                composable<HeartRateRoute> { HeartRateScreen(onBack = back) }
                composable<HealthRoute> { HealthScreen(onBack = back) }
                composable<BackupRoute> { BackupScreen(onBack = back) }
                composable<AboutRoute> { AboutScreen(onBack = back) }
            }
        }
    } }
}

private fun NavHostController.handleAppIntent(intent: Intent) {
    when (intent.getStringExtra(EXTRA_OPEN)) {
        OPEN_HUD -> navigate(HudRoute) { launchSingleTop = true }
        OPEN_TRACK -> navigateTopLevel(TopLevel.Track)
        else -> if (intent.data != null) handleDeepLink(intent)
    }
}

/**
 * A tab tap always lands on that tab's root. Saving/restoring the popped stack resurrects pushed screens on top of
 * the tab (expense-tracker hit exactly this), so state is deliberately not restored.
 */
private fun NavHostController.navigateTopLevel(tab: TopLevel) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
