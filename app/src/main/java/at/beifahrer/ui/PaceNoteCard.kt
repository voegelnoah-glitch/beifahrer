package at.beifahrer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.beifahrer.pacenote.CallStyle
import at.beifahrer.pacenote.Glyph
import at.beifahrer.pacenote.NoteType
import at.beifahrer.pacenote.PaceNote
import at.beifahrer.pacenote.PaceText
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Die große Ansage-Karte: Symbol, "Rechts 3", Zusätze, Meter bis zur Kurve. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaceNoteCard(note: PaceNote?, dist: Double, style: CallStyle, modifier: Modifier = Modifier, vmax: Int? = null, overspeed: Boolean = false) {
    val danger = note?.danger == true
    val stripe = if (danger) C.Danger else C.Accent
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Panel)
            .border(1.dp, if (danger || overspeed) C.Danger.copy(alpha = .7f) else C.Line, RoundedCornerShape(18.dp))
    ) {
        // Farbstreifen links: matchParentSize, damit die Karte nicht auf volle Bildschirmhöhe wächst
        Box(Modifier.matchParentSize()) { Box(Modifier.width(5.dp).fillMaxHeight().background(stripe)) }
        Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NoteGlyph(note, Modifier.size(84.dp))
            Column(Modifier.weight(1f)) {
                if (danger) Text("VORSICHT", color = C.Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                val title = (note?.let { PaceText.title(it, style) } ?: "Ziel erreicht").uppercase()
                Text(title, style = TitleStyle.copy(fontSize = if (title.length > 13) 21.sp else 28.sp, lineHeight = if (title.length > 13) 22.sp else 28.sp), color = C.Ink)
                val chips = note?.let { n -> n.mods + n.dangerReasons.filter { it !in n.mods } } ?: emptyList()
                if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.forEach { c ->
                        val red = note != null && c in note.dangerReasons && c !in note.mods
                        Text(c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (red) Color(0xFFFF8A8A) else C.Accent,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background((if (red) C.Danger else C.Accent).copy(alpha = .15f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
            if (note != null) Column(horizontalAlignment = Alignment.End) {
                if (dist > 0) {
                    Text("${(dist / 10).roundToInt() * 10}", color = C.Ink, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                    Text("m", color = C.Muted, fontSize = 14.sp)
                } else Text("jetzt", color = stripe, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                // Empfohlenes Höchsttempo – rot, wenn man gerade zu schnell ist
                if (vmax != null) Text("max $vmax", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (overspeed) C.Ink else C.Accent,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (overspeed) C.Danger else Color.Transparent)
                        .border(2.dp, if (overspeed) C.Danger else C.Accent, RoundedCornerShape(7.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        // Countdown-Balken (letzte 250 m)
        val frac = (1 - dist / 250).coerceIn(0.0, 1.0).toFloat()
        Box(Modifier.align(Alignment.BottomStart).padding(start = 5.dp).fillMaxWidth(frac).height(4.dp).background(stripe))
    }
}

@Composable
fun NoteGlyph(note: PaceNote?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val u = size.minDimension / 100f
        if (note == null) return@Canvas
        when (note.type) {
            NoteType.HAZARD -> drawHazard(u)
            NoteType.VERTICAL -> drawCrest(u, note.strength < 0)
            NoteType.CURVE -> {
                val col = if (note.danger) C.Danger else C.Accent
                val shape = Glyph.curve(note)
                val path = Path().apply {
                    shape.points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
                }
                drawPath(path, Color.White.copy(alpha = .12f), style = Stroke(17 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, col, style = Stroke(8 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val h = shape.tipHeading; val ah = 11 * u
                val tx = shape.tipX * u; val ty = shape.tipY * u
                drawPath(Path().apply {
                    moveTo(tx + cos(h) * ah, ty + sin(h) * ah)
                    lineTo(tx + cos(h + 2.3f) * ah, ty + sin(h + 2.3f) * ah)
                    lineTo(tx + cos(h - 2.3f) * ah, ty + sin(h - 2.3f) * ah); close()
                }, col)
                // Zahl im Kreis
                val c = Offset(if (note.dir == at.beifahrer.pacenote.Dir.LEFT) 80 * u else 20 * u, 80 * u)
                drawCircle(C.Bg, 17 * u, c)
                val txt = measurer.measure(Glyph.badge(note), TextStyle(color = col, fontSize = (26 * u).toSp(), fontWeight = FontWeight.ExtraBold))
                drawText(txt, topLeft = Offset(c.x - txt.size.width / 2f, c.y - txt.size.height / 2f))
            }
        }
    }
}

private fun DrawScope.drawHazard(u: Float) {
    val p = Path().apply { moveTo(50 * u, 16 * u); lineTo(88 * u, 82 * u); lineTo(12 * u, 82 * u); close() }
    drawPath(p, C.Danger, style = Stroke(9 * u, join = StrokeJoin.Round))
    drawRect(C.Danger, Offset(46 * u, 40 * u), androidx.compose.ui.geometry.Size(8 * u, 22 * u))
    drawRect(C.Danger, Offset(46 * u, 67 * u), androidx.compose.ui.geometry.Size(8 * u, 7 * u))
}

private fun DrawScope.drawCrest(u: Float, crest: Boolean) {
    val top = if (crest) 25 * u else 92 * u
    val p = Path().apply {
        moveTo(8 * u, 70 * u)
        cubicTo(35 * u, 70 * u, 35 * u, top, 50 * u, top)
        cubicTo(65 * u, top, 65 * u, 70 * u, 92 * u, 70 * u)
    }
    drawPath(p, C.Vertical, style = Stroke(9 * u, cap = StrokeCap.Round))
}
