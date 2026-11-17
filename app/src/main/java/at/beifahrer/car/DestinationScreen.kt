package at.beifahrer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import at.beifahrer.nav.Destination
import at.beifahrer.nav.DriveController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Zielsuche im Auto: Tastatur (nur im Stand, das regelt Android Auto) oder Sprache,
 * darunter die letzten Ziele zum Antippen.
 */
class DestinationScreen(carContext: CarContext, private val controller: DriveController) : Screen(carContext) {

    private var results: List<Destination> = emptyList()
    private var query = ""
    private var searchJob: Job? = null

    override fun onGetTemplate(): Template {
        val shown = if (query.length >= 2) results else controller.state.value.recent
        val list = ItemList.Builder().apply {
            if (shown.isEmpty()) setNoItemsMessage(if (query.length >= 2) "Nichts gefunden" else "Noch keine Ziele – suche oben")
            shown.take(6).forEach { d ->
                addItem(Row.Builder().setTitle(d.name).apply { if (d.detail.isNotBlank()) addText(d.detail) }
                    .setOnClickListener { start(d) }.build())
            }
        }.build()
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(text: String) = search(text)
            override fun onSearchSubmitted(text: String) = search(text)
        })
            .setHeaderAction(Action.APP_ICON)
            .setSearchHint("Ziel suchen")
            .setShowKeyboardByDefault(false)
            .setItemList(list)
            .build()
    }

    private fun search(text: String) {
        query = text
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300)
            results = try { controller.places.suggest(text, null) } catch (e: Exception) { emptyList() }
            invalidate()
        }
    }

    private fun start(d: Destination) {
        controller.places.newSession()
        controller.startRoute(d)
        screenManager.push(NavScreen(carContext, controller))
    }
}
