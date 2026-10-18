package com.shivarya.locationspeedtracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shivarya.locationspeedtracker.ui.components.SectionCard

private val credits = listOf(
    "Map data" to "Online maps © Google. Offline maps: © OpenStreetMap contributors (ODbL), © OpenMapTiles, served by OpenFreeMap.",
    "MapLibre Native" to "Offline map renderer — BSD 2-Clause licence.",
    "Google Play services" to "Fused location, activity recognition and Maps SDK — Google APIs Terms of Service.",
    "Android Jetpack, Material 3, Kotlin" to "Apache License 2.0.",
    "Geoid model" to "Mean-sea-level altitude from androidx.core altitude (EGM2008) — Apache License 2.0.",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map data & licences") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            credits.forEach { (title, body) ->
                SectionCard(title = title) { Text(body, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
