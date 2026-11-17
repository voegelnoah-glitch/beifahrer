package at.beifahrer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.beifahrer.nav.Destination
import at.beifahrer.nav.DriveController
import at.beifahrer.nav.DriveState
import at.beifahrer.nav.Mode
import at.beifahrer.pacenote.CallStyle
import at.beifahrer.pacenote.NoteType
import at.beifahrer.pacenote.PaceText
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Ort für eine neue Gefahrenmeldung. */
private data class ReportTarget(val lat: Double, val lng: Double, val where: String, val fromMap: Boolean)

@Composable
fun BeifahrerScreen(controller: DriveController) {
    val st by controller.state.collectAsStateWithLifecycle()
    var showVoices by remember { mutableStateOf(false) }
    var showHazards by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ReportTarget?>(null) }
    if (showVoices) { VoicesScreen(controller) { showVoices = false }; return }
    if (showHazards) { HazardsScreen(st, controller) { showHazards = false }; return }
    if (showSettings) { SettingsScreen(st, controller) { showSettings = false }; return }
    report?.let { r ->
        HazardReportScreen(st, r.where,
            onSave = { kind, note, vis -> controller.reportHazardAt(r.lat, r.lng, kind, note, vis); report = null },
            onMovePin = if (r.fromMap) ({ report = null; controller.showMessage("Lang auf die neue Stelle drücken.") }) else null,
            onBack = { report = null })
        return
    }
    // Wo die eigenen Anzeigen die Karte verdecken (Pixel) – damit die Kamera den Standort in den freien Bereich legt
    var screenH by remember { mutableStateOf(0) }
    var topEnd by remember { mutableStateOf(0) }
    var bottomStart by remember { mutableStateOf(0) }
    val driving = st.mode == Mode.DRIVING || st.mode == Mode.ARRIVED
    Box(Modifier.fillMaxSize().background(C.Bg).onGloballyPositioned { screenH = it.size.height }) {
        if (st.navigatorReady) NavigationViewHost(Modifier.fillMaxSize(), following = st.mode == Mode.DRIVING, tilt = st.settings.tilt,
            padTop = if (driving) topEnd else 0, padBottom = if (driving && bottomStart > 0) (screenH - bottomStart).coerceAtLeast(0) else 0,
            hazards = st.cloudHazards.filter { it.visibleFor(st.settings.group) },
            onLongPress = { report = ReportTarget(it.latitude, it.longitude, "%.5f, %.5f".format(it.latitude, it.longitude), fromMap = true) })
        when (st.mode) {
            Mode.IDLE, Mode.ROUTING -> SearchOverlay(st, controller, openVoices = { showVoices = true }, openHazards = { showHazards = true }, openSettings = { showSettings = true })
            Mode.DRIVING, Mode.ARRIVED -> DriveOverlay(st, controller, onTopEnd = { topEnd = it }, onBottomStart = { bottomStart = it }, openSettings = { showSettings = true }, openHazards = { showHazards = true }, openReport = {
                val pos = controller.currentPosition()
                if (pos == null) controller.showMessage("Noch keine Position.")
                else report = ReportTarget(pos.first, pos.second, "aktuelle Position", fromMap = false)
            })
        }
        // Hinweise verschwinden nach ein paar Sekunden von selbst (außer "Pausiert")
        LaunchedEffect(st.message) { if (st.message != null && !st.paused) { delay(5000); controller.showMessage(null) } }
        st.message?.let { msg ->
            Text(msg, color = C.Ink, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFF222C3A)).padding(14.dp))
        }
    }
}

// ---------------- Ziel eingeben ----------------

