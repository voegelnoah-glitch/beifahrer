package at.beifahrer

import android.app.Application
import at.beifahrer.nav.DriveController

class BeifahrerApp : Application() {
    /** Gemeinsamer Zustand für Handy-Oberfläche und Android Auto. */
    val controller: DriveController by lazy { DriveController(this) }

    override fun onCreate() {
        super.onCreate()
        controller.connectCloud() // anonyme Anmeldung bei Firebase für die gemeinsamen Gefahrenstellen
        controller.checkForUpdate() // neue Version auf GitHub?
    }
}
