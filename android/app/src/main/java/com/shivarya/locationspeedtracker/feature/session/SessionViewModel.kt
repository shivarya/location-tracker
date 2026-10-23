package com.shivarya.locationspeedtracker.feature.session

import android.content.Intent
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.shivarya.locationspeedtracker.core.data.SessionRepository
import com.shivarya.locationspeedtracker.core.data.db.LapEntity
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.SessionEntity
import com.shivarya.locationspeedtracker.core.data.toSample
import com.shivarya.locationspeedtracker.core.export.ExportFile
import com.shivarya.locationspeedtracker.core.export.ExportFormat
import com.shivarya.locationspeedtracker.core.export.ExportManager
import com.shivarya.locationspeedtracker.core.export.ShareCard
import com.shivarya.locationspeedtracker.core.export.ShareCardRenderer
import com.shivarya.locationspeedtracker.core.export.shareCaption
import com.shivarya.locationspeedtracker.core.model.ActivityType
import com.shivarya.locationspeedtracker.core.settings.AppSettings
import com.shivarya.locationspeedtracker.core.settings.SettingsRepository
import com.shivarya.locationspeedtracker.core.tracking.TrackSample
import com.shivarya.locationspeedtracker.core.tracking.displayChunks
import kotlinx.coroutines.Dispatchers
import com.shivarya.locationspeedtracker.ui.navigation.SessionRoute
import com.shivarya.locationspeedtracker.core.health.HealthConnectManager
import com.shivarya.locationspeedtracker.core.health.HealthSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SessionUi(
    val loading: Boolean = true,
    val session: SessionEntity? = null,
    val samples: List<TrackSample> = emptyList(),
    /** [samples] simplified for the map. */
    val route: List<List<TrackSample>> = emptyList(),
    val laps: List<LapEntity> = emptyList(),
    val metrics: List<MetricEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val deleted: Boolean = false,
)

/** The share-image preview sheet: drawn when it opens and again whenever one of its switches changes. */
data class ShareCardUi(
    val open: Boolean = false,
    val hideEnds: Boolean = false,
    /** QR code on the image, Play links in the message; remembered in [AppSettings.shareAppLinks]. */
    val appLinks: Boolean = true,
    val loading: Boolean = false,
    val card: ShareCard? = null,
    val error: String? = null,
)

sealed interface SessionEvent {
    data class Share(val intent: Intent) : SessionEvent
    data class Message(val text: String) : SessionEvent
    data object Deleted : SessionEvent
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val health: HealthConnectManager,
    private val export: ExportManager,
    private val cards: ShareCardRenderer,
    private val settings: SettingsRepository,
) : ViewModel() {
    val id: String = savedState.toRoute<SessionRoute>().id
    private val track = MutableStateFlow(Loaded())

    private data class Loaded(
        val samples: List<TrackSample> = emptyList(),
        val route: List<List<TrackSample>> = emptyList(),
        val metrics: List<MetricEntity> = emptyList(),
        val done: Boolean = false,
    )
    private val events = Channel<SessionEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val ui: StateFlow<SessionUi> = combine(
        sessions.observe(id),
        sessions.observeLaps(id),
        track,
        settings.settings,
    ) { s, laps, t, st ->
        SessionUi(
            loading = !t.done,
            session = s,
            samples = t.samples,
            route = t.route,
            laps = laps,
            metrics = t.metrics,
            settings = st,
            deleted = t.done && s == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUi())

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val pts = sessions.points(id).map { it.toSample() }
            val metrics = sessions.metrics(id)
            track.value = Loaded(pts, displayChunks(pts), metrics, done = true)
            // Heart rate can arrive later (Health Connect backfill): reload sensor rows when the session changes.
            sessions.observe(id).map { it?.updatedAt }.distinctUntilChanged().drop(1).collect {
                track.value = track.value.copy(metrics = sessions.metrics(id))
            }
        }
    }

    fun rename(name: String) = viewModelScope.launch { sessions.rename(id, name) }
    fun setNotes(notes: String) = viewModelScope.launch { sessions.setNotes(id, notes) }
    fun setActivity(a: ActivityType) = viewModelScope.launch { sessions.setActivity(id, a) }

    /** Health Connect actions are only offered for activity types it has (not drives / motorbike rides). */
    val healthSupported: Boolean get() = ui.value.session?.let { health.exerciseType(ActivityType.from(it.activity)) != null } == true

    fun syncToHealth() = viewModelScope.launch {
        if (!health.canWrite()) {
            events.send(SessionEvent.Message("Connect Health Connect in Settings first"))
            return@launch
        }
        HealthSyncWorker.enqueue(context, id)
        events.send(SessionEvent.Message("Saving to Health Connect…"))
    }

    fun fetchHeartRate() = viewModelScope.launch {
        if (!health.canReadHeartRate()) {
            events.send(SessionEvent.Message("Connect Health Connect in Settings first"))
            return@launch
        }
        HealthSyncWorker.fetchHeartRate(context, id)
        events.send(SessionEvent.Message("Looking for heart rate from your watch…"))
    }

    fun delete() = viewModelScope.launch {
        sessions.delete(id)
        events.send(SessionEvent.Deleted)
    }

    fun share(format: ExportFormat) = viewModelScope.launch {
        runCatching {
            val file = when (format) {
                ExportFormat.Json -> export.json(listOf(id))
                ExportFormat.Csv -> export.sessionsCsv(listOf(id))
                else -> export.track(id, format) ?: error("This session has no GPS points")
            }
            events.send(SessionEvent.Share(export.shareIntent(listOf(file), "SpeedTrack session")))
        }.onFailure { events.send(SessionEvent.Message("Export failed: ${it.message}")) }
    }

    private val _shareCard = MutableStateFlow(ShareCardUi())
    val shareCard: StateFlow<ShareCardUi> = _shareCard
    private var cardJob: Job? = null

    fun openShareCard() {
        _shareCard.value = ShareCardUi(open = true, appLinks = ui.value.settings.shareAppLinks)
        renderCard()
    }

    fun setHideEnds(hide: Boolean) {
        _shareCard.update { it.copy(hideEnds = hide) }
        renderCard()
    }

    fun setAppLinks(on: Boolean) {
        _shareCard.update { it.copy(appLinks = on) }
        viewModelScope.launch { settings.setShareAppLinks(on) }
        renderCard()
    }

    fun closeShareCard() {
        cardJob?.cancel()
        _shareCard.value = ShareCardUi()
    }

    private fun renderCard() {
        cardJob?.cancel()
        cardJob = viewModelScope.launch {
            _shareCard.update { it.copy(loading = true, error = null) }
            try {
                val full = sessions.full(id) ?: error("the session is gone")
                val card = _shareCard.value.let { cards.render(full, ui.value.settings, it.hideEnds, it.appLinks) }
                _shareCard.update { it.copy(loading = false, card = card) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _shareCard.update { it.copy(loading = false, error = "Couldn't draw the image: ${e.message}") }
            }
        }
    }

    fun shareCardImage() = viewModelScope.launch {
        val card = _shareCard.value.card ?: return@launch
        val s = ui.value.session ?: return@launch
        val st = ui.value.settings
        val text = shareCaption(s, st.unitSystem, st.showPace, _shareCard.value.appLinks)
        events.send(SessionEvent.Share(export.shareIntent(listOf(ExportFile(card.file, "image/png")), "SpeedTrack session", text)))
        closeShareCard()
    }
}
