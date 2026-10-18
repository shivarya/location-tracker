package com.shivarya.locationspeedtracker.core.export

import com.shivarya.locationspeedtracker.core.data.FullSession
import com.shivarya.locationspeedtracker.core.data.db.MetricEntity
import com.shivarya.locationspeedtracker.core.data.db.TrackPointEntity
import com.shivarya.locationspeedtracker.core.model.ActivityType
import java.time.Instant
import java.util.Locale

/** Writers for the interchange formats other apps read. Pure string builders — JVM-testable. */
object TrackFormats {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun iso(t: Long) = Instant.ofEpochMilli(t).toString()
    private fun d(v: Double, decimals: Int = 7) = String.format(Locale.US, "%.${decimals}f", v)

    private fun nearestMetric(metrics: List<MetricEntity>): (Long) -> MetricEntity? {
        if (metrics.isEmpty()) return { null }
        val bySecond = metrics.associateBy { it.time / 1000 }
        return { t -> bySecond[t / 1000] ?: bySecond[t / 1000 - 1] }
    }

    /**
     * GPX 1.1, one <trkseg> per recording segment (pauses split them). Heart rate and cadence go in Garmin's
     * TrackPointExtension, which Strava, Garmin Connect, Komoot and most others read.
     */
    fun gpx(full: FullSession, title: String): String {
        val s = full.session
        val metric = nearestMetric(full.metrics)
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="SpeedTrack" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
        ).append('\n')
        sb.append("  <metadata><name>").append(esc(title)).append("</name><time>").append(iso(s.startTime)).append("</time></metadata>\n")
        sb.append("  <trk>\n    <name>").append(esc(title)).append("</name>\n")
        sb.append("    <type>").append(gpxType(ActivityType.from(s.activity))).append("</type>\n")
        s.notes?.let { sb.append("    <desc>").append(esc(it)).append("</desc>\n") }
        full.points.groupBy { it.segment }.toSortedMap().values.forEach { seg ->
            sb.append("    <trkseg>\n")
            seg.forEach { p -> appendGpxPoint(sb, p, metric(p.time)) }
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun appendGpxPoint(sb: StringBuilder, p: TrackPointEntity, m: MetricEntity?) {
        sb.append("      <trkpt lat=\"").append(d(p.lat)).append("\" lon=\"").append(d(p.lon)).append("\">")
        (p.altitudeMsl ?: p.altitude)?.let { sb.append("<ele>").append(d(it, 1)).append("</ele>") }
        sb.append("<time>").append(iso(p.time)).append("</time>")
        p.hAcc?.let { sb.append("<hdop>").append(d(it / 5.0, 1)).append("</hdop>") }
        val hr = m?.heartRate
        val cad = m?.cadence
        if (hr != null || cad != null || p.speed != null) {
            sb.append("<extensions><gpxtpx:TrackPointExtension>")
            hr?.let { sb.append("<gpxtpx:hr>").append(it).append("</gpxtpx:hr>") }
            // GPX cadence is per-leg (rpm / strides); the step counter gives steps per minute.
            cad?.let { sb.append("<gpxtpx:cad>").append((it / 2).toInt()).append("</gpxtpx:cad>") }
            p.speed?.let { sb.append("<gpxtpx:speed>").append(d(it, 2)).append("</gpxtpx:speed>") }
            sb.append("</gpxtpx:TrackPointExtension></extensions>")
        }
        sb.append("</trkpt>\n")
    }

    private fun gpxType(a: ActivityType) = when (a) {
        ActivityType.Walk -> "walking"
        ActivityType.Run -> "running"
        ActivityType.Hike -> "hiking"
        ActivityType.Cycle -> "cycling"
        ActivityType.Drive -> "driving"
        ActivityType.Motorbike -> "motorcycling"
        ActivityType.Other -> "other"
    }

    /** Garmin TCX: a single lap per segment with HR and cadence — the format heart-rate data imports best in. */
    fun tcx(full: FullSession): String {
        val s = full.session
        val metric = nearestMetric(full.metrics)
        val sport = when (ActivityType.from(s.activity)) {
            ActivityType.Run, ActivityType.Walk, ActivityType.Hike -> "Running"
            ActivityType.Cycle -> "Biking"
            else -> "Other"
        }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""").append('\n')
        sb.append("  <Activities>\n    <Activity Sport=\"").append(sport).append("\">\n")
        sb.append("      <Id>").append(iso(s.startTime)).append("</Id>\n")
        val segments = full.points.groupBy { it.segment }.toSortedMap().values
        segments.forEach { seg ->
            if (seg.isEmpty()) return@forEach
            val first = seg.first()
            val last = seg.last()
            val hrs = seg.mapNotNull { metric(it.time)?.heartRate }
            sb.append("      <Lap StartTime=\"").append(iso(first.time)).append("\">\n")
            sb.append("        <TotalTimeSeconds>").append(d((last.time - first.time) / 1000.0, 1)).append("</TotalTimeSeconds>\n")
            sb.append("        <DistanceMeters>").append(d(last.distanceM - first.distanceM, 1)).append("</DistanceMeters>\n")
            sb.append("        <MaximumSpeed>").append(d(seg.maxOf { it.speed ?: 0.0 }, 2)).append("</MaximumSpeed>\n")
            sb.append("        <Calories>").append(((s.calories ?: 0.0) / segments.size).toInt()).append("</Calories>\n")
            if (hrs.isNotEmpty()) {
                sb.append("        <AverageHeartRateBpm><Value>").append(hrs.average().toInt()).append("</Value></AverageHeartRateBpm>\n")
                sb.append("        <MaximumHeartRateBpm><Value>").append(hrs.max()).append("</Value></MaximumHeartRateBpm>\n")
            }
            sb.append("        <Intensity>Active</Intensity>\n        <TriggerMethod>Manual</TriggerMethod>\n        <Track>\n")
            seg.forEach { p ->
                val m = metric(p.time)
                sb.append("          <Trackpoint><Time>").append(iso(p.time)).append("</Time>")
                sb.append("<Position><LatitudeDegrees>").append(d(p.lat)).append("</LatitudeDegrees><LongitudeDegrees>")
                    .append(d(p.lon)).append("</LongitudeDegrees></Position>")
                (p.altitudeMsl ?: p.altitude)?.let { sb.append("<AltitudeMeters>").append(d(it, 1)).append("</AltitudeMeters>") }
                sb.append("<DistanceMeters>").append(d(p.distanceM, 1)).append("</DistanceMeters>")
                m?.heartRate?.let { sb.append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>") }
                m?.cadence?.let { sb.append("<Cadence>").append((it / 2).toInt().coerceAtMost(254)).append("</Cadence>") }
                sb.append("</Trackpoint>\n")
            }
            sb.append("        </Track>\n      </Lap>\n")
        }
        sb.append("      <Creator xsi:type=\"Device_t\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><Name>SpeedTrack</Name></Creator>\n")
        sb.append("    </Activity>\n  </Activities>\n</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    /** KML for Google Earth / My Maps: the route as a LineString per segment, start and finish placemarks. */
    fun kml(full: FullSession, title: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
        sb.append("<name>").append(esc(title)).append("</name>\n")
        sb.append("<Style id=\"route\"><LineStyle><color>ffffd400</color><width>4</width></LineStyle></Style>\n")
        full.points.groupBy { it.segment }.toSortedMap().values.forEachIndexed { i, seg ->
            sb.append("<Placemark><name>").append(esc(title)).append(if (i > 0) " (${i + 1})" else "").append("</name>")
            sb.append("<styleUrl>#route</styleUrl><LineString><tessellate>1</tessellate><altitudeMode>clampToGround</altitudeMode><coordinates>\n")
            seg.forEach { p -> sb.append(d(p.lon)).append(',').append(d(p.lat)).append(',').append(d(p.altitudeMsl ?: p.altitude ?: 0.0, 1)).append('\n') }
            sb.append("</coordinates></LineString></Placemark>\n")
        }
        full.points.firstOrNull()?.let { sb.append(placemark("Start", it)) }
        full.points.lastOrNull()?.takeIf { full.points.size > 1 }?.let { sb.append(placemark("Finish", it)) }
        sb.append("</Document></kml>\n")
        return sb.toString()
    }

    private fun placemark(name: String, p: TrackPointEntity) =
        "<Placemark><name>$name</name><TimeStamp><when>${iso(p.time)}</when></TimeStamp><Point><coordinates>${d(p.lon)},${d(p.lat)}</coordinates></Point></Placemark>\n"
}