@Composable
private fun SearchOverlay(st: DriveState, controller: DriveController, openVoices: () -> Unit, openHazards: () -> Unit, openSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Destination>()) }
    LaunchedEffect(query) {
        delay(250) // erst suchen, wenn kurz nicht getippt wird
        results = try { controller.places.suggest(query, null) } catch (e: Exception) { emptyList() }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Ziel eingeben") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = C.Panel2, unfocusedContainerColor = C.Panel2,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
        val list = if (query.length >= 2) results else st.recent
        if (list.isNotEmpty()) LazyColumn(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel2).border(1.dp, C.Line, RoundedCornerShape(16.dp))
        ) {
            items(list) { d ->
                Row(Modifier.fillMaxWidth().clickable { controller.places.newSession(); controller.startRoute(d) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (query.length >= 2) Icons.Default.Place else Icons.Default.History, null, tint = C.Muted)
                    Column { Text(d.name, color = C.Ink); if (d.detail.isNotBlank()) Text(d.detail, color = C.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        st.update?.let { u -> UpdateBanner(u.versionName, st.updateProgress) { controller.installUpdate() } }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (st.mode == Mode.ROUTING) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Text("Route zu ${st.destination?.name} wird berechnet …", color = C.Ink)
            } else ScreenTitle(if (st.navigatorReady) "Wohin geht's?" else "Navigation lädt …")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.Panel2).border(1.dp, C.Line, RoundedCornerShape(12.dp))
                    .clickable(onClick = openVoices).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Stimme  ", color = C.Muted, fontSize = 12.sp)
                    Text(st.aiVoiceLabel?.substringBefore(" ·") ?: "Handy", color = C.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("›", color = C.Accent, fontSize = 18.sp)
                }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.Panel2).border(1.dp, C.Line, RoundedCornerShape(12.dp))
                    .clickable(onClick = openSettings).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = C.Accent, modifier = Modifier.size(18.dp))
                    Text("  Einstellungen", color = C.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A1D1F)).border(1.dp, Color(0xFF6B2C2F), RoundedCornerShape(12.dp))
                .clickable(onClick = openHazards).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFFFB3B3), modifier = Modifier.size(18.dp))
                Text("  Gefahrenstellen (Cloud)", color = C.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (st.cloudOnline) "verbunden" else "offline", color = C.Muted, fontSize = 12.sp)
                Text("  ›", color = C.Ink, fontSize = 18.sp)
            }
            AccountRow(st, controller)
            Text("Tipp: Lang auf die Karte drücken, um eine Gefahr einzutragen.", color = C.Muted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Testfahrt simulieren", color = C.Ink)
                    Text("Auto fährt die Route virtuell ab – zum Ausprobieren am Sofa", color = C.Muted, fontSize = 12.sp)
                }
                Switch(st.simulate, controller::setSimulate)
            }
        }
    }
}

/** Hinweis auf eine neue Version mit "Jetzt aktualisieren". */
@Composable
fun UpdateBanner(version: String, progress: Int?, onUpdate: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Accent.copy(alpha = .14f)).border(1.dp, C.Accent, RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Neue Version $version", color = C.Accent, fontWeight = FontWeight.Bold)
            Text(if (progress != null) "Wird geladen … $progress %" else "Mit einem Tipp aktualisieren", color = C.Muted, fontSize = 12.sp)
        }
        if (progress == null) androidx.compose.material3.Button(onClick = onUpdate) { Text("Aktualisieren") }
        else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
    }
}

