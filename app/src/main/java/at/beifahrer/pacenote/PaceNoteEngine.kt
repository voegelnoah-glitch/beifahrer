package at.beifahrer.pacenote

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Herzstück der App: macht aus einer Route (Punkte in Metern + Höhe) ein "Gebetbuch",
 * also die Liste aller Ansagen. Reines Kotlin ohne Android – lässt sich mit Unit-Tests prüfen.
 *
 * Ablauf:
 *  1. Route alle 5 m neu abtasten
 *  2. Krümmung über Kreis durch drei Punkte (15 m Abstand) berechnen
 *  3. Abschnitte mit gleicher Richtung und Radius < 350 m = Kurve
 *  4. Radius -> Rallye-Skala 1 (eng) bis 6 (schnell), Haarnadel, Quadrat (90°)
 *  5. Zusätze: zieht zu, öffnet, lang, steil bergab, Kuppe davor/darin
 *  6. Kuppen/Senken aus dem Höhenprofil, gemeldete Gefahren dazumischen
 *  7. Verbindungen "in"/"und" und Distanzen ("150") zur nächsten Ansage
 */
object PaceNoteEngine {

    data class Config(
        val step: Double = 5.0,
        val curveMaxRadius: Double = 350.0,
        val minAngleDeg: Double = 14.0,
        val mergeGap: Double = 15.0,
        val linkIn: Double = 25.0,
        val linkAnd: Double = 60.0,
    )

    /** Obergrenze des Radius je Stufe, 1 = sehr eng. */
    private val GRADES = listOf(1 to 20.0, 2 to 35.0, 3 to 55.0, 4 to 85.0, 5 to 130.0, 6 to 350.0)

