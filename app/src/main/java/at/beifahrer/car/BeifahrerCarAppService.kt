package at.beifahrer.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import at.beifahrer.BeifahrerApp
import at.beifahrer.nav.Mode

/** Einstieg für Android Auto. */
class BeifahrerCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            val controller = (application as BeifahrerApp).controller
            // Läuft am Handy schon eine Fahrt, direkt die Karte zeigen – sonst Zielsuche
            return if (controller.state.value.mode == Mode.DRIVING) NavScreen(carContext, controller)
            else DestinationScreen(carContext, controller)
        }
    }
}
