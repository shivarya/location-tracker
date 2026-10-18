package com.shivarya.locationspeedtracker.feature.history

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.export.ExportFormat
import com.shivarya.locationspeedtracker.core.export.ExportManager
import com.shivarya.locationspeedtracker.core.export.ImportManager
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.units.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class Totals(val sessions: Int = 0, val distanceM: Double = 0.0, val movingS: Double = 0.0, val elevGainM: Double = 0.0)

data class HistoryUi(
    val loading: Boolean = true,
    val sessions: List<SessionEntity> = emptyList(),
    val filter: ActivityType? = null,
    /** Activities present in history (filter chips only offer these). */
    val activities: List<ActivityType> = emptyList(),
    val week: Totals = Totals(),
    val month: Totals = Totals(),
    val units: UnitSystem = UnitSystem.Metric,
    val selection: Set<String> = emptySet(),
)

sealed interface HistoryEvent {
    data class Deleted(val ids: List<String>) : HistoryEvent
    data class Share(val intent: Intent) : HistoryEvent
    data class Message(val text: String) : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val export: ExportManager,
    private val importer: ImportManager,
    settings: SettingsRepository,
) : ViewModel() {
    private val filter = MutableStateFlow<ActivityType?>(null)
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val events = Channel<HistoryEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val ui: StateFlow<HistoryUi> = combine(
        sessions.finished,
        filter,
        settings.settings.map { it.unitSystem },
        selection,
    ) { all, f, units, sel ->
        val shown = if (f == null) all else all.filter { it.activity == f.stored }
        HistoryUi(
            loading = false,
            sessions = shown,
            filter = f,
            activities = all.map { ActivityType.from(it.activity) }.distinct().sortedBy { it.ordinal },
            week = totals(shown, since(Calendar.WEEK_OF_YEAR)),
            month = totals(shown, since(Calendar.MONTH)),
            units = units,
            selection = sel.intersect(shown.map { it.id }.toSet()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun setFilter(a: ActivityType?) { filter.value = a }

    fun toggleSelect(id: String) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { selection.value = emptySet() }

    fun delete(ids: List<String>) = viewModelScope.launch {
        sessions.softDelete(ids)
        selection.value = selection.value - ids.toSet()
        events.send(HistoryEvent.Deleted(ids))
    }

    fun undoDelete(ids: List<String>) = viewModelScope.launch { sessions.restore(ids) }

    fun export(format: ExportFormat, ids: List<String>? = selection.value.takeIf { it.isNotEmpty() }?.toList()) = viewModelScope.launch {
        runCatching {
            val files = when (format) {
                ExportFormat.Json -> listOf(export.json(ids))
                ExportFormat.Csv -> listOf(export.sessionsCsv(ids))
                else -> ids.orEmpty().ifEmpty { ui.value.sessions.map { it.id } }.mapNotNull { export.track(it, format) }
            }
            if (files.isEmpty()) error("Nothing to export")
            events.send(HistoryEvent.Share(export.shareIntent(files, "SpeedTrack export")))
        }.onFailure { events.send(HistoryEvent.Message("Export failed: ${it.message}")) }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val result = importer.import(uri)
        events.send(HistoryEvent.Message(result.message))
    }

    private fun since(field: Int): Long = Calendar.getInstance().apply {
        if (field == Calendar.WEEK_OF_YEAR) set(Calendar.DAY_OF_WEEK, firstDayOfWeek) else set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun totals(list: List<SessionEntity>, from: Long) = list.filter { it.startTime >= from }.let { l ->
        Totals(l.size, l.sumOf { it.stats.distanceM }, l.sumOf { it.stats.movingS }, l.sumOf { it.stats.elevGainM })
    }
}
