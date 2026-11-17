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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.beifahrer.ai.CloudVoice
import at.beifahrer.ai.Gender
import at.beifahrer.ai.Provider
import at.beifahrer.nav.DriveController
import kotlinx.coroutines.launch

/** Feste Personen wie in der Vorlage – jede ist eine Google-KI-Stimme mit passender Sprechweise. */
private data class Persona(val name: String, val desc: String, val voiceId: String, val gender: Gender, val preset: at.beifahrer.pacenote.VoicePreset)
private val PERSONAS = listOf(
    Persona("Max", "Rallye-Beifahrer · schnell & knapp", "de-DE-Chirp3-HD-Charon", Gender.MALE, at.beifahrer.pacenote.VoicePreset.PROFI),
    Persona("Lena", "ruhig & gut verständlich", "de-DE-Chirp3-HD-Kore", Gender.FEMALE, at.beifahrer.pacenote.VoicePreset.RUHIG),
    Persona("Jonas", "locker, wie ein Kumpel daneben", "de-DE-Chirp3-HD-Puck", Gender.MALE, at.beifahrer.pacenote.VoicePreset.NORMAL),
    Persona("Sophie", "sportlich & energisch", "de-DE-Chirp3-HD-Aoede", Gender.FEMALE, at.beifahrer.pacenote.VoicePreset.NORMAL),
)
private fun Persona.voice() = CloudVoice(Provider.GOOGLE, voiceId, name, gender, "Chirp 3 HD")

private val SAMPLES = listOf(
    "Links 4 in Rechts 2 zieht zu, 150",
    "Vorsicht! Rechts Haarnadel nach Kuppe",
    "Kuppe, 100, Links 6 lang",
    "Achtung, scharfe Linkskurve, zieht zu",
)

/**
 * Seite "KI-Stimme wählen": Anbieter wählen, Stimmen live laden, mit Beispielsatz probehören, auswählen.
 * Die Liste kommt direkt vom Anbieter – neue Stimmen erscheinen also automatisch.
 */
