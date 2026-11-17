package at.beifahrer.car

import android.app.Presentation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.widget.FrameLayout
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import at.beifahrer.R
import at.beifahrer.nav.DriveController
import at.beifahrer.nav.Mode
import at.beifahrer.pacenote.CallStyle
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.NavigationViewForAuto
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Android-Auto-Bildschirm während der Fahrt.
 *  - Karte: Googles NavigationViewForAuto auf der Autofläche (VirtualDisplay + Presentation)
 *  - Darüber: unsere Ansage-Karte (PaceNoteOverlayView), im freien Bereich rechts oben
 *  - Vorlage: Abbiege-Schritt, Ankunftszeit und Knöpfe (Gefahr, Ansagestil, Stimme, Beenden)
 */
class NavScreen(carContext: CarContext, private val controller: DriveController) : Screen(carContext), SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var navView: NavigationViewForAuto? = null
    private var overlay: PaceNoteOverlayView? = null
    private val navManager = carContext.getCarService(NavigationManager::class.java)

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        navManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() { controller.stop(); screenManager.pop() }
        })
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                navManager.navigationStarted()
                // Karte bei jeder Positionsänderung neu zeichnen (Overlay hat kein Update-Limit)
                lifecycleScope.launch { controller.state.collect { overlay?.update(it) } }
                // Vorlage nur aktualisieren, wenn sich der Abbiege-Text, der Modus oder Einstellungen ändern
                lifecycleScope.launch {
                    controller.state.map { listOf(it.navStepText, it.navStepMeters?.div(50), it.mode, it.style, it.voiceOn, it.remainingSeconds / 60) }
                        .distinctUntilChanged().collect { invalidate() }
                }
            }
            override fun onDestroy(owner: LifecycleOwner) { navManager.navigationEnded() }
        })
    }

    // ---------- Kartenfläche ----------

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay("BeifahrerAuto", container.width, container.height, container.dpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY).also { virtualDisplay = it }
        val pres = Presentation(carContext, vd.display).also { presentation = it }
        val nv = NavigationViewForAuto(pres.context).also { navView = it }
        nv.onCreate(null); nv.onStart(); nv.onResume()
        val ov = PaceNoteOverlayView(pres.context).also { overlay = it }
        pres.setContentView(FrameLayout(pres.context).apply { addView(nv); addView(ov) })
        pres.show()
        nv.getMapAsync { map -> map.followMyLocation(GoogleMap.CameraPerspective.TILTED) }
        ov.update(controller.state.value)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) { overlay?.visibleArea = Rect(visibleArea) }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        navView?.apply { onPause(); onStop(); onDestroy() }
        presentation?.dismiss()
        virtualDisplay?.release()
        navView = null; presentation = null; virtualDisplay = null; overlay = null
    }

    // ---------- Vorlage ----------

    override fun onGetTemplate(): Template {
        val st = controller.state.value
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_hazard)).setOnClickListener { controller.reportHazard("Bodenwelle") }.build())
            .addAction(Action.Builder().setTitle(if (st.style == CallStyle.RALLY) "R 3" else "Text").setOnClickListener { controller.toggleStyle() }.build())
            .addAction(Action.Builder().setIcon(icon(R.drawable.ic_volume)).setTitle(if (st.voiceOn) "An" else "Aus")
                .setOnClickListener { controller.setVoice(!st.voiceOn) }.build())
            .addAction(Action.Builder().setTitle("Ende").setOnClickListener { controller.stop(); screenManager.pop() }.build())
            .build()

        val builder = NavigationTemplate.Builder().setActionStrip(strip)
        if (st.mode == Mode.DRIVING) {
            val stepText = st.navStepText
            builder.setNavigationInfo(
                if (stepText == null) RoutingInfo.Builder().setLoading(true).build()
                else RoutingInfo.Builder().setCurrentStep(
                    Step.Builder(stepText).setManeuver(Maneuver.Builder(Maneuver.TYPE_STRAIGHT).setIcon(icon(R.drawable.ic_launcher)).build()).build(),
                    Distance.create((st.navStepMeters ?: 0).toDouble(), Distance.UNIT_METERS),
                ).build()
            )
            val arrival = System.currentTimeMillis() + st.remainingSeconds * 1000L
            builder.setDestinationTravelEstimate(
                TravelEstimate.Builder(
                    Distance.create(st.remainingMeters / 1000.0, Distance.UNIT_KILOMETERS),
                    DateTimeWithZone.create(arrival, TimeZone.getDefault()),
                ).setRemainingTimeSeconds(st.remainingSeconds.toLong()).build()
            )
        }
        return builder.build()
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()
}
