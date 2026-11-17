package at.beifahrer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.beifahrer.data.CloudHazard
import at.beifahrer.data.HAZARD_KINDS
import at.beifahrer.nav.DriveController
import at.beifahrer.nav.DriveState
import java.util.Date

/**
 * Seite "GEFAHR MELDEN" wie in der Vorlage: Ort, Art (Kacheln), Notiz, Sichtbarkeit, großer Melde-Knopf.
 * Den Ort wählt man vorher durch langes Drücken auf die Karte – oder es ist die aktuelle Position.
 */
@Composable
fun HazardReportScreen(st: DriveState, whereText: String, onSave: (kind: String, note: String, vis: String) -> Unit, onMovePin: (() -> Unit)?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var kind by remember { mutableStateOf(HAZARD_KINDS.first()) }
    var note by remember { mutableStateOf("") }
    var vis by remember { mutableStateOf(st.settings.vis) }
    Column(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = C.Ink) }
            Column { ScreenTitle("Gefahr melden"); Text("Geht über die Cloud an andere Fahrer", color = C.Muted, fontSize = 12.sp) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Ort
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 10.dp).clip(RoundedCornerShape(50)).background(C.WarmBottom).padding(7.dp)) {}
                Text("Hier · $whereText", color = C.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                onMovePin?.let { TextButton(onClick = it) { Text("Pin verschieben", color = C.Accent) } }
            }
            Text("Was ist dort?", color = C.Muted, fontSize = 12.sp)
            HAZARD_KINDS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { k ->
                        val on = k == kind
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color(0xFF7A1E1A) else C.Panel2)
                                .border(1.dp, if (on) C.WarmBottom else C.Line, RoundedCornerShape(10.dp))
                                .clickable { kind = k }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(k, color = C.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
            Text("Notiz (optional)", color = C.Muted, fontSize = 12.sp)
            OutlinedTextField(note, { if (it.length <= 80) note = it }, placeholder = { Text("z. B. mitten im Kurvenausgang") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Sichtbar für", color = C.Muted, fontSize = 12.sp)
            Segmented(listOf("all" to "Alle Fahrer", "group" to "Meine Gruppe"), vis) { vis = it }
            if (vis == "group" && st.settings.group.isBlank()) Text("Tipp: Gruppen-Code in den Einstellungen eintragen.", color = C.Muted, fontSize = 12.sp)
        }
        WarmButton("$kind melden") { onSave(kind, note.trim(), vis) }
    }
}

/** Liste der Gefahrenstellen aus der Cloud, mit "Noch da" / "Weg" / Löschen. */
@Composable
fun HazardsScreen(st: DriveState, controller: DriveController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { controller.watchNearby() }
    Column(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = C.Ink) }
            Column {
                ScreenTitle("Gefahrenstellen")
                Text(if (st.cloudOnline) "Cloud verbunden · von allen Fahrern" else "Keine Verbindung – zeigt den letzten Stand",
                    color = if (st.cloudOnline) C.Accent else C.Muted, fontSize = 12.sp)
            }
        }
        Text("Neue Stelle: lang auf die Karte drücken oder während der Fahrt den roten Knopf nutzen.", color = C.Muted, fontSize = 13.sp)
        AccountRow(st, controller)
        val list = st.cloudHazards.filter { it.visibleFor(st.settings.group) }.sortedByDescending { it.createdAt ?: Date(0) }
        if (list.isEmpty()) Text("Hier in der Gegend ist noch nichts eingetragen.", color = C.Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { h -> HazardRow(h, controller) }
        }
    }
}

@Composable
private fun HazardRow(h: CloudHazard, controller: DriveController) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel2)
            .drawBehind { if (h.active) drawRect(C.Danger, Offset.Zero, size.copy(width = 3.dp.toPx())) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(h.kind, color = C.Ink, fontWeight = FontWeight.SemiBold)
            Text(buildString {
                append(ago(h.createdAt))
                if (h.createdByName.isNotBlank()) append(" · von ${h.createdByName}")
                if (h.group.isNotBlank()) append(" · nur Gruppe")
                if (h.sensor) append(" · vom Sensor")
                if (!h.active) append(if (h.sensor && h.confirmedBy.size < 2) " · wartet auf Bestätigung" else " · wahrscheinlich weg")
            }, color = C.Muted, fontSize = 12.sp)
        }
        if (h.note.isNotBlank()) Text(h.note, color = C.Ink, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { controller.confirmHazard(h) }) { Text("Noch da (${h.confirmedBy.size})", fontSize = 12.sp) }
            OutlinedButton(onClick = { controller.hazardGone(h) }) { Text("Weg (${h.goneBy.size})", fontSize = 12.sp) }
            if (controller.isMine(h)) TextButton(onClick = { controller.deleteHazard(h) }) { Text("Löschen", color = C.Danger, fontSize = 12.sp) }
        }
    }
}

private fun ago(d: Date?): String {
    if (d == null) return "gerade eben"
    val min = (System.currentTimeMillis() - d.time) / 60_000
    return when {
        min < 1 -> "gerade eben"; min < 60 -> "vor $min Min."
        min < 1440 -> "vor ${min / 60} Std."; else -> "vor ${min / 1440} Tagen"
    }
}
