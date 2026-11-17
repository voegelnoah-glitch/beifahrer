package at.beifahrer.data

import android.content.Context
import at.beifahrer.BuildConfig
import at.beifahrer.nav.Destination
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.tasks.await

/** Zielsuche mit Google Places (Autovervollständigung). */
class PlacesSearch(context: Context) {
    init {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.MAPS_API_KEY)
    }
    private val client = Places.createClient(context.applicationContext)
    private var token = AutocompleteSessionToken.newInstance()

    suspend fun suggest(query: String, near: LatLng?): List<Destination> {
        if (query.length < 2) return emptyList()
        val req = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .setCountries(listOf("AT", "DE", "CH", "IT", "LI"))
            .apply { near?.let { setOrigin(it); setLocationBias(CircularBounds.newInstance(it, 50_000.0)) } }
            .build()
        return client.findAutocompletePredictions(req).await().autocompletePredictions.map {
            Destination(it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString(), it.placeId)
        }
    }

    /** Nach einer Auswahl neue Sitzung beginnen (so rechnet Google die Suche günstiger ab). */
    fun newSession() { token = AutocompleteSessionToken.newInstance() }
}
