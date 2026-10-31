package com.shivarya.locationspeedtracker.core.maps

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.maps.Style

/**
 * Makes rivers, main roads and town names readable at the zooms a flight (and an Overview offline area, z ≤ 7) is
 * seen at. The tiles already carry them — OpenMapTiles has rivers, motorways, trunk and primary roads from z5–z7 —
 * but OpenFreeMap's dark style draws rivers and water in rgb(27,27,29) on rgb(12,12,12) and main roads in #2a2a2a,
 * i.e. invisibly, and Liberty's rivers are hairlines below z11. Layer ids are OpenFreeMap's; a missing one is skipped.
 */
object StyleTweaks {
    fun apply(style: Style, dark: Boolean) {
        if (dark) {
            set(style, "water", fillColor("#13263A"))
            set(style, "waterway", lineColor("#2D5F8A"), lineWidth(ramp(4 to 0.8, 8 to 1.4, 12 to 2.2, 18 to 5.0)))
            set(style, "highway_motorway_subtle", lineColor("#5C4F38"))
            set(style, "highway_motorway_casing", lineColor("#6B5A3C"))
            set(style, "highway_motorway_inner", lineColor("#3B3224"))
            set(style, "highway_major_subtle", lineColor("#4D483F"), lineWidth(ramp(6 to 0.6, 8 to 1.6, 11 to 2.4)))
            set(style, "boundary_state", lineColor("#4A4A4A"))
            listOf("place_city_large", "place_city", "place_town", "place_village").forEach { set(style, it, textColor("#A3ACB2")) }
        } else {
            set(style, "waterway_river", lineWidth(ramp(4 to 0.8, 8 to 1.2, 11 to 1.5, 20 to 6.0)))
            set(style, "road_motorway", lineWidth(ramp(5 to 0.8, 7 to 1.6, 20 to 18.0)))
            set(style, "road_trunk_primary", lineWidth(ramp(5 to 0.6, 7 to 1.4, 20 to 18.0)))
        }
    }

    private fun set(style: Style, id: String, vararg props: PropertyValue<*>) {
        style.getLayer(id)?.setProperties(*props)
    }

    private fun ramp(vararg stops: Pair<Int, Double>): Expression =
        interpolate(linear(), zoom(), *stops.map { (z, w) -> stop(z, w) }.toTypedArray())
}
