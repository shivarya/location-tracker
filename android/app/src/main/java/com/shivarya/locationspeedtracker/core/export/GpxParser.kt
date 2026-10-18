package com.shivarya.locationspeedtracker.core.export

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.SAXParserFactory

data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: Long? = null,
    val hr: Int? = null,
    /** Per-leg cadence as written in GPX (rpm / strides per minute). */
    val cad: Int? = null,
    val speed: Double? = null,
)

data class GpxTrack(val name: String?, val type: String?, val segments: List<List<GpxPoint>>)

/**
 * Minimal GPX 1.0/1.1 reader: tracks (trk/trkseg/trkpt) and routes (rte/rtept), with Garmin/Strava extensions for
 * heart rate, cadence and speed. SAX + local names, so namespace prefixes don't matter; runs on the JVM for tests.
 */
object GpxParser {
    fun parse(input: InputStream): GpxTrack {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // No DTDs / external entities — imported files are untrusted.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        }
        val handler = Handler()
        factory.newSAXParser().parse(input, handler)
        return GpxTrack(handler.name, handler.type, handler.segments.filter { it.isNotEmpty() })
    }

    private class Handler : DefaultHandler() {
        var name: String? = null
        var type: String? = null
        val segments = mutableListOf<MutableList<GpxPoint>>()
        private var current: MutableList<GpxPoint>? = null
        private var lat = 0.0
        private var lon = 0.0
        private var ele: Double? = null
        private var time: Long? = null
        private var hr: Int? = null
        private var cad: Int? = null
        private var speed: Double? = null
        private var inPoint = false
        private val text = StringBuilder()
        private val stack = ArrayDeque<String>()

        override fun startElement(uri: String?, localName: String, qName: String?, attrs: Attributes) {
            val tag = localName.ifEmpty { qName?.substringAfter(':') ?: "" }
            stack.addLast(tag)
            text.setLength(0)
            when (tag) {
                "trkseg", "rte" -> current = mutableListOf<GpxPoint>().also { segments += it }
                "trkpt", "rtept" -> {
                    inPoint = true
                    lat = attrs.getValue("lat")?.toDoubleOrNull() ?: Double.NaN
                    lon = attrs.getValue("lon")?.toDoubleOrNull() ?: Double.NaN
                    ele = null; time = null; hr = null; cad = null; speed = null
                    if (current == null) current = mutableListOf<GpxPoint>().also { segments += it }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            val tag = localName.ifEmpty { qName?.substringAfter(':') ?: "" }
            val value = text.toString().trim()
            val parent = stack.getOrNull(stack.size - 2)
            when {
                inPoint && tag == "ele" -> ele = value.toDoubleOrNull()
                inPoint && tag == "time" -> time = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                inPoint && tag == "hr" -> hr = value.toDoubleOrNull()?.toInt()
                inPoint && (tag == "cad" || tag == "cadence") -> cad = value.toDoubleOrNull()?.toInt()
                inPoint && tag == "speed" -> speed = value.toDoubleOrNull()
                tag == "trkpt" || tag == "rtept" -> {
                    inPoint = false
                    if (lat.isFinite() && lon.isFinite() && kotlin.math.abs(lat) <= 90 && kotlin.math.abs(lon) <= 180) {
                        current?.add(GpxPoint(lat, lon, ele, time, hr, cad, speed))
                    }
                }
                tag == "trkseg" || tag == "rte" -> current = null
                tag == "name" && name == null && (parent == "trk" || parent == "metadata" || parent == "rte") -> name = value.ifEmpty { null }
                tag == "type" && parent == "trk" -> type = value.ifEmpty { null }
            }
            stack.removeLastOrNull()
            text.setLength(0)
        }
    }
}
