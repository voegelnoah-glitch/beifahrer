package at.beifahrer.data

import android.content.Context
import at.beifahrer.pacenote.CallStyle
import at.beifahrer.pacenote.DriveStyle
import at.beifahrer.pacenote.VoicePreset
import org.json.JSONObject

/** Alle Einstellungen der App. Werden sofort gespeichert und gelten auch während der Fahrt. */
data class AppSettings(
    // Warnung
    val leadSec: Double = 5.5,          // so viele Sekunden vor der Kurve wird angesagt
    val leadMode: String = "meter",     // "meter" = fester Abstand, "zeit" = je nach Tempo (leadSec)
    val leadMeters: Int = 200,          // so viele Meter vor der Kurve (bei leadMode "meter")
    val sayIn: Boolean = true,          // "In 200 Metern" vor der Ansage sagen
    val remind: Boolean = true,         // kurz davor nochmal: "Rechts 3!"
    val overTol: Int = 5,               // zu-schnell-Warnung ab +X km/h, 0 = aus
    // Wovor warnen: abgeschaltete Kategorien (siehe WARN_GROUPS). Leer = vor allem warnen.
    val warnOff: Set<String> = emptySet(),
    val sayDist: Boolean = true,
    val style: CallStyle = CallStyle.RALLY,
    // Höchsttempo wird nie gesprochen, nur angezeigt: "aa" = nur Android Auto, "both" = Handy + Auto, "off"
    val vmaxShow: String = "aa",
    // Kurventempo
    val driveStyle: DriveStyle = DriveStyle.NORMAL,
    val wet: Boolean = false,
    val speedLimit: Int = 100,
    // Stimme
    val voiceOn: Boolean = true,
    val preset: VoicePreset = VoicePreset.PROFI,
    val volume: Float = 1f,
    val duck: Boolean = true,           // Musik leiser bei Ansagen
    // Cloud
    val vis: String = "all",            // neue Meldungen: "all" oder "group"
    val group: String = "",             // Gruppen-Code
    val sensor: Boolean = true,         // Bodenwellen automatisch erkennen
    // Anzeige
    val tilt: Boolean = true,           // 3D-Karte während der Fahrt
    val mapType: String = "normal",     // "normal", "satellit" (Satellit mit Straßen) oder "gelaende"
)

/** Wovor gewarnt werden kann – Gruppen mit (Schlüssel, Anzeigename). Gefährliche Kurven werden immer angesagt. */
val WARN_GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "Kurven" to listOf("haarnadel" to "Haarnadeln", "eng" to "Enge Kurven 1–2", "mittel" to "Mittlere 3–4", "schnell" to "Schnelle 5–6", "ziehtzu" to "Zieht zu"),
    "Straße" to listOf("kuppe" to "Kuppen", "senke" to "Senken & Mulden"),
    "Gefahren von anderen Fahrern" to HAZARD_KINDS.map { it to it },
)

/** Speichert [AppSettings] als JSON in den SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val o = runCatching { JSONObject(prefs.getString("s", "{}")!!) }.getOrDefault(JSONObject())
        val d = AppSettings()
        return AppSettings(
            leadSec = o.optDouble("leadSec", d.leadSec), leadMode = o.optString("leadMode", d.leadMode), leadMeters = o.optInt("leadMeters", d.leadMeters), sayIn = o.optBoolean("sayIn", d.sayIn), remind = o.optBoolean("remind", d.remind), overTol = o.optInt("overTol", d.overTol),
            warnOff = o.optJSONArray("warnOff")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: d.warnOff,
            sayDist = o.optBoolean("sayDist", d.sayDist), vmaxShow = o.optString("vmaxShow", d.vmaxShow),
            style = runCatching { CallStyle.valueOf(o.optString("style")) }.getOrDefault(d.style),
            driveStyle = runCatching { DriveStyle.valueOf(o.optString("driveStyle")) }.getOrDefault(d.driveStyle),
            wet = o.optBoolean("wet", d.wet), speedLimit = o.optInt("speedLimit", d.speedLimit),
            voiceOn = o.optBoolean("voiceOn", d.voiceOn),
            preset = runCatching { VoicePreset.valueOf(o.optString("preset")) }.getOrDefault(d.preset),
            volume = o.optDouble("volume", d.volume.toDouble()).toFloat(), duck = o.optBoolean("duck", d.duck),
            vis = o.optString("vis", d.vis), group = o.optString("group", d.group),
            sensor = o.optBoolean("sensor", d.sensor), tilt = o.optBoolean("tilt", d.tilt), mapType = o.optString("mapType", d.mapType),
        )
    }

    fun save(s: AppSettings) {
        val o = JSONObject()
            .put("leadSec", s.leadSec).put("leadMode", s.leadMode).put("leadMeters", s.leadMeters).put("sayIn", s.sayIn).put("remind", s.remind).put("overTol", s.overTol)
            .put("warnOff", org.json.JSONArray(s.warnOff.toList()))
            .put("sayDist", s.sayDist).put("vmaxShow", s.vmaxShow).put("style", s.style.name)
            .put("driveStyle", s.driveStyle.name).put("wet", s.wet).put("speedLimit", s.speedLimit)
            .put("voiceOn", s.voiceOn).put("preset", s.preset.name).put("volume", s.volume.toDouble()).put("duck", s.duck)
            .put("vis", s.vis).put("group", s.group).put("sensor", s.sensor).put("tilt", s.tilt).put("mapType", s.mapType)
        prefs.edit().putString("s", o.toString()).apply()
    }
}
