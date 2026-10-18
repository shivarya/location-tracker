package com.shivarya.locationspeedtracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.shivarya.locationspeedtracker.core.settings.Palette
import com.shivarya.locationspeedtracker.core.settings.ThemeMode

// "Classic" = the Expo app's palette (navy #1A252F / #2C3E50 / #34495E, cyan accent #00D4FF).
private val ClassicDark = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF00222C),
    primaryContainer = Color(0xFF004E61),
    onPrimaryContainer = Color(0xFFB8EEFF),
    secondary = Color(0xFF95A5A6),
    onSecondary = Color(0xFF1A252F),
    secondaryContainer = Color(0xFF34495E),
    onSecondaryContainer = Color(0xFFECF0F1),
    tertiary = Color(0xFFF1C40F),
    onTertiary = Color(0xFF2B2200),
    tertiaryContainer = Color(0xFF4F3F00),
    onTertiaryContainer = Color(0xFFFFE07A),
    background = Color(0xFF1A252F),
    onBackground = Color(0xFFECF0F1),
    surface = Color(0xFF1A252F),
    onSurface = Color(0xFFECF0F1),
    surfaceVariant = Color(0xFF34495E),
    onSurfaceVariant = Color(0xFF95A5A6),
    surfaceContainerLowest = Color(0xFF101A24),
    surfaceContainerLow = Color(0xFF223042),
    surfaceContainer = Color(0xFF2C3E50),
    surfaceContainerHigh = Color(0xFF30455A),
    surfaceContainerHighest = Color(0xFF34495E),
    outline = Color(0xFF7F8C8D),
    outlineVariant = Color(0xFF3D5266),
    error = Color(0xFFE74C3C),
    onError = Color.White,
)

private val ClassicLight = lightColorScheme(
    primary = Color(0xFF00677F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EAFF),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF4C616B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE6F1),
    onSecondaryContainer = Color(0xFF071E26),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDF9C),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF4F7),
    surfaceContainer = Color(0xFFE9EFF1),
    surfaceContainerHigh = Color(0xFFE3E9EC),
    surfaceContainerHighest = Color(0xFFDEE3E6),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFBFC8CC),
    error = Color(0xFFBA1A1A),
)

/** Fixed meaning colours (GPS quality, speed ramp) — not theme-derived on purpose. */
@Immutable
data class SignalColors(
    val excellent: Color,
    val good: Color,
    val fair: Color,
    val poor: Color,
    val bad: Color,
    val warning: Color,
    val recording: Color,
)

// The Expo app's GPSStrengthIndicator colours.
private val SignalDark = SignalColors(
    excellent = Color(0xFF27AE60),
    good = Color(0xFF2ECC71),
    fair = Color(0xFFF39C12),
    poor = Color(0xFFE67E22),
    bad = Color(0xFFE74C3C),
    warning = Color(0xFFF1C40F),
    recording = Color(0xFFFF5252),
)
private val SignalLight = SignalDark.copy(
    good = Color(0xFF1E9E52),
    fair = Color(0xFFC77700),
    poor = Color(0xFFC25400),
    warning = Color(0xFF9A7400),
    recording = Color(0xFFD32F2F),
)

val LocalSignalColors = staticCompositionLocalOf { SignalDark }

object SpeedTrackTheme {
    val signal: SignalColors
        @Composable get() = LocalSignalColors.current
}

/**
 * Wallpaper-derived dark schemes put every surface tier within a hair of the background, so cards vanish into the
 * page. Blend each tier toward onSurface for visible separation (same fix as diet-plan / expense-tracker).
 */
private fun ColorScheme.withLiftedSurfaces() = copy(
    surfaceContainerLowest = lerp(surface, Color.Black, 0.35f),
    surfaceContainerLow = lerp(surface, onSurface, 0.05f),
    surfaceContainer = lerp(surface, onSurface, 0.08f),
    surfaceContainerHigh = lerp(surface, onSurface, 0.11f),
    surfaceContainerHighest = lerp(surface, onSurface, 0.15f),
    outlineVariant = lerp(surface, onSurface, 0.22f),
)

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun isAppInDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun SpeedTrackTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    palette: Palette = Palette.Classic,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        palette == Palette.Dynamic && supportsDynamicColor ->
            if (dark) dynamicDarkColorScheme(context).withLiftedSurfaces() else dynamicLightColorScheme(context)
        dark -> ClassicDark
        else -> ClassicLight
    }
    CompositionLocalProvider(LocalSignalColors provides if (dark) SignalDark else SignalLight) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