@Composable
fun VoicesScreen(controller: DriveController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(controller.aiVoice.settings.voice?.provider ?: Provider.GOOGLE) }
    var gender by remember { mutableStateOf<Gender?>(null) }
    var sample by remember { mutableStateOf(SAMPLES[0]) }
    var voices by remember { mutableStateOf<List<CloudVoice>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(controller.aiVoice.settings.voice?.id) }
    var pending by remember { mutableStateOf<CloudVoice?>(controller.aiVoice.settings.voice) }
    var pendingPreset by remember { mutableStateOf<at.beifahrer.pacenote.VoicePreset?>(null) }
    val st by controller.state.collectAsStateWithLifecycle()
    var elevenKey by remember { mutableStateOf(controller.aiVoice.settings.elevenLabsKey) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(provider, reload) {
        voices = null; error = null
        if (provider == Provider.ELEVENLABS && controller.aiVoice.settings.elevenLabsKey.isBlank()) { voices = emptyList(); return@LaunchedEffect }
        voices = try { controller.loadVoices(provider) } catch (e: Exception) {
            error = when (provider) {
                Provider.GOOGLE -> "Google-Stimmen nicht erreichbar. Ist die „Cloud Text-to-Speech API“ in der Cloud Console aktiviert?"
                Provider.ELEVENLABS -> "ElevenLabs-Schlüssel ungültig oder kein Internet."
                Provider.DEVICE -> "Keine Handy-Stimmen gefunden."
            }
            emptyList()
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = C.Ink) }
            Column {
                ScreenTitle("Stimme wählen")
                Text("Tippe ▶ zum Anhören", color = C.Muted, fontSize = 12.sp)
            }
        }
        Text("Ansage-Stil", color = C.Muted, fontSize = 12.sp)
        Segmented(listOf(at.beifahrer.pacenote.CallStyle.RALLY to "Rallye-kurz", at.beifahrer.pacenote.CallStyle.PLAIN to "Normal erklärt"), st.settings.style) { v ->
            controller.updateSettings { it.copy(style = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Provider.entries.sortedBy { listOf(Provider.GOOGLE, Provider.ELEVENLABS, Provider.DEVICE).indexOf(it) }.forEach { p ->
                FilterChip(provider == p, { provider = p }, { Text(p.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(gender == null, { gender = null }, { Text("Alle", fontSize = 12.sp) })
            FilterChip(gender == Gender.FEMALE, { gender = Gender.FEMALE }, { Text("Weiblich", fontSize = 12.sp) })
            FilterChip(gender == Gender.MALE, { gender = Gender.MALE }, { Text("Männlich", fontSize = 12.sp) })
        }
        // Beispielsatz zum Durchtippen
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel2)
            .clickable { sample = SAMPLES[(SAMPLES.indexOf(sample) + 1) % SAMPLES.size] }.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("Beispiel: ", color = C.Muted, fontSize = 13.sp)
            Text(sample, color = C.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("ändern", color = C.Muted, fontSize = 12.sp)
        }

        if (provider == Provider.ELEVENLABS) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(elevenKey, { elevenKey = it }, label = { Text("ElevenLabs API-Schlüssel") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { controller.aiVoice.settings.elevenLabsKey = elevenKey; reload++ }) { Text("Stimmen laden") }
            Text("Schlüssel bekommst du auf elevenlabs.io unter Profil → API Keys.", color = C.Muted, fontSize = 12.sp)
        }

        error?.let { Text(it, color = C.Danger, fontSize = 13.sp) }
        val list = voices
        if (list == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            if (provider == Provider.GOOGLE) {
                item(key = "h-personas") { Text("KI-STIMMEN", color = C.Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 2.dp)) }
                items(PERSONAS, key = { "p-" + it.name }) { p ->
                    val v = p.voice()
                    VoiceRow(v.copy(tier = p.desc), pending?.id == v.id, playing == v.id,
                        onSelect = { pending = v; pendingPreset = p.preset },
                        onPlay = {
                            playing = v.id
                            scope.launch {
                                try { controller.previewVoice(v, sample) } catch (e: Exception) { error = "Anhören fehlgeschlagen: ${e.message?.take(80)}" }
                                playing = null
                            }
                        })
                }
                item(key = "own") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("Eigene Stimme aufnehmen", color = C.Muted, fontWeight = FontWeight.SemiBold)
                        Text("ca. 40 Wörter, wie ein echter Beifahrer · kommt in einer nächsten Version", color = C.Muted, fontSize = 12.sp)
                    }
                }
                item(key = "h-all") { Text("ALLE GOOGLE-STIMMEN", color = C.Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp, start = 2.dp)) }
            }
            // nach Stimmtyp gruppieren (Reihenfolge bleibt: beste Qualität zuerst)
            list.filter { gender == null || it.gender == gender }.groupBy { it.tier }.forEach { (tier, group) ->
                item(key = "h-$tier") { Text(tier.uppercase(), color = C.Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp, start = 2.dp)) }
                items(group, key = { it.id }) { v ->
                    VoiceRow(v, pending?.id == v.id, playing == v.id,
                        onSelect = { pending = v; pendingPreset = null },
                        onPlay = {
                            playing = v.id
                            scope.launch {
                                try { controller.previewVoice(v, sample) } catch (e: Exception) { error = "Probehören fehlgeschlagen: ${e.message?.take(80)}" }
                                playing = null
                            }
                        })
                }
            }
        }
        Text("Tempo", color = C.Muted, fontSize = 12.sp)
        Segmented(listOf(at.beifahrer.pacenote.VoicePreset.RUHIG to "Langsam", at.beifahrer.pacenote.VoicePreset.NORMAL to "Normal", at.beifahrer.pacenote.VoicePreset.PROFI to "Schnell"),
            pendingPreset ?: st.settings.preset) { pendingPreset = it }
        Text("Die Ansagen einer Route werden beim Start einmal erzeugt und gespeichert. Während der Fahrt braucht die KI-Stimme kein Netz.",
            color = C.Muted, fontSize = 11.sp)
        pending?.let { v ->
            CyanButton("Stimme ${v.name} übernehmen") {
                selected = v.id
                controller.chooseVoice(v)
                pendingPreset?.let { controller.setPreset(it) }
                onBack()
            }
        }
    }
}

@Composable
private fun VoiceRow(v: CloudVoice, selected: Boolean, loading: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel)
            .border(1.dp, if (selected) C.Accent else C.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(C.Panel2), contentAlignment = Alignment.Center) {
            Text(v.name.take(1), color = C.Ink, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(v.name, color = C.Ink, fontWeight = FontWeight.SemiBold)
            Text(if (v.tier.length > 12) v.tier else when (v.gender) { Gender.MALE -> "männlich"; Gender.FEMALE -> "weiblich"; else -> v.tier }, color = C.Muted, fontSize = 12.sp)
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(C.Panel2).clickable(enabled = !loading, onClick = onPlay), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.PlayArrow, "${v.name} probehören", tint = C.Ink)
        }
    }
}
