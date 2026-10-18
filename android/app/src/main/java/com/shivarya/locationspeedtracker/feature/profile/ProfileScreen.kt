package com.shivarya.locationspeedtracker.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivarya.locationspeedtracker.core.fitness.HrZones
import com.shivarya.locationspeedtracker.core.settings.Profile
import com.shivarya.locationspeedtracker.core.settings.Sex
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import com.shivarya.locationspeedtracker.core.units.Units
import com.shivarya.locationspeedtracker.feature.settings.Segmented
import com.shivarya.locationspeedtracker.feature.settings.SettingsViewModel
import com.shivarya.locationspeedtracker.ui.components.SectionCard
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

val ZoneColors = listOf(Color(0xFF7F8C8D), Color(0xFF2E86FF), Color(0xFF27AE60), Color(0xFFF39C12), Color(0xFFE74C3C))

@Composable
fun ProfileScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val units = ui.settings.unitSystem
    val p = ui.settings.profile
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var maxHr by rememberSaveable { mutableStateOf("") }
    var resting by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.Unspecified) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.loaded) {
        if (!ui.loaded || loaded) return@LaunchedEffect
        weight = fmt(Units.weight(p.weightKg, units).value)
        height = if (units == UnitSystem.Metric) fmt(p.heightCm) else fmt(p.heightCm / Units.CM_PER_IN)
        year = p.birthYear.toString()
        maxHr = if (p.maxHrOverride > 0) p.maxHrOverride.toString() else ""
        resting = p.restingHr.toString()
        sex = p.sex
        loaded = true
    }
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val draft = Profile(
        weightKg = weight.toDoubleOrNull()?.let { Units.weightToKg(it, units) }?.coerceIn(20.0, 300.0) ?: p.weightKg,
        heightCm = height.toDoubleOrNull()?.let { if (units == UnitSystem.Metric) it else it * Units.CM_PER_IN }?.coerceIn(80.0, 250.0) ?: p.heightCm,
        birthYear = year.toIntOrNull()?.coerceIn(thisYear - 100, thisYear - 8) ?: p.birthYear,
        sex = sex,
        maxHrOverride = maxHr.toIntOrNull()?.coerceIn(0, 230) ?: 0,
        restingHr = resting.toIntOrNull()?.coerceIn(30, 120) ?: p.restingHr,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Used only on this phone, to estimate calories and heart-rate zones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(title = "Body") {
                NumberField("Weight (${Units.weight(0.0, units).unit})", weight) { weight = it }
                NumberField(if (units == UnitSystem.Metric) "Height (cm)" else "Height (inches)", height) { height = it }
                NumberField("Year of birth", year) { year = it }
                Spacer(Modifier.height(8.dp))
                Text("Sex (for the calorie formula)", style = MaterialTheme.typography.labelLarge)
                Segmented(Sex.entries, sex, { if (it == Sex.Unspecified) "Rather not say" else it.name }) { sex = it }
            }
            SectionCard(title = "Heart rate") {
                NumberField("Max heart rate (blank = estimate from age)", maxHr) { maxHr = it }
                NumberField("Resting heart rate", resting) { resting = it }
                Spacer(Modifier.height(8.dp))
                val max = draft.maxHr(thisYear)
                Text("Zones for max $max bpm", style = MaterialTheme.typography.labelLarge)
                val floors = HrZones.floors(max) + max
                HrZones.names.forEachIndexed { i, name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(12.dp), shape = RoundedCornerShape(3.dp), color = ZoneColors[i]) {}
                        Spacer(Modifier.width(8.dp))
                        Text("Zone ${i + 1} · $name", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${floors[i]}–${floors[i + 1] - 1} bpm", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = { vm.setProfile(draft); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

private fun fmt(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v)
