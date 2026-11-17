package at.beifahrer.pacenote

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Form des Kurven-Symbols (Einfahrt von unten, Bogen, Pfeilspitze) in einem 100x100-Feld.
 * Wird vom Handy (Compose) und von Android Auto (Canvas) gleich gezeichnet.
 */
object Glyph {
    data class Shape(val points: List<Pair<Float, Float>>, val tipX: Float, val tipY: Float, val tipHeading: Float, val scale: Float)

    fun curve(n: PaceNote): Shape {
        val dir = if (n.dir == Dir.LEFT) -1 else 1
        val ang = min(n.angle, 185) * Math.PI / 180
        val r0 = if (n.hairpin) 13.0 else doubleArrayOf(0.0, 9.0, 14.0, 20.0, 27.0, 35.0, 46.0)[n.grade.coerceIn(1, 6)]
        val tight = when { "zieht zu" in n.mods -> 0.55; "öffnet" in n.mods -> 1.6; else -> 1.0 }
        var x = 50.0 - dir * (if (n.hairpin) 14.0 else 8.0)
        var y = 92.0
        var h = -Math.PI / 2
        val pts = mutableListOf(x to y)
        y -= if (n.hairpin) 26.0 else 22.0
        pts += x to y
        val steps = 40
        for (i in 1..steps) {
            val r = r0 * (1 + (tight - 1) * (i.toDouble() / steps))
            val dh = ang / steps
            h += dir * dh; x += cos(h) * r * dh; y += sin(h) * r * dh
            pts += x to y
        }
        x += cos(h) * 10; y += sin(h) * 10; pts += x to y
        // ins Feld einpassen
        val minX = pts.minOf { it.first }; val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }; val maxY = pts.maxOf { it.second }
        val s = min(1.0, 74.0 / max(maxX - minX, maxY - minY))
        val ox = 50 - (minX + maxX) / 2 * s; val oy = 50 - (minY + maxY) / 2 * s
        val mapped = pts.map { (px, py) -> (ox + px * s).toFloat() to (oy + py * s).toFloat() }
        return Shape(mapped, (ox + x * s).toFloat(), (oy + y * s).toFloat(), h.toFloat(), s.toFloat())
    }

    fun badge(n: PaceNote): String = when {
        n.hairpin -> "HN"; n.square -> "Q"; else -> n.grade.toString()
    }
}
