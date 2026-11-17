package at.beifahrer.data

import at.beifahrer.pacenote.Geo
import at.beifahrer.pacenote.LocalProjection
import at.beifahrer.pacenote.PaceNoteEngine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.hypot

/**
 * Holt Höhen entlang der Route (Open-Meteo, kostenlos und ohne Schlüssel) – daraus entstehen Kuppen, Senken und "steil bergab".
 * Abtastung alle 20 m, bis zu 100 Punkte pro Anfrage. Das Geländemodell ist grob, deshalb wird leicht geglättet.
 */
class ElevationClient {

    /** Liefert für jeden Routenpunkt eine Höhe (Meter). Blockierend – im IO-Thread aufrufen. */
    fun heightsAlong(route: List<Geo>, proj: LocalProjection): DoubleArray {
        // Route in Metern, mit Streckenposition
        val local = route.map { proj.toLocal(it) }
        val withS = ArrayList<Pair<Double, Geo>>(local.size)
        var s = 0.0
        local.forEachIndexed { i, p ->
            if (i > 0) s += hypot(p.x - local[i - 1].x, p.y - local[i - 1].y)
            withS += s to route[i]
        }
        // gleichmäßig alle 20 m abtasten
        val samples = PaceNoteEngine.resample(local, SAMPLE_M)
        val sampleGeo = samples.map { proj.toGeo(it) }
        val heights = DoubleArray(sampleGeo.size)
        sampleGeo.chunked(BATCH).forEachIndexed { b, chunk ->
            val lat = chunk.joinToString(",") { "%.5f".format(java.util.Locale.ROOT, it.lat) }
            val lng = chunk.joinToString(",") { "%.5f".format(java.util.Locale.ROOT, it.lng) }
            val res = JSONObject(fetch("https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lng")).getJSONArray("elevation")
            for (i in 0 until res.length()) heights[b * BATCH + i] = res.getDouble(i)
        }
        // Rauschen des Geländemodells glätten (gleitender Mittelwert über ~60 m)
        val c = heights.copyOf()
        for (i in 1 until heights.size - 1) heights[i] = 0.25 * c[i - 1] + 0.5 * c[i] + 0.25 * c[i + 1]
        // zurück auf die Original-Punkte interpolieren
        return DoubleArray(route.size) { i ->
            val pos = withS[i].first / SAMPLE_M
            val a = pos.toInt().coerceIn(0, heights.size - 1)
            val b = (a + 1).coerceAtMost(heights.size - 1)
            val t = pos - a
            heights[a] + (heights[b] - heights[a]) * t
        }
    }

    /** GET mit eigener Kennung und einem zweiten Versuch (Mobilfunk bricht gern mal ab). */
    private fun fetch(url: String): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val con = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("User-Agent", "Beifahrer-App/1.0 (Android)")
                    setRequestProperty("Accept", "application/json")
                }
                val code = con.responseCode
                if (code == 200) return con.inputStream.bufferedReader().use { it.readText() }
                last = IllegalStateException("HTTP $code")
            } catch (e: Exception) { last = e }
            if (attempt == 0) Thread.sleep(800)
        }
        throw last ?: IllegalStateException("unbekannt")
    }

    companion object {
        private const val SAMPLE_M = 20.0
        private const val BATCH = 100
    }
}
