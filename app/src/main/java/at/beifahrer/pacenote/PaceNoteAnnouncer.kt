package at.beifahrer.pacenote

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Spricht die Ansagen. Musik wird dabei kurz leiser gestellt ("Ducking").
 * Läuft über den Navigations-Audiokanal, damit es in Android Auto aus den Autolautsprechern kommt.
 */
/** Sprechweisen wie im Prototyp. */
enum class VoicePreset(val label: String, val rate: Float, val pitch: Float) {
    // Ruhigere Tempi: zu schnell klingt die Stimme abgehackt
    PROFI("Schnell", 1.12f, 1.0f), NORMAL("Normal", 1.0f, 1.0f), RUHIG("Langsam", 0.9f, 1.0f), TIEF("Tief", 1.0f, 0.8f)
}

class PaceNoteAnnouncer(context: Context) : TextToSpeech.OnInitListener {

    var preset = VoicePreset.PROFI
    var volume = 1f
    /** Musik leiser stellen, während gesprochen wird. */
    var duck = true

    var enabled = true
        set(value) { field = value; if (!value) tts.stop() }

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var counter = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.GERMAN
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { audio.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { audio.abandonAudioFocusRequest(focus) }
        })
        ready = true
    }

    /** Alle deutschen Stimmen des Geräts (Google Sprachausgabe hat meist mehrere, auch offline). */
    fun germanVoices(): List<Voice> =
        if (!ready) emptyList() else tts.voices.orEmpty().filter { it.locale.language == "de" }.sortedBy { it.isNetworkConnectionRequired }

    fun selectVoice(name: String) { tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it } }

    /** Beispielsatz zum Probehören. */
    fun preview(text: String = "Vorsicht! Rechts 2 zieht zu, 100") = say(text, preset.rate, force = true)

    val speaking: Boolean get() = ready && tts.isSpeaking

    /**
     * Neue Ansagen werden hinten angehängt statt die laufende abzuschneiden (sonst klingt es abgehackt).
     * [force] (Probehören) unterbricht.
     */
    fun say(text: String, rate: Float = preset.rate, force: Boolean = false) {
        if (!ready || (!enabled && !force)) return
        if (duck) audio.requestAudioFocus(focus)
        tts.setSpeechRate(rate)
        tts.setPitch(preset.pitch)
        val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        tts.speak(PaceText.smooth(text), if (force) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "note-${counter++}")
    }

    /** Laufende und wartende Ansagen abbrechen. */
    fun stop() { if (ready) tts.stop() }

    fun shutdown() = tts.shutdown()
}
