package at.beifahrer.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Provider(val label: String) { DEVICE("Handy"), GOOGLE("Google KI"), ELEVENLABS("ElevenLabs") }
enum class Gender { MALE, FEMALE, UNKNOWN }

/** Eine wählbare KI-Stimme. [id] ist der Name bei Google bzw. die voice_id bei ElevenLabs. */
data class CloudVoice(val provider: Provider, val id: String, val name: String, val gender: Gender, val tier: String)

/** Gemeinsame Schnittstelle für alle KI-Stimmen-Dienste. Alle Aufrufe blockieren – im IO-Thread nutzen. */
interface CloudTts {
    fun listVoices(): List<CloudVoice>
    /** Liefert MP3-Daten. */
    fun synthesize(text: String, voice: CloudVoice, rate: Float): ByteArray
}

/**
 * Google Cloud Text-to-Speech. Läuft mit demselben API-Schlüssel wie die Karte,
 * es muss nur zusätzlich die "Cloud Text-to-Speech API" aktiviert sein.
 */
class GoogleCloudTts(private val apiKey: String) : CloudTts {

    override fun listVoices(): List<CloudVoice> {
        val json = JSONObject(get("https://texttospeech.googleapis.com/v1/voices?languageCode=de-DE&key=$apiKey", ANDROID_KEY_HEADERS))
        val arr = json.optJSONArray("voices") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { o -> (0 until o.getJSONArray("languageCodes").length()).any { o.getJSONArray("languageCodes").getString(it) == "de-DE" } }
            .map { o ->
                val full = o.getString("name")               // z. B. de-DE-Chirp3-HD-Charon
                val tier = when {
                    "Chirp3-HD" in full -> "Chirp 3 HD"
                    "Studio" in full -> "Studio"
                    "Neural2" in full -> "Neural2"
                    "Wavenet" in full -> "WaveNet"
                    "Standard" in full -> "Standard"
                    else -> full.split('-').getOrElse(2) { "" }
                }
                val short = full.removePrefix("de-DE-").replace("Chirp3-HD-", "")
                val gender = when (o.optString("ssmlGender")) { "MALE" -> Gender.MALE; "FEMALE" -> Gender.FEMALE; else -> Gender.UNKNOWN }
                CloudVoice(Provider.GOOGLE, full, short, gender, tier)
            }
            .sortedBy { TIER_ORDER.indexOf(it.tier).let { i -> if (i < 0) 99 else i } }
    }

    override fun synthesize(text: String, voice: CloudVoice, rate: Float): ByteArray {
        val audio = JSONObject().put("audioEncoding", "MP3").put("speakingRate", rate.toDouble())
        val body = JSONObject()
            .put("input", JSONObject().put("text", text))
            .put("voice", JSONObject().put("languageCode", "de-DE").put("name", voice.id))
            .put("audioConfig", audio)
        val url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"
        val res = try {
            JSONObject(post(url, body.toString(), ANDROID_KEY_HEADERS))
        } catch (e: IllegalStateException) {
            // Manche Stimmtypen kennen kein Sprechtempo → ohne nochmal versuchen
            if (e.message?.startsWith("HTTP 400") != true) throw e
            audio.remove("speakingRate")
            JSONObject(post(url, body.toString(), ANDROID_KEY_HEADERS))
        }
        return Base64.decode(res.getString("audioContent"), Base64.DEFAULT)
    }

    companion object {
        val TIER_ORDER = listOf("Chirp 3 HD", "Studio", "Neural2", "WaveNet", "Standard")
        /** Der Schlüssel ist auf die App beschränkt – Google erkennt sie an Paketname + Signatur (SHA-1). */
        val ANDROID_KEY_HEADERS = mapOf("X-Android-Package" to "at.beifahrer", "X-Android-Cert" to "AB1B226C3B57428F079507D5C285A6DFB60F9550")
    }
}

/** ElevenLabs – sehr ausdrucksstarke Stimmen, eigener Schlüssel von elevenlabs.io nötig. */
class ElevenLabsTts(private val apiKey: String) : CloudTts {

    override fun listVoices(): List<CloudVoice> {
        val arr = JSONObject(get("https://api.elevenlabs.io/v1/voices", mapOf("xi-api-key" to apiKey))).getJSONArray("voices")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val g = o.optJSONObject("labels")?.optString("gender").orEmpty()
            CloudVoice(Provider.ELEVENLABS, o.getString("voice_id"), o.getString("name"),
                when (g) { "male" -> Gender.MALE; "female" -> Gender.FEMALE; else -> Gender.UNKNOWN },
                o.optString("category").ifBlank { "ElevenLabs" })
        }
    }

    override fun synthesize(text: String, voice: CloudVoice, rate: Float): ByteArray {
        val body = JSONObject()
            .put("text", text)
            .put("model_id", "eleven_multilingual_v2")
            .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.75).put("speed", rate.coerceIn(0.7f, 1.2f).toDouble()))
        return postBytes("https://api.elevenlabs.io/v1/text-to-speech/${voice.id}", body.toString(),
            mapOf("xi-api-key" to apiKey, "Accept" to "audio/mpeg"))
    }
}

// ---------- kleine HTTP-Helfer ----------

private fun open(url: String, headers: Map<String, String>) = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 10_000; readTimeout = 20_000
    headers.forEach { (k, v) -> setRequestProperty(k, v) }
}

private fun HttpURLConnection.readOrThrow(): ByteArray {
    if (responseCode !in 200..299) {
        val err = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw IllegalStateException("HTTP $responseCode: ${err.take(300)}")
    }
    return inputStream.use { it.readBytes() }
}

private fun get(url: String, headers: Map<String, String> = emptyMap()) = String(open(url, headers).readOrThrow())

private fun postBytes(url: String, json: String, headers: Map<String, String>): ByteArray = open(url, headers).run {
    requestMethod = "POST"; doOutput = true
    setRequestProperty("Content-Type", "application/json")
    outputStream.use { it.write(json.toByteArray()) }
    readOrThrow()
}

private fun post(url: String, json: String, headers: Map<String, String>) = String(postBytes(url, json, headers))