    fun analyze(raw: List<RoutePoint>, hazards: List<RouteHazard> = emptyList(), cfg: Config = Config()): PaceBook {
        require(raw.size >= 2) { "Route braucht mindestens zwei Punkte" }
        val p = resample(raw, cfg.step)
        val n = p.size

        // --- Krümmung (1/m, + = links) ---
        val m = 3
        val kRaw = DoubleArray(n)
        for (i in m until n - m) {
            val a = p[i - m]; val b = p[i]; val c = p[i + m]
            val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
            val ab = hypot(b.x - a.x, b.y - a.y)
            val bc = hypot(c.x - b.x, c.y - b.y)
            val ca = hypot(a.x - c.x, a.y - c.y)
            kRaw[i] = 2 * cross / max(ab * bc * ca, 1e-9)
        }
        val k = smooth(kRaw, 1)
        val z = smooth(DoubleArray(n) { p[it].z }, 4)
        val grade = DoubleArray(n) { if (it == 0) 0.0 else (z[it] - z[it - 1]) / cfg.step }
        val gradeS = smooth(grade, 3)

        // --- Kurven-Abschnitte ---
        val kMin = 1 / cfg.curveMaxRadius
        data class Group(val sign: Int, val start: Int, var end: Int)
        val groups = mutableListOf<Group>()
        var cur: Group? = null
        for (i in 0 until n) {
            val s = if (abs(k[i]) > kMin) sign(k[i]).toInt() else 0
            if (s != 0 && cur != null && cur.sign == s) cur.end = i
            else {
                cur?.let { groups += it }
                cur = if (s != 0) Group(s, i, i) else null
            }
        }
        cur?.let { groups += it }
        val merged = mutableListOf<Group>()
        for (g in groups) {
            val last = merged.lastOrNull()
            if (last != null && last.sign == g.sign && (g.start - last.end) * cfg.step <= cfg.mergeGap) last.end = g.end
            else merged += g.copy()
        }

        val curves = mutableListOf<PaceNote>()
        for (g in merged) {
            var angle = 0.0; var kmax = 0.0
            for (i in g.start..g.end) { angle += k[i] * cfg.step; kmax = max(kmax, abs(k[i])) }
            val angDeg = abs(angle) * 180 / PI
            if (angDeg < cfg.minAngleDeg) continue

            var c0 = g.start; var c1 = g.end
            while (abs(k[c0]) < 0.6 * kmax) c0++
            while (abs(k[c1]) < 0.6 * kmax) c1--
            var coreLen = 0.0; var coreAng = 0.0
            for (i in c0..c1) { coreLen += cfg.step; coreAng += abs(k[i]) * cfg.step }
            val rCore = coreLen / max(coreAng, 1e-6)
            val radius = 0.5 * (rCore + 1 / kmax)

            val len = g.end - g.start + 1
            val coreN = c1 - c0 + 1
            val third = max(1, coreN / 3)
            fun avgK(a: Int, b: Int): Double { var s = 0.0; var c = 0; for (i in a until b) { s += abs(k[i]); c++ }; return s / max(c, 1) }
            val kIn = avgK(c0, c0 + third)
            val kOut = avgK(c1 - third + 1, c1 + 1)
            var apex = g.start
            for (i in g.start..g.end) if (abs(k[i]) > abs(k[apex])) apex = i
            var slope = 0.0
            for (i in g.start..g.end) slope += gradeS[i]
            slope /= len

            val gr = GRADES.firstOrNull { radius < it.second }?.first ?: 6
            val hairpin = angDeg >= 135 && radius <= 30
            val square = !hairpin && angDeg in 75.0..105.0 && radius <= 25
            val mods = mutableListOf<String>()
            if (coreN >= 6 && angDeg >= 40 && kOut > kIn * 1.3) mods += "zieht zu"
            else if (coreN >= 6 && angDeg >= 40 && kIn > kOut * 1.3) mods += "öffnet"
            if ((g.end - g.start) * cfg.step > 140 && !hairpin) mods += "lang"
            if (slope < -0.07) mods += "steil bergab" else if (slope > 0.11) mods += "steil bergauf"

            curves += PaceNote(
                type = NoteType.CURVE, s = p[g.start].s, sApex = p[apex].s, sEnd = p[g.end].s,
                dir = if (g.sign > 0) Dir.LEFT else Dir.RIGHT, grade = gr, hairpin = hairpin, square = square,
                radius = radius.roundToInt(), angle = angDeg.roundToInt(), slope = slope, mods = mods,
            )
        }

        // --- Kuppen / Senken ---
        val w = (30 / cfg.step).roundToInt()
        data class Ext(val s: Double, val change: Double)
        val vert = mutableListOf<Pair<Int, Double>>()
        for (i in w until n - w) {
            val before = (z[i] - z[i - w]) / (w * cfg.step)
            val after = (z[i + w] - z[i]) / (w * cfg.step)
            vert += i to (after - before)
        }
        val ext = mutableListOf<Ext>()
        for (j in 1 until vert.size - 1) {
            val v = abs(vert[j].second)
            if (v < 0.045) continue
            if (v < abs(vert[j - 1].second) || v < abs(vert[j + 1].second)) continue
            ext += Ext(p[vert[j].first].s, vert[j].second)
        }
        val others = mutableListOf<PaceNote>()
        for (e in ext) {
            val stronger = ext.any { o -> o !== e && sign(o.change) != sign(e.change) && abs(o.s - e.s) < 70 && abs(o.change) > abs(e.change) }
            if (stronger) continue
            if (others.isNotEmpty() && e.s - others.last().s < 40) continue
            val kind = if (e.change < 0) (if (e.change < -0.09) "Kuppe" else "leichte Kuppe")
            else (if (e.change > 0.09) "Senke" else "Mulde")
            others += PaceNote(type = NoteType.VERTICAL, s = e.s, sApex = e.s, sEnd = e.s, kind = kind, strength = e.change)
        }
        for (h in hazards) others += PaceNote(type = NoteType.HAZARD, s = h.s, sApex = h.s, sEnd = h.s, kind = h.label, danger = true, sensor = h.fromSensor)
        others.sortBy { it.s }

        // Kuppe vor/in einer Kurve an die Kurve hängen
        val free = mutableListOf<PaceNote>()
        for (c in others) {
            val host = curves.firstOrNull { c.s >= it.s - 45 && c.s <= it.sEnd }
            if (host != null && c.type == NoteType.VERTICAL && c.strength < 0) {
                if (c.s < host.s) { if (!host.blindCrest) { host.blindCrest = true; host.mods.add(0, "nach Kuppe") } }
                else if ("über Kuppe" !in host.mods) host.mods += "über Kuppe"
            } else free += c
        }

        // Gefahren bewerten
        var prevEnd = 0.0
        for (cv in curves) {
            val approach = cv.s - prevEnd
            val reasons = mutableListOf<String>()
            if (cv.grade <= 2 && approach > 300) reasons += "nach langer Geraden"
            if ("zieht zu" in cv.mods && cv.grade <= 4) reasons += "zieht zu"
            if (cv.blindCrest && cv.grade <= 4) reasons += "nicht einsehbar"
            if (cv.hairpin && cv.slope < -0.05) reasons += "Haarnadel bergab"
            cv.danger = reasons.isNotEmpty(); cv.dangerReasons = reasons
            prevEnd = cv.sEnd
        }

        val notes = (curves + free).sortedBy { it.s }
        for (i in notes.indices) {
            val a = notes[i]; val b = notes.getOrNull(i + 1) ?: continue
            val gap = b.s - a.sEnd
            when {
                a.type == NoteType.CURVE && b.type == NoteType.CURVE && gap <= cfg.linkIn -> a.link = Link.INTO
                a.type == NoteType.CURVE && b.type == NoteType.CURVE && gap <= cfg.linkAnd -> a.link = Link.AND
                gap >= 90 -> a.distAfter = if (gap >= 500) 500 else ((gap / 50).roundToInt() * 50)
            }
        }
        notes.forEachIndexed { i, nt -> nt.id = i }
        return PaceBook(points = p, notes = notes, length = p.last().s)
    }

    // ---------- Hilfsfunktionen ----------

    fun resample(pts: List<RoutePoint>, step: Double): List<RoutePoint> {
        val out = ArrayList<RoutePoint>()
        out += pts[0].copy(s = 0.0)
        var s = 0.0; var carry = 0.0
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            if (d == 0.0) continue
            var t = step - carry
            while (t <= d) {
                val f = t / d
                s += step
                out += RoutePoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f, s)
                t += step
            }
            carry = d - (t - step)
        }
        return out
    }

    private fun smooth(arr: DoubleArray, half: Int): DoubleArray = DoubleArray(arr.size) { i ->
        var sum = 0.0; var w = 0.0
        for (k in -half..half) {
            val j = i + k
            if (j < 0 || j >= arr.size) continue
            val wk = (half + 1 - abs(k)).toDouble()
            sum += arr[j] * wk; w += wk
        }
        sum / w
    }

    /** Index-Hilfe: Punkt in der Liste zu einer Streckenposition. */
    fun indexAt(book: PaceBook, s: Double): Int = floor(s / 5.0).toInt().coerceIn(0, book.points.size - 1)

    fun clampDist(d: Double) = min(max(d, 0.0), 9_999.0)
}
