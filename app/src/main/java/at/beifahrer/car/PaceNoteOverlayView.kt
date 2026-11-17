package at.beifahrer.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import at.beifahrer.nav.DriveState
import at.beifahrer.pacenote.Dir
import at.beifahrer.pacenote.Glyph
import at.beifahrer.pacenote.NoteType
import at.beifahrer.pacenote.PaceNote
import at.beifahrer.pacenote.PaceText
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zeichnet die Ansage-Karte direkt auf die Android-Auto-Karte.
 * Gleiche Optik wie am Handy, aber groß und kontrastreich für den Blick aufs Display.
 */
class PaceNoteOverlayView(context: Context) : View(context) {

    var visibleArea: Rect? = null
        set(value) { field = value; invalidate() }

    private var state: DriveState? = null
    private val d = resources.displayMetrics.density

    private val amber = Color.parseColor("#3FD8FF") // einheitlich Neon-Cyan
    private val red = Color.parseColor("#FF5A4F")
    private val blue = Color.parseColor("#3FD8FF")
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F00B1A36") }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    fun update(s: DriveState) {
        val old = state
        state = s
        if (old == null || old.overspeed != s.overspeed || old.currentVmax != s.currentVmax || old.current?.id != s.current?.id || (old.distToCurrent / 10).toInt() != (s.distToCurrent / 10).toInt() || old.style != s.style || old.book !== s.book) postInvalidate()
    }

    override fun onDraw(c: Canvas) {
        val st = state ?: return
        val n = st.current ?: return
        val area = visibleArea ?: Rect(0, 0, width, height)
        val w = (area.width() * 0.42f).coerceIn(260 * d, 380 * d)
        val h = 96 * d
        val left = area.right - w - 12 * d
        val top = area.top + 12 * d
        val accent = if (n.danger) red else amber
        val r = RectF(left, top, left + w, top + h)
        c.drawRoundRect(r, 16 * d, 16 * d, card)
        fill.color = accent
        c.drawRect(left, top + 8 * d, left + 5 * d, top + h - 8 * d, fill)

        // Symbol
        drawGlyph(c, n, left + 14 * d, top + 10 * d, 76 * d, accent)

        // Text
        val tx = left + 100 * d
        val t = PaceText.title(n, st.style).uppercase()
        title.textSize = if (t.length > 13) 22 * d else 30 * d
        if (n.danger) { small.color = red; small.textSize = 11 * d; c.drawText("VORSICHT", tx, top + 22 * d, small) }
        c.drawText(t, tx, top + 52 * d, title)
        val extra = (n.mods + n.dangerReasons.filter { it !in n.mods }).joinToString(" · ")
        if (extra.isNotEmpty()) { small.color = amber; small.textSize = 13 * d; c.drawText(extra, tx, top + 76 * d, small) }

        // Meter
        val dist = st.distToCurrent
        val ds = if (dist > 0) "${(dist / 10).roundToInt() * 10} m" else "jetzt"
        title.textSize = 24 * d
        val tw = title.measureText(ds)
        c.drawText(ds, left + w - tw - 12 * d, top + 52 * d, title)
        // Empfohlenes Höchsttempo (rot bei zu schnell)
        st.currentVmax?.takeIf { it < st.speedLimit && st.settings.vmaxShow != "off" }?.let { v ->
            val label = "max $v"
            small.textSize = 15 * d; small.color = if (st.overspeed) Color.WHITE else amber
            val lw = small.measureText(label) + 14 * d
            val bx = left + w - lw - 12 * d; val by = top + 62 * d
            val box = RectF(bx, by, bx + lw, by + 24 * d)
            if (st.overspeed) { fill.color = red; c.drawRoundRect(box, 6 * d, 6 * d, fill) }
            stroke.color = if (st.overspeed) red else amber; stroke.strokeWidth = 2 * d
            c.drawRoundRect(box, 6 * d, 6 * d, stroke)
            c.drawText(label, bx + 7 * d, by + 17 * d, small)
        }
        // Countdown
        fill.color = accent
        val frac = (1 - dist / 250).coerceIn(0.0, 1.0).toFloat()
        c.drawRect(left + 5 * d, top + h - 4 * d, left + 5 * d + (w - 5 * d) * frac, top + h, fill)
    }

    private fun drawGlyph(c: Canvas, n: PaceNote, x: Float, y: Float, size: Float, color: Int) {
        val u = size / 100f
        c.save(); c.translate(x, y)
        when (n.type) {
            NoteType.HAZARD -> {
                stroke.color = red; stroke.strokeWidth = 9 * u
                c.drawPath(Path().apply { moveTo(50 * u, 16 * u); lineTo(88 * u, 82 * u); lineTo(12 * u, 82 * u); close() }, stroke)
            }
            NoteType.VERTICAL -> {
                stroke.color = blue; stroke.strokeWidth = 9 * u
                val top = if (n.strength < 0) 25 * u else 92 * u
                c.drawPath(Path().apply { moveTo(8 * u, 70 * u); cubicTo(35 * u, 70 * u, 35 * u, top, 50 * u, top); cubicTo(65 * u, top, 65 * u, 70 * u, 92 * u, 70 * u) }, stroke)
            }
            NoteType.CURVE -> {
                val g = Glyph.curve(n)
                val p = Path()
                g.points.forEachIndexed { i, (px, py) -> if (i == 0) p.moveTo(px * u, py * u) else p.lineTo(px * u, py * u) }
                stroke.color = Color.argb(30, 255, 255, 255); stroke.strokeWidth = 17 * u; c.drawPath(p, stroke)
                stroke.color = color; stroke.strokeWidth = 8 * u; c.drawPath(p, stroke)
                val hh = g.tipHeading; val ah = 11 * u; val tx = g.tipX * u; val ty = g.tipY * u
                fill.color = color
                c.drawPath(Path().apply {
                    moveTo(tx + cos(hh) * ah, ty + sin(hh) * ah)
                    lineTo(tx + cos(hh + 2.3f) * ah, ty + sin(hh + 2.3f) * ah)
                    lineTo(tx + cos(hh - 2.3f) * ah, ty + sin(hh - 2.3f) * ah); close()
                }, fill)
                val cx = if (n.dir == Dir.LEFT) 80 * u else 20 * u; val cy = 80 * u
                fill.color = Color.parseColor("#061026"); c.drawCircle(cx, cy, 17 * u, fill)
                title.textSize = 24 * u
                val b = Glyph.badge(n); c.drawText(b, cx - title.measureText(b) / 2, cy + 8 * u, title)
            }
        }
        c.restore()
    }
}
