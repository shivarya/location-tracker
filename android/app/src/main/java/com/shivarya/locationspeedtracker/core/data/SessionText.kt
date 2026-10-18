package com.shivarya.locationspeedtracker.core.data

import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** "Morning run", "Evening drive" — used when a session has no name. */
fun SessionEntity.displayTitle(): String {
    name?.let { return it }
    val hour = Calendar.getInstance().apply { timeInMillis = startTime }.get(Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    val a = ActivityType.from(activity)
    return "$part ${if (a == ActivityType.Other) "session" else a.label.lowercase()}"
}

fun SessionEntity.dateLine(): String {
    val d = Date(startTime)
    return "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(d)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(d)}"
}
