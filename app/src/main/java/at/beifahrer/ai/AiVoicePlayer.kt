package at.beifahrer.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import at.beifahrer.BuildConfig
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Gewählte Stimme + ElevenLabs-Schlüssel, dauerhaft gespeichert. */
class VoiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)

    var voice: CloudVoice?
        get() = prefs.getString("voice", null)?.let {
            val o = JSONObject(it)
            CloudVoice(Provider.valueOf(o.getString("p")), o.getString("id"), o.getString("name"), Gender.valueOf(o.getString("g")), o.getString("tier"))
        }
        set(v) {
            prefs.edit().putString("voice", v?.let {
                JSONObject().put("p", it.provider.name).put("id", it.id).put("name", it.name).put("g", it.gender.name).put("tier", it.tier).toString()
            }).apply()
        }

    var elevenLabsKey: String
        get() = prefs.getString("elevenKey", "").orEmpty()
        set(v) { prefs.edit().putString("elevenKey", v.trim()).apply() }
}

/**
 * Spielt Ansagen mit KI-Stimme ab.
 *
 * Wichtig fürs Fahren: Die Ansagen eines Gebetbuchs sind vor der Fahrt bekannt. Darum werden sie beim
 * Routen-Start einmal erzeugt ([prefetch]) und als MP3 gespeichert. Während der Fahrt wird nur abgespielt –
 * ohne Verzögerung und auch ohne Netz. Fehlt eine Datei, spricht die Handy-Stimme (Rückfall).
 * Wiederkehrende Sätze wie "Links 3" werden über alle Fahrten hinweg wiederverwendet.
 */
class AiVoicePlayer(private val context: Context) {

    val settings = VoiceSettings(context)
    var volume = 1f
    private val dir = File(context.filesDir, "voice-cache").apply { mkdirs() }
    private var player: MediaPlayer? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun engine(p: Provider): CloudTts? = when (p) {
        Provider.GOOGLE -> GoogleCloudTts(BuildConfig.MAPS_API_KEY)
        Provider.ELEVENLABS -> settings.elevenLabsKey.takeIf { it.isNotBlank() }?.let { ElevenLabsTts(it) }
        Provider.DEVICE -> null
    }

    /** Aktiv, wenn eine KI-Stimme gewählt ist. */
    val active: Boolean get() = settings.voice?.let { it.provider != Provider.DEVICE } == true

    private fun file(voice: CloudVoice, text: String, rate: Float): File {
        val key = MessageDigest.getInstance("SHA-1").digest("${voice.provider}|${voice.id}|$rate|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$key.mp3")
    }

    /** Erzeugt alle fehlenden Ansagen (blockierend, im IO-Thread). Gibt die Anzahl neuer Dateien zurück. */
    fun prefetch(sentences: Collection<String>, rate: Float, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val voice = settings.voice ?: return 0
        val tts = engine(voice.provider) ?: return 0
        val missing = sentences.distinct().filter { !file(voice, it, rate).exists() }
        missing.forEachIndexed { i, s ->
            val bytes = tts.synthesize(at.beifahrer.pacenote.PaceText.smooth(s), voice, rate) // flüssiger Satzbau
            file(voice, s, rate).writeBytes(bytes)
            onProgress(i + 1, missing.size)
        }
        return missing.size
    }

    /** Spielt die Ansage, wenn sie schon erzeugt wurde. @return false → Handy-Stimme verwenden. */
    private val queue = ArrayDeque<File>()
    val playing: Boolean get() = player != null

    /** Spielt die Ansage, wenn sie schon erzeugt wurde – läuft gerade eine, wird angehängt. @return false → Handy-Stimme. */
    fun play(text: String, rate: Float): Boolean {
        val voice = settings.voice ?: return false
        val f = file(voice, text, rate)
        if (!f.exists()) return false
        if (player != null) queue.addLast(f) else playFile(f)
        return true
    }

    /** Probehören auf der Auswahlseite (blockierend für den Download, im IO-Thread aufrufen). */
    fun preview(voice: CloudVoice, text: String, rate: Float) {
        val tts = engine(voice.provider) ?: return
        val f = file(voice, text, rate)
        if (!f.exists()) f.writeBytes(tts.synthesize(text, voice, rate))
        playFile(f)
    }

    private fun playFile(f: File) {
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(f.absolutePath)
            setVolume(volume, volume)
            setOnCompletionListener {
                it.release(); if (player === it) player = null
                queue.removeFirstOrNull()?.let { next -> playFile(next) }
            }
            prepare()
            start()
        }
    }

    fun stop() { queue.clear(); player?.release(); player = null }

    /** Speicherbedarf der erzeugten Ansagen in MB. */
    fun cacheSizeMb() = dir.listFiles().orEmpty().sumOf { it.length() } / 1_048_576.0
}
