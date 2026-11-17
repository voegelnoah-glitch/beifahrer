package at.beifahrer.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import at.beifahrer.data.CloudHazard
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.navigation.NavigationView

/**
 * Die Google-Navigationskarte in Compose. Das NavigationView braucht die Lebenszyklus-Aufrufe der Activity.
 * Google-Kopfzeile und ETA-Karte sind aus – die App zeigt eigene (siehe DriveOverlay).
 */
@Composable
fun NavigationViewHost(
    modifier: Modifier = Modifier,
    following: Boolean,
    tilt: Boolean = true,
    hazards: List<CloudHazard> = emptyList(),
    onLongPress: (LatLng) -> Unit = {},
    padTop: Int = 0,        // Pixel oben, die von eigenen Anzeigen verdeckt sind
    padBottom: Int = 0,     // Pixel unten (Vorschau + Knöpfe)
) {
    val longPress by rememberUpdatedState(onLongPress)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember {
        NavigationView(context).apply {
            onCreate(Bundle())
            setHeaderEnabled(false)
            setEtaCardEnabled(false)
            setSpeedometerEnabled(false)
            setTripProgressBarEnabled(false) // der blaue Balken links würde die Anzeigen überdecken
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> view.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(following, tilt) {
        if (following) view.getMapAsync {
            it.followMyLocation(if (tilt) GoogleMap.CameraPerspective.TILTED else GoogleMap.CameraPerspective.TOP_DOWN_HEADING_UP)
        }
        onDispose {}
    }
    // Kamera nur im freien Bereich zwischen oberen und unteren Anzeigen – so ist dein Standort mittig sichtbar
    LaunchedEffect(padTop, padBottom) {
        view.getMapAsync { it.setPadding(0, padTop, 0, padBottom) }
    }
    // Lang drücken = Gefahr an dieser Stelle eintragen
    DisposableEffect(Unit) {
        view.getMapAsync { map -> map.setOnMapLongClickListener { longPress(it) } }
        onDispose {}
    }
    // Gefahrenstellen aller Nutzer als rote Marker
    val markers = remember { mutableListOf<Marker>() }
    LaunchedEffect(hazards) {
        view.getMapAsync { map ->
            markers.forEach { it.remove() }; markers.clear()
            hazards.filter { it.active }.forEach { h ->
                map.addMarker(
                    MarkerOptions().position(LatLng(h.lat, h.lng)).title(h.kind).snippet(h.note.ifBlank { null })
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )?.let { markers += it }
            }
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
