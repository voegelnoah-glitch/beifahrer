package at.beifahrer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import at.beifahrer.ui.BeifahrerScreen
import at.beifahrer.ui.BeifahrerTheme
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator

class MainActivity : ComponentActivity() {

    private val controller get() = (application as BeifahrerApp).controller

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) initNavigation()
        else controller.showMessage("Ohne Standort kann die App keine Kurven ansagen.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BeifahrerTheme { BeifahrerScreen(controller) } }

        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    /** Google verlangt einmalig die Zustimmung zu den Navigations-Nutzungsbedingungen. */
    private fun initNavigation() {
        if (controller.state.value.navigatorReady) return
        if (NavigationApi.areTermsAccepted(application)) getNavigator()
        else NavigationApi.showTermsAndConditionsDialog(this, getString(R.string.app_name)) { accepted ->
            if (accepted) getNavigator() else controller.showMessage("Ohne Zustimmung funktioniert die Navigation nicht.")
        }
    }

    private fun getNavigator() {
        NavigationApi.getNavigator(this, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(navigator: Navigator) = controller.attachNavigator(navigator)
            override fun onError(errorCode: Int) {
                // Häufigste Ursache: API-Schlüssel falsch oder "Navigation SDK" in der Cloud Console nicht aktiviert
                controller.showMessage("Navigation konnte nicht starten (Fehler $errorCode). API-Schlüssel und freigeschaltete APIs prüfen.")
            }
        })
    }
}
