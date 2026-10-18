package com.shivarya.locationspeedtracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shivarya.locationspeedtracker.core.model.ActivityType

val ActivityType.icon: ImageVector
    get() = when (this) {
        ActivityType.Walk -> Icons.AutoMirrored.Filled.DirectionsWalk
        ActivityType.Run -> Icons.AutoMirrored.Filled.DirectionsRun
        ActivityType.Hike -> Icons.Filled.Hiking
        ActivityType.Cycle -> Icons.AutoMirrored.Filled.DirectionsBike
        ActivityType.Drive -> Icons.Filled.DirectionsCar
        ActivityType.Motorbike -> Icons.Filled.TwoWheeler
        ActivityType.Other -> Icons.Filled.Speed
    }

@Composable
fun ActivitySelector(selected: ActivityType, onSelect: (ActivityType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivityType.entries.forEach { a ->
            FilterChip(
                selected = a == selected,
                onClick = { onSelect(a) },
                label = { Text(a.label) },
                leadingIcon = { Icon(a.icon, contentDescription = null) },
            )
        }
    }
}
