package at.beifahrer.pacenote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Prüft die Kurven-Erkennung an künstlichen Straßen mit bekannten Radien. */
class PaceNoteEngineTest {

    /** Baut eine Straße aus Geraden und Bögen, abgetastet alle [every] Meter. */
    private fun road(vararg parts: Triple<Char, Double, Double>, every: Double = 8.0): List<RoutePoint> {
        var x = 0.0; var y = 0.0; var h = Math.PI / 2
        val pts = mutableListOf(RoutePoint(x, y)); var next = every; var s = 0.0
        for ((kind, a, b) in parts) {
            if (kind == 'S') repeat(a.toInt()) { x += cos(h); y += sin(h); s += 1; if (s >= next) { pts += RoutePoint(x, y); next += every } }
            else {
                val dir = if (kind == 'L') 1 else -1
                var turned = 0.0; val total = Math.toRadians(b)
                while (turned < total) { val dh = 1 / a; h += dir * dh; turned += dh; x += cos(h); y += sin(h); s += 1; if (s >= next) { pts += RoutePoint(x, y); next += every } }
            }
        }
        pts += RoutePoint(x, y)
        return pts
    }

    @Test fun haarnadelWirdErkannt() {
        val book = PaceNoteEngine.analyze(road(Triple('S', 200.0, 0.0), Triple('L', 14.0, 180.0), Triple('S', 200.0, 0.0)))
        val c = book.notes.single { it.type == NoteType.CURVE }
        assertTrue(c.hairpin)
        assertEquals(Dir.LEFT, c.dir)
    }

    @Test fun radienErgebenRichtigeStufe() {
        val cases = mapOf(30.0 to 2, 45.0 to 3, 70.0 to 4, 110.0 to 5, 200.0 to 6)
        for ((radius, grade) in cases) {
            val book = PaceNoteEngine.analyze(road(Triple('S', 200.0, 0.0), Triple('R', radius, 80.0), Triple('S', 200.0, 0.0)))
            val c = book.notes.single { it.type == NoteType.CURVE }
            assertEquals("Radius $radius", grade, c.grade)
            assertEquals(Dir.RIGHT, c.dir)
        }
    }

    @Test fun kurzeFolgeWirdVerbunden() {
        val book = PaceNoteEngine.analyze(road(Triple('S', 200.0, 0.0), Triple('L', 45.0, 90.0), Triple('R', 45.0, 90.0), Triple('S', 200.0, 0.0)))
        val curves = book.notes.filter { it.type == NoteType.CURVE }
        assertEquals(2, curves.size)
        assertEquals(Link.INTO, curves[0].link)
        assertEquals("Links 3 in", PaceText.spoken(curves[0], CallStyle.RALLY))
    }

    @Test fun kurventempo() {
        assertEquals(25, CurveSpeed.kmh(15))                       // Haarnadel, normal
        assertEquals(20, CurveSpeed.kmh(15, DriveStyle.GEMUETLICH))
        assertEquals(40, CurveSpeed.kmh(40))
        assertEquals(35, CurveSpeed.kmh(40, wet = true))
        assertEquals(100, CurveSpeed.kmh(300))                     // nie über dem Tempolimit
        assertEquals(80, CurveSpeed.kmh(300, limitKmh = 80))
    }

    @Test fun geradeOhneAnsage() {
        val book = PaceNoteEngine.analyze(road(Triple('S', 1000.0, 0.0)))
        assertTrue(book.notes.isEmpty())
    }
}
