package at.beifahrer.data

import android.content.Context
import at.beifahrer.nav.Destination
import org.json.JSONArray
import org.json.JSONObject

/** Die letzten Fahrziele – für die Schnellauswahl am Handy und in Android Auto. */
class RecentDestinations(context: Context) {
    private val prefs = context.getSharedPreferences("recent", Context.MODE_PRIVATE)

    fun load(): List<Destination> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return List(arr.length()) { i -> arr.getJSONObject(i).let { Destination(it.getString("name"), it.optString("detail"), it.getString("placeId")) } }
    }

    fun add(d: Destination) {
        val list = (listOf(d) + load().filter { it.placeId != d.placeId }).take(8)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("detail", it.detail).put("placeId", it.placeId)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
