package at.beifahrer.pacenote

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

/** Einfacher Breiten-/Längengrad, unabhängig von Google-Klassen (damit testbar). */
data class Geo(val lat: Double, val lng: Double)

/**
 * Rechnet Längen-/Breitengrade in ein lokales Meter-Raster um (genau genug für Routen bis ~150 km)
 * und findet die aktuelle Position auf der Route.
 */
class LocalProjection(private val origin: Geo) {
    private val mPerDegLat = 110_574.0
    private val mPerDegLng = 111_320.0 * cos(origin.lat * PI / 180)

    fun toLocal(g: Geo, z: Double = 0.0) = RoutePoint((g.lng - origin.lng) * mPerDegLng, (g.lat - origin.lat) * mPerDegLat, z)
    fun toGeo(p: RoutePoint) = Geo(origin.lat + p.y / mPerDegLat, origin.lng + p.x / mPerDegLng)
}

/**
 * Verfolgt das Auto entlang der analysierten Route.
 * Sucht nur in der Nähe der letzten Position, damit Serpentinen (Straße liegt direkt daneben) nicht verwechselt werden.
 */
class RouteTracker(private val book: PaceBook, private val proj: LocalProjection) {
    private var lastIdx = 0

    /** Gibt die Streckenposition in Metern zurück, oder null wenn das Auto mehr als [maxOffRoute] m neben der Route ist. */
    fun locate(g: Geo, maxOffRoute: Double = 40.0): Double? {
        val p = proj.toLocal(g)
        val pts = book.points
        val from = (lastIdx - 10).coerceAtLeast(0)
        val to = (lastIdx + 120).coerceAtMost(pts.size - 2) // 600 m nach vorne
        var best = Double.MAX_VALUE; var bestS = 0.0; var bestI = lastIdx
        for (i in from..to) {
            val a = pts[i]; val b = pts[i + 1]
            val dx = b.x - a.x; val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(a.x + dx * t - p.x, a.y + dy * t - p.y)
            if (d < best) { best = d; bestS = a.s + (b.s - a.s) * t; bestI = i }
        }
        if (best > maxOffRoute) return null
        lastIdx = bestI
        return bestS
    }

    fun reset() { lastIdx = 0 }
}

/** Projiziert gespeicherte Gefahrenstellen auf die Route (nur wenn höchstens 25 m daneben). */
fun projectHazards(book: PaceBook, proj: LocalProjection, hazards: List<Pair<Geo, String>>, sensorFlags: List<Boolean>): List<RouteHazard> {
    val out = mutableListOf<RouteHazard>()
    hazards.forEachIndexed { idx, (g, label) ->
        val p = proj.toLocal(g)
        var best = Double.MAX_VALUE; var bestS = 0.0
        for (q in book.points) {
            val d = hypot(q.x - p.x, q.y - p.y)
            if (d < best) { best = d; bestS = q.s }
        }
        if (best <= 25.0) out += RouteHazard(bestS, label, sensorFlags.getOrElse(idx) { false })
    }
    return out
}

/** Google "Encoded Polyline" – für die Elevation-API-Anfrage. */
object PolylineCodec {
    fun encode(points: List<Geo>): String {
        val sb = StringBuilder()
        var pLat = 0L; var pLng = 0L
        for (g in points) {
            val lat = (g.lat * 1e5).roundToLong(); val lng = (g.lng * 1e5).roundToLong()
            enc(lat - pLat, sb); enc(lng - pLng, sb)
            pLat = lat; pLng = lng
        }
        return sb.toString()
    }

    private fun enc(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }
        sb.append((x + 63).toInt().toChar())
    }
}