/** Google-Anmeldung: nötig zum Eintragen und Abstimmen, Lesen geht auch ohne. */
@Composable
fun AccountRow(st: DriveState, controller: DriveController) {
    val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
    val acc = st.account
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel2).border(1.dp, C.Line, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (acc?.google == true) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(C.Ink), contentAlignment = Alignment.Center) {
                Text((acc.firstName ?: "?").take(1), color = C.Bg, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Angemeldet als ${acc.firstName ?: "Google-Nutzer"}", color = C.Ink, fontSize = 14.sp)
                acc.email?.let { Text(it, color = C.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            androidx.compose.material3.TextButton(onClick = { controller.signOut() }) { Text("Abmelden", color = C.Muted) }
        } else {
            Column(Modifier.weight(1f)) {
                Text("Mit Google anmelden", color = C.Ink, fontWeight = FontWeight.SemiBold)
                Text("Zum Eintragen und Bestätigen. Ansagen hörst du auch ohne.", color = C.Muted, fontSize = 11.sp)
            }
            androidx.compose.material3.Button(onClick = { controller.signInWithGoogle(activity) }) { Text("Anmelden") }
        }
    }
}

// ---------------- Während der Fahrt ----------------

@Composable
private fun DriveOverlay(st: DriveState, controller: DriveController, onTopEnd: (Int) -> Unit, onBottomStart: (Int) -> Unit, openSettings: () -> Unit, openHazards: () -> Unit, openReport: () -> Unit) {
    // Zurück-Taste: nachfragen, dann Fahrt beenden und ins Hauptmenü
    var askStop by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { askStop = true }
    if (askStop) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askStop = false },
        title = { Text("Fahrt beenden?") },
        text = { Text("Die Navigation wird gestoppt und du kommst zurück ins Hauptmenü.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { askStop = false; controller.stop() }) { Text("Beenden") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askStop = false }) { Text("Weiterfahren") } },
    )
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.onGloballyPositioned { onTopEnd(it.boundsInRoot().bottom.toInt()) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NavBanner(st)
        if (st.book == null) Text("Kurven werden berechnet …", color = C.Muted, modifier = Modifier.padding(6.dp))
        else {
            PaceNoteCard(if (st.mode == Mode.ARRIVED) null else st.current, st.distToCurrent, st.style,
                // Höchsttempo am Handy nur, wenn gewünscht (Standard: nur in Android Auto)
                vmax = st.currentVmax?.takeIf { it < st.speedLimit && st.settings.vmaxShow == "both" },
                overspeed = st.overspeed && st.settings.vmaxShow == "both")
            val cur = st.current
            if (cur != null && st.upcoming.isNotEmpty()) {
                val lead = when { cur.link != null -> if (cur.link == at.beifahrer.pacenote.Link.INTO) "direkt in" else "und dann"
                    cur.distAfter != null -> if (cur.distAfter!! >= 500) "lange Gerade, dann" else "${cur.distAfter} m, dann"
                    else -> "dann" }
                Text("$lead  " + st.upcoming.take(2).joinToString("  ·  ") { PaceText.title(it, st.style) },
                    color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.onGloballyPositioned { onBottomStart(it.boundsInRoot().top.toInt()) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        st.voicePrep?.let { Text(it, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
        Tape(st)
        BottomBar(st, controller, openSettings, openHazards, openReport, onStop = { askStop = true })
        }
    }
}

@Composable
private fun NavBanner(st: DriveState) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Banner).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Navigation, null, tint = C.Accent, modifier = Modifier.size(28.dp))
        Column {
            st.navStepMeters?.let { Text(formatDist(it), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Text(st.navStepText ?: "Der Route folgen", color = Color.White.copy(alpha = .9f), fontSize = 13.sp, maxLines = 2)
        }
    }
}

/** Vorschau der nächsten 600 m als Band. */
@Composable
private fun Tape(st: DriveState) {
    val book = st.book ?: return
    BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(12.dp))) {
        Text("VORSCHAU 600 M", color = C.Muted, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 9.dp).fillMaxWidth().height(2.dp).background(C.Line))
        var lastX = -99f
        book.notes.forEach { n ->
            val d = n.s - st.posS
            if (d < -5 || d > 600) return@forEach
            val frac = (d.coerceAtLeast(0.0) / 600 * 0.9 + 0.04).toFloat()
            if (frac - lastX < 0.11f && !n.danger) return@forEach
            lastX = frac
            val bg = if (n.danger) C.Danger else C.Panel2
            val fg = when { n.danger -> Color.White; n.type == NoteType.CURVE -> C.Accent; else -> C.Vertical }
            Text(PaceText.short(n), color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = maxWidth * frac, y = (-4).dp).clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun BottomBar(st: DriveState, controller: DriveController, openSettings: () -> Unit, openHazards: () -> Unit, openReport: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val overHere = st.overspeed && st.settings.vmaxShow == "both"
            Box(Modifier.size(56.dp).border(3.dp, if (overHere) C.Danger else C.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${st.speedKmh}", color = if (overHere) C.Danger else C.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                    Text("km/h", color = C.Muted, fontSize = 10.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                val eta = LocalTime.now().plusSeconds(st.remainingSeconds.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
                // Ankunftszeit: kleines Etikett, große Uhrzeit, darunter Rest-Strecke und -Zeit
                Text(if (st.paused) "PAUSIERT" else "ANKUNFT", color = if (st.paused) C.Accent else C.Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(eta, color = C.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, maxLines = 1, softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                Text("${formatDist(st.remainingMeters)} · ${formatMin(st.remainingSeconds)}", color = C.Muted, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
            // Pause / Weiter
            androidx.compose.material3.FilledTonalButton(onClick = controller::togglePause, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Icon(if (st.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                Text(if (st.paused) " Weiter" else " Pause")
            }
            // Beenden -> Hauptmenü
            androidx.compose.material3.Button(onClick = onStop, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = C.Danger)) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                Text(" Stopp")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriveAction("Gefahr melden", Icons.Default.Warning, Color(0xFFFFB3B3), Color(0xFF3A1D1F), Modifier.weight(1f), openReport)
            DriveAction("Gefahren", Icons.AutoMirrored.Filled.List, C.Accent, C.Panel2, Modifier.weight(1f), openHazards)
            DriveAction("Einstellungen", Icons.Default.Settings, C.Accent, C.Panel2, Modifier.weight(1f), openSettings)
        }
    }
}

@Composable
private fun DriveAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(bg).border(1.dp, C.Line, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = C.Ink, fontSize = 11.sp, maxLines = 1)
    }
}

private fun formatMin(sec: Int): String { val m = (sec / 60).coerceAtLeast(1); return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

private fun formatDist(m: Int) = if (m >= 1000) "%.1f km".format(m / 1000.0).replace('.', ',') else "${(m / 10) * 10} m"
