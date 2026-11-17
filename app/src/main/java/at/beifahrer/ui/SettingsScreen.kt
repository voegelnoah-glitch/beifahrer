package at.beifahrer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.beifahrer.data.AppSettings
import at.beifahrer.data.WARN_GROUPS
import at.beifahrer.nav.DriveController
import at.beifahrer.nav.DriveState
import at.beifahrer.pacenote.CallStyle
import at.beifahrer.pacenote.DriveStyle
import at.beifahrer.pacenote.VoicePreset
import kotlin.math.roundToInt

/** Alle wichtigen Einstellungen auf einer Seite. Jede Änderung gilt sofort und wird gespeichert. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(st: DriveState, controller: DriveController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val s = st.settings
    fun set(change: (AppSettings) -> AppSettings) = controller.updateSettings(change)

    Column(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = C.Ink) }
            Column { ScreenTitle("Einstellungen"); Text("Gilt sofort, auch während der Fahrt", color = C.Muted, fontSize = 12.sp) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Section("Warnung") {
                Item("Wie früh ansagen?", "Bei 80 km/h etwa ${(80 / 3.6 * s.leadSec).roundToInt()} m vor der Kurve") {
                    Segmented(listOf(8.0 to "Sehr früh", 6.5 to "Früh", 5.5 to "Normal", 4.0 to "Spät"), s.leadSec) { v -> set { it.copy(leadSec = v) } }
                }
                Item("Kurz davor nochmal erinnern", "Bei engen und gefährlichen Kurven: „Rechts 3!“ etwa 2 Sekunden vorher") {
                    OnOff(s.remind) { v -> set { it.copy(remind = v) } }
                }
                Item("Rot anzeigen, wenn zu schnell", "Wenn normales Bremsen bis zur Kurve nicht mehr reicht (keine Ansage)") {
                    Segmented(listOf(0 to "Aus", 5 to "+5", 10 to "+10", 15 to "+15 km/h"), s.overTol) { v -> set { it.copy(overTol = v) } }
                }
            }

            Section("Warnen vor") {
                Text("Tippe an, wovor die Stimme warnen soll. Rot markierte, gefährliche Kurven werden immer angesagt.", color = C.Muted, fontSize = 12.sp)
                WARN_GROUPS.forEach { (group, items) ->
                    Item(group) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items.forEach { (key, label) ->
                                val on = key !in s.warnOff
                                FilterChip(on, { set { it.copy(warnOff = if (on) it.warnOff + key else it.warnOff - key) } },
                                    { Text(label, fontSize = 13.sp) },
                                    leadingIcon = if (on) ({ Text("✓", color = C.Accent) }) else null,
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Accent.copy(alpha = .14f), selectedLabelColor = C.Accent))
                            }
                        }
                    }
                }
            }

            Section("Ansage") {
                Item("Abstände („150“)") { OnOff(s.sayDist) { v -> set { it.copy(sayDist = v) } } }
                Item("Ansage-Stil", "„Rechts 3“ oder „Mittlere Rechtskurve“") {
                    Segmented(listOf(CallStyle.RALLY to "Rallye-kurz", CallStyle.PLAIN to "Normal erklärt"), s.style) { v -> set { it.copy(style = v) } }
                }
            }

            Section("Kurventempo (nur Anzeige)") {
                Item("Höchsttempo anzeigen", "Wird nie angesagt, nur angezeigt") {
                    Segmented(listOf("aa" to "Nur Auto", "both" to "Handy + Auto", "off" to "Aus"), s.vmaxShow) { v -> set { it.copy(vmaxShow = v) } }
                }
                Item("Fahrstil", "Wie flott die Tempo-Empfehlung ist") {
                    Segmented(DriveStyle.entries.map { it to it.label }, s.driveStyle) { v -> set { it.copy(driveStyle = v) } }
                }
                Item("Straße", "Bei Nässe empfiehlt die App etwa ein Viertel weniger") {
                    Segmented(listOf(false to "Trocken", true to "Nass"), s.wet) { v -> set { it.copy(wet = v) } }
                }
                Item("Tempolimit", "Die Empfehlung geht nie darüber") {
                    Segmented(listOf(80 to "80", 100 to "100", 130 to "130"), s.speedLimit) { v -> set { it.copy(speedLimit = v) } }
                }
            }

            Section("Stimme") {
                Item("Sprachausgabe") { OnOff(s.voiceOn) { v -> set { it.copy(voiceOn = v) } } }
                Item("Sprechtempo") {
                    Segmented(listOf(VoicePreset.RUHIG to "Langsam", VoicePreset.NORMAL to "Normal", VoicePreset.PROFI to "Schnell"), s.preset) { v -> controller.setPreset(v) }
                }
                Item("Lautstärke") {
                    Segmented(listOf(0.5f to "Leise", 0.8f to "Mittel", 1f to "Laut"), s.volume) { v -> set { it.copy(volume = v) } }
                }
                Item("Musik leiser bei Ansagen") { OnOff(s.duck) { v -> set { it.copy(duck = v) } } }
            }

            Section("Cloud & Gruppe") {
                Item("Neue Meldungen sichtbar für") {
                    Segmented(listOf("all" to "Alle Fahrer", "group" to "Meine Gruppe"), s.vis) { v -> set { it.copy(vis = v) } }
                }
                Item("Gruppen-Code", "Alle mit demselben Code sehen eure Gruppen-Meldungen") {
                    var code by remember(s.group) { mutableStateOf(s.group) }
                    OutlinedTextField(code, { v ->
                        code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(24)
                        set { it.copy(group = code) }
                    }, placeholder = { Text("z. B. RALLYE-CREW") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Item("Bodenwellen automatisch erkennen", "Über den Beschleunigungssensor des Handys") { OnOff(s.sensor) { v -> set { it.copy(sensor = v) } } }
            }

            Section("Anzeige") {
                Item("Karte während der Fahrt") { Segmented(listOf(true to "3D", false to "2D"), s.tilt) { v -> set { it.copy(tilt = v) } } }
            }

            Section("App") {
                Text("Version ${at.beifahrer.BuildConfig.VERSION_NAME}", color = C.Ink, fontSize = 14.sp)
                st.update?.let { u -> UpdateBanner(u.versionName, st.updateProgress) { controller.installUpdate() } }
                    ?: Segmented(listOf(true to "Nach Updates suchen"), true) { controller.checkForUpdate(manual = true) }
                if (!controller.updater.enabled) Text("Updates sind aus, weil die App nicht über GitHub gebaut wurde.", color = C.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), style = TitleStyle.copy(fontSize = 17.sp), color = C.Accent)
        content()
    }
}

@Composable
private fun Item(label: String, help: String? = null, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        help?.let { Text(it, color = C.Muted, fontSize = 12.sp) }
        control()
    }
}

@Composable
private fun OnOff(value: Boolean, onChange: (Boolean) -> Unit) = Segmented(listOf(true to "An", false to "Aus"), value, onSelect = onChange)
