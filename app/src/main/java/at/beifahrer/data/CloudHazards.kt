package at.beifahrer.data

import android.util.Log
import at.beifahrer.pacenote.Geo
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import kotlin.math.cos
import kotlin.math.hypot

/** Arten von Gefahrenstellen – dieselbe Liste steht in firestore.rules. */
val HAZARD_KINDS = listOf("Bodenwelle", "Schlagloch", "Rollsplitt", "Nässe / Eis", "Wildwechsel", "Kuppe uneinsehbar", "Steilkurve", "Baustelle", "Sonstiges")
private val LEGACY_KINDS = mapOf("Glätte" to "Nässe / Eis")

/** Wie lange eine Meldung gilt, bevor sie verschwindet (wenn niemand sie bestätigt). */
private fun lifetimeDays(kind: String) = when (kind) {
    "Rollsplitt", "Nässe / Eis", "Wildwechsel" -> 30
    "Kuppe uneinsehbar" -> 3650
    "Baustelle" -> 60
    "Schlagloch" -> 120
    else -> 365 // Bodenwelle, Steilkurve, Sonstiges bleiben lange
}

/** Eine Gefahrenstelle aus der Cloud. */
data class CloudHazard(
    val id: String,
    val lat: Double,
    val lng: Double,
    val kind: String,
    val note: String,
    val sensor: Boolean,
    val createdBy: String,
    val createdByName: String,          // Vorname aus dem Google-Konto
    val createdAt: Date?,
    val confirmedBy: List<String>,
    val goneBy: List<String>,
    val group: String,                  // leer = für alle Fahrer, sonst nur für diese Gruppe
) {
    fun visibleFor(myGroup: String) = group.isBlank() || group == myGroup

    /** Wird angesagt? Sensor-Funde erst, wenn zwei verschiedene Leute (bzw. Fahrten) sie bestätigt haben. */
    val active: Boolean get() = goneBy.size < confirmedBy.size + 2 && (!sensor || confirmedBy.size >= 2)
}

/**
 * Gemeinsame Gefahrenstellen aller Nutzer – gespeichert in Google Firebase (Firestore).
 *
 *  - Anmeldung: anonym, ohne Konto. Jeder Nutzer hat eine feste, zufällige ID.
 *  - Abfrage: nur Stellen entlang der aktuellen Route (Geohash-Bereiche), live: neue Meldungen anderer kommen sofort an.
 *  - Offline: Firestore speichert lokal zwischen. Meldungen ohne Netz werden später automatisch hochgeladen.
 *  - Aufräumen: "Weg"-Stimmen und Ablaufdatum ([lifetimeDays]); dafür in der Firebase-Konsole eine TTL-Regel auf "expiresAt" setzen.
 */
class CloudHazards {

    /** Ohne google-services.json (Firebase noch nicht eingerichtet) läuft die App einfach ohne Cloud. */
    val enabled: Boolean = runCatching { FirebaseApp.getInstance() }.isSuccess
    private val auth: FirebaseAuth? = if (enabled) FirebaseAuth.getInstance() else null
    private val colOrNull = if (enabled) FirebaseFirestore.getInstance().collection("hazards") else null
    private val listeners = mutableListOf<ListenerRegistration>()
    private val found = mutableMapOf<String, CloudHazard>()

    private val _hazards = MutableStateFlow<List<CloudHazard>>(emptyList())
    /** Alle Stellen im beobachteten Gebiet (auch inaktive, für die Liste mit Abstimmung). */
    val hazards: StateFlow<List<CloudHazard>> = _hazards

    val uid: String? get() = auth?.currentUser?.uid

