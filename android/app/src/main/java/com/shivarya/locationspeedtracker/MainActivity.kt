package com.shivarya.locationspeedtracker

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.shivarya.locationspeedtracker.core.tracking.TrackingEngine
import com.shivarya.locationspeedtracker.ui.PipView
import com.shivarya.locationspeedtracker.ui.RootState
import com.shivarya.locationspeedtracker.ui.RootViewModel
import com.shivarya.locationspeedtracker.ui.navigation.AppScaffold
import com.shivarya.locationspeedtracker.ui.theme.SpeedTrackTheme
import com.shivarya.locationspeedtracker.ui.theme.isAppInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val root: RootViewModel by viewModels()

    @Inject lateinit var engine: TrackingEngine

    /** Picture-in-picture: while recording, leaving the app shrinks it to a floating speedometer. */
    private val inPip = mutableStateOf(false)

    /** singleTask: deep links / shortcut intents arriving while running come via onNewIntent. */
    private val newIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    private val deepLinks = newIntents.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { root.state.value is RootState.Loading }
        lifecycleScope.launch {
            engine.state.map { it.isRecording }.distinctUntilChanged().collect { recording ->
                runCatching { setPictureInPictureParams(pipParams(recording)) }
            }
        }

        setContent {
            val settings by root.settings.collectAsStateWithLifecycle()
            val state by root.state.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(settings.themeMode)
            // Status/nav bar icons follow the app's theme, not the system's (they differ when the user overrides it).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            SpeedTrackTheme(themeMode = settings.themeMode, palette = settings.palette) {
                Box(Modifier.fillMaxSize()) {
                    when (val s = state) {
                        RootState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                        is RootState.Ready -> AppScaffold(
                            newIntents = deepLinks,
                            initialIntent = intent,
                            startupMessage = s.message,
                        )
                    }
                    // Drawn over (not instead of) the app, so navigation state survives the trip into PiP.
                    if (inPip.value) PipView(engine, settings.unitSystem)
                }
            }
        }
    }

    private fun pipParams(recording: Boolean): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .apply { if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(recording).setSeamlessResizeEnabled(false) }
        .build()

    /** Android 8–11 have no auto-enter: do it on the home gesture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && engine.state.value.isRecording) runCatching { enterPictureInPictureMode(pipParams(true)) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newIntents.tryEmit(intent)
    }

    private companion object {
        // androidx.activity's defaults for the 3-button navigation bar scrim.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
