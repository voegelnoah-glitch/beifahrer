package at.beifahrer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nacht-Cockpit wie in der Vorlage: tiefes Navy, alles Normale in einem Neon-Cyan, Gefahren rot/orange.
 */
object C {
    val Bg = Color(0xFF061026)
    val Panel = Color(0xF00B1A36)
    val Panel2 = Color(0xFF10284F)
    val Line = Color(0xFF1D3F70)
    val Ink = Color(0xFFE6F3FF)
    val Muted = Color(0xFF7D98C0)
    val Accent = Color(0xFF3FD8FF)        // einheitlich für alles Normale
    val AccentInk = Color(0xFF03203A)
    val Route = Accent
    val Danger = Color(0xFFFF5A4F)
    val Vertical = Accent
    val Banner = Panel2
    val WarmTop = Color(0xFFFFC3AE)
    val WarmBottom = Color(0xFFFF9A7D)
    val WarmInk = Color(0xFF6A1610)
}

/** Titel: schmal, fett, kursiv, Großbuchstaben (wie "GEFAHR MELDEN"). */
val TitleStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, fontSize = 26.sp, letterSpacing = 0.5.sp)

@Composable
fun ScreenTitle(text: String) = Text(text.uppercase(), style = TitleStyle, color = C.Ink)

/** Großer Knopf in Cyan (z. B. "FAHRT STARTEN", "STIMME MAX ÜBERNEHMEN"). */
@Composable
fun CyanButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = C.Accent, spotColor = C.Accent)
            .clip(RoundedCornerShape(14.dp)).background(C.Accent).clickable(onClick = onClick).padding(15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), style = TitleStyle.copy(fontSize = 17.sp, letterSpacing = 1.5.sp), color = C.AccentInk) }
}

/** Warmer Knopf für Gefahren (z. B. "BODENWELLE MELDEN"). */
@Composable
fun WarmButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(C.WarmTop, C.WarmBottom))).clickable(onClick = onClick).padding(15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), style = TitleStyle.copy(fontSize = 17.sp, letterSpacing = 1.5.sp), color = C.WarmInk) }
}

/** Segment-Auswahl wie in der Vorlage: gewählt = Cyan-Fläche. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) C.Accent else Color.Transparent)
                    .clickable { onSelect(value) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) C.AccentInk else C.Muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
        }
    }
}

@Composable
fun BeifahrerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.AccentInk, secondary = C.Accent,
            background = C.Bg, surface = C.Panel, onSurface = C.Ink, error = C.Danger,
        ),
        content = content,
    )
}