    /** Beobachtet alle Stellen entlang einer Route (Kreise mit 5 km Radius alle 8 km). */
    fun watchRoute(route: List<Geo>) {
        stop()
        val col = colOrNull ?: return
        val centers = mutableListOf(route.first())
        var acc = 0.0
        for (i in 1 until route.size) {
            acc += distM(route[i - 1], route[i])
            if (acc >= 8_000) { centers += route[i]; acc = 0.0 }
        }
        centers += route.last()
        val bounds = centers.flatMap { GeoFireUtils.getGeoHashQueryBounds(GeoLocation(it.lat, it.lng), 5_000.0) }
            .distinctBy { it.startHash + it.endHash }
        for (b in bounds) {
            listeners += col.orderBy("geohash").startAt(b.startHash).endAt(b.endHash).addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "Cloud-Abfrage fehlgeschlagen", err); return@addSnapshotListener }
                snap?.documentChanges?.forEach { ch ->
                    val d = ch.document
                    if (ch.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) found.remove(d.id)
                    else { val h = parse(d.id, d.data); if (h != null) found[d.id] = h else found.remove(d.id) }
                }
                publish()
            }
        }
    }

    /** Beobachtet die Umgebung eines Punktes (für die Liste ohne Route). */
    fun watchAround(center: Geo, radiusM: Double = 20_000.0) {
        stop()
        val col = colOrNull ?: return
        GeoFireUtils.getGeoHashQueryBounds(GeoLocation(center.lat, center.lng), radiusM).forEach { b ->
            listeners += col.orderBy("geohash").startAt(b.startHash).endAt(b.endHash).addSnapshotListener { snap, _ ->
                snap?.documents?.forEach { d -> val h = parse(d.id, d.data ?: emptyMap()); if (h != null) found[d.id] = h }
                publish()
            }
        }
    }

    fun stop() { listeners.forEach { it.remove() }; listeners.clear(); found.clear(); publish() }

    /**
     * Neue Stelle melden. Gibt es dieselbe Art schon im Umkreis von 25 m, wird stattdessen bestätigt.
     * Funktioniert auch offline (wird nachgereicht).
     */
    fun report(lat: Double, lng: Double, kind: String, note: String, sensor: Boolean, firstName: String?, group: String) {
        val col = colOrNull ?: return
        val me = uid ?: return
        require(kind in HAZARD_KINDS)
        val near = found.values.firstOrNull { it.kind == kind && distM(Geo(it.lat, it.lng), Geo(lat, lng)) < 25 }
        if (near != null) { confirm(near.id); return }
        val expires = Timestamp(Date(System.currentTimeMillis() + lifetimeDays(kind) * 86_400_000L))
        col.add(
            mapOf(
                "lat" to lat, "lng" to lng,
                "geohash" to GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lng)),
                "kind" to kind, "note" to note.take(80), "source" to if (sensor) "sensor" else "user",
                "createdBy" to me, "createdByName" to (firstName ?: ""), "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to expires,
                "confirmedBy" to listOf(me), "goneBy" to emptyList<String>(), "group" to group.take(24),
            )
        )
    }

    /** "Noch da" – nimmt gleichzeitig ein eigenes "Weg" zurück. */
    fun confirm(id: String) {
        val me = uid ?: return
        (colOrNull ?: return).document(id).update(mapOf("confirmedBy" to FieldValue.arrayUnion(me), "goneBy" to FieldValue.arrayRemove(me)))
    }

    /** "Weg" – nimmt gleichzeitig ein eigenes "Noch da" zurück. */
    fun markGone(id: String) {
        val me = uid ?: return
        (colOrNull ?: return).document(id).update(mapOf("goneBy" to FieldValue.arrayUnion(me), "confirmedBy" to FieldValue.arrayRemove(me)))
    }

    /** Eigene Meldung löschen. */
    fun delete(id: String) { (colOrNull ?: return).document(id).delete() }

    private fun publish() { _hazards.value = found.values.toList() }

    @Suppress("UNCHECKED_CAST")
    private fun parse(id: String, m: Map<String, Any?>): CloudHazard? {
        val kind = (m["kind"] as? String)?.let { LEGACY_KINDS[it] ?: it } ?: return null
        if (kind !in HAZARD_KINDS) return null                         // fremde Daten nie ungeprüft verwenden
        val lat = (m["lat"] as? Number)?.toDouble() ?: return null
        val lng = (m["lng"] as? Number)?.toDouble() ?: return null
        val expires = (m["expiresAt"] as? Timestamp)?.toDate()
        if (expires != null && expires.before(Date())) return null
        return CloudHazard(
            id, lat, lng, kind, (m["note"] as? String).orEmpty().take(80), m["source"] == "sensor",
            m["createdBy"] as? String ?: "", (m["createdByName"] as? String).orEmpty().take(30), (m["createdAt"] as? Timestamp)?.toDate(),
            (m["confirmedBy"] as? List<String>).orEmpty(), (m["goneBy"] as? List<String>).orEmpty(),
            (m["group"] as? String).orEmpty().take(24),
        )
    }

    companion object {
        private const val TAG = "CloudHazards"
        fun distM(a: Geo, b: Geo) = hypot((b.lat - a.lat) * 110_574.0, (b.lng - a.lng) * 111_320.0 * cos(Math.toRadians(a.lat)))
    }
}
