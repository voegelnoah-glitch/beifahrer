package at.beifahrer.nav

import at.beifahrer.data.AccountInfo
import at.beifahrer.data.AppSettings
import at.beifahrer.data.CloudHazard
import at.beifahrer.data.UpdateInfo
import at.beifahrer.pacenote.CallStyle
import at.beifahrer.pacenote.DriveStyle
import at.beifahrer.pacenote.PaceBook
import at.beifahrer.pacenote.PaceNote
import at.beifahrer.pacenote.VoicePreset

enum class Mode { IDLE, ROUTING, DRIVING, ARRIVED }

/** Ein Fahrziel aus der Suche oder der Verlaufsliste. */
data class Destination(val name: String, val detail: String, val placeId: String)

/** Alles, was Handy-Oberfläche und Android-Auto-Bildschirm anzeigen. */
data class DriveState(
    val mode: Mode = Mode.IDLE,
    val navigatorReady: Boolean = false,
    val destination: Destination? = null,
    val book: PaceBook? = null,
    val posS: Double = 0.0,
    val speedKmh: Int = 0,
    val current: PaceNote? = null,
    val upcoming: List<PaceNote> = emptyList(),
    val distToCurrent: Double = 0.0,
    val remainingMeters: Int = 0,
    val remainingSeconds: Int = 0,
    val navStepText: String? = null,     // Abbiege-Hinweis von Google (TurnByTurn)
    val navStepMeters: Int? = null,
    val settings: AppSettings = AppSettings(),
    val currentVmax: Int? = null,       // empfohlenes Höchsttempo der aktuellen Kurve
    val overspeed: Boolean = false,
    val voiceName: String? = null,
    val aiVoiceLabel: String? = null,   // z. B. "Charon · Google KI"
    val voicePrep: String? = null,      // Fortschritt beim Erzeugen der KI-Ansagen
    val simulate: Boolean = false,
    val message: String? = null,
    val lastSpoken: String? = null,
    val recent: List<Destination> = emptyList(),
    val cloudOnline: Boolean = false,
    val account: AccountInfo? = null,
    val update: UpdateInfo? = null,         // neue Version verfügbar
    val updateProgress: Int? = null,        // Download in Prozent
    val cloudHazards: List<CloudHazard> = emptyList(),   // alle Stellen im beobachteten Gebiet
    val paused: Boolean = false,                         // Fahrt pausiert: keine Ansagen, keine Google-Führung
) {
    // Abkürzungen auf die Einstellungen
    val style: CallStyle get() = settings.style
    val driveStyle: DriveStyle get() = settings.driveStyle
    val wet: Boolean get() = settings.wet
    val speedLimit: Int get() = settings.speedLimit
    val voiceOn: Boolean get() = settings.voiceOn
    val preset: VoicePreset get() = settings.preset
}
