package at.beifahrer.data

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.YearMonth
import java.time.ZoneId

/**
 * Gemeinsame Monatsgrenze für Routenstarts aller Nutzer – damit die Google-Navigation gratis bleibt
 * (Google: die ersten 1.000 Ziele pro Monat kostenlos). Zähler liegt in Firestore unter usage/nav-JJJJ-MM.
 */
class RouteBudget {
    private val db: FirebaseFirestore? =
        if (runCatching { FirebaseApp.getInstance() }.isSuccess) FirebaseFirestore.getInstance() else null

    /** true = Route darf starten (wurde gezählt), false = Grenze erreicht, null = Zähler nicht erreichbar. */
    suspend fun take(): Boolean? {
        val d = db ?: return null
        val ref = d.collection("usage").document("nav-" + YearMonth.now(ZoneId.of("Europe/Vienna")))
        return try {
            withTimeout(6_000) {
                d.runTransaction { tr ->
                    val n = tr.get(ref).getLong("count") ?: 0L
                    if (n >= LIMIT) false
                    else { tr.set(ref, mapOf("count" to n + 1)); true }
                }.await()
            }
        } catch (e: Exception) { null }
    }

    /** Wie viele Routen diesen Monat schon gestartet wurden (für die Einstellungen). */
    suspend fun used(): Long? {
        val d = db ?: return null
        return try {
            withTimeout(5_000) { d.collection("usage").document("nav-" + YearMonth.now(ZoneId.of("Europe/Vienna"))).get().await().getLong("count") ?: 0L }
        } catch (e: Exception) { null }
    }

    companion object { const val LIMIT = 950L }
}
