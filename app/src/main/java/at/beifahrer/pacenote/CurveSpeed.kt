package at.beifahrer.pacenote

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Fahrstil = wie viel Seitenkraft (Querbeschleunigung in m/s²) in Kurven als angenehm gilt. */
enum class DriveStyle(val label: String, val lateral: Double) {
    GEMUETLICH("Gemütlich", 2.5), NORMAL("Normal", 3.5), SPORTLICH("Sportlich", 4.5)
}

/**
 * Empfohlenes Höchsttempo für eine Kurve.
 *
 *   v = Wurzel(Querbeschleunigung × Radius)
 *
 * Bei Nässe 30 % weniger Seitenkraft. Nie über dem Tempolimit, auf 5 km/h abgerundet, mindestens 15 km/h.
 * Das ist eine Empfehlung aus der Kartengeometrie (Schätzwert), keine Garantie: Straßenzustand,
 * Reifen, Beladung und Sicht entscheiden. Alle Stile bleiben deutlich unter der Haftgrenze trockener Straßen.
 */
object CurveSpeed {
    fun kmh(radius: Int, style: DriveStyle = DriveStyle.NORMAL, wet: Boolean = false, limitKmh: Int = 100): Int {
        val a = style.lateral * (if (wet) 0.7 else 1.0)
        val v = sqrt(a * max(radius, 5)) * 3.6
        return max(15, min(limitKmh, (floor(v / 5) * 5).toInt()))
    }

    fun forNote(n: PaceNote?, style: DriveStyle, wet: Boolean, limitKmh: Int): Int? =
        if (n?.type == NoteType.CURVE) kmh(n.radius, style, wet, limitKmh) else null
}
