package at.beifahrer.nav

import android.app.Application
import android.location.Location
import android.util.Log
import at.beifahrer.BuildConfig
import at.beifahrer.ai.AiVoicePlayer
import at.beifahrer.ai.CloudVoice
import at.beifahrer.ai.Provider
import at.beifahrer.data.ElevationClient
import at.beifahrer.data.CloudHazard
import at.beifahrer.data.CloudHazards
import at.beifahrer.data.Account
import at.beifahrer.data.AppSettings
import at.beifahrer.data.SettingsStore
import at.beifahrer.data.UpdateInfo
import at.beifahrer.data.Updater
import at.beifahrer.data.PlacesSearch
import at.beifahrer.data.RecentDestinations
import at.beifahrer.pacenote.BumpDetector
import at.beifahrer.pacenote.CallStyle
import at.beifahrer.pacenote.CurveSpeed
import at.beifahrer.pacenote.DriveStyle
import at.beifahrer.pacenote.Geo
import at.beifahrer.pacenote.LocalProjection
import at.beifahrer.pacenote.NoteType
import at.beifahrer.pacenote.PaceBook
import at.beifahrer.pacenote.PaceNoteAnnouncer
import at.beifahrer.pacenote.PaceNoteEngine
import at.beifahrer.pacenote.PaceText
import at.beifahrer.pacenote.RoutePoint
import at.beifahrer.pacenote.RouteTracker
import at.beifahrer.pacenote.projectHazards
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoadSnappedLocationProvider
import com.google.android.libraries.navigation.SimulationOptions
import com.google.android.libraries.navigation.Waypoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Verbindet alles: Google-Navigation (Route + Position), Kurven-Engine, Stimme, Sensor und Gefahren-Speicher.
 * Eine Instanz für die ganze App, damit Handy und Android Auto denselben Zustand sehen.
 */
class DriveController(private val app: Application) {

    private val _state = MutableStateFlow(DriveState())
    val state: StateFlow<DriveState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val announcer = PaceNoteAnnouncer(app)
    val aiVoice = AiVoicePlayer(app)
    private var prepJob: Job? = null
    val cloud = CloudHazards()
    val account = Account()
    private var cloudJob: Job? = null
    private val recent = RecentDestinations(app)
    private val elevation = ElevationClient()
    val places by lazy { PlacesSearch(app) }
    private val bumps = BumpDetector(app) { strength -> onBumpDetected(strength) }

    private var navigator: Navigator? = null
    private var locationProvider: RoadSnappedLocationProvider? = null
    private var proj: LocalProjection? = null
    private var tracker: RouteTracker? = null
    private var routeGeo: List<Geo> = emptyList()
    private var routeZ: DoubleArray = DoubleArray(0)
    private var lastLocation: Location? = null
    private val spoken = mutableSetOf<Int>()
    private val overWarned = mutableSetOf<Int>()
    private val reminded = mutableSetOf<Int>()
    private val settingsStore = SettingsStore(app)
    val updater = Updater(app)
    private var buildJob: Job? = null

    init {
        val saved = settingsStore.load()
        _state.update { it.copy(recent = recent.load(), aiVoiceLabel = aiVoice.settings.voice?.let(::label), settings = saved) }
        applyToEngines(saved)
    }

    // ---------------- Cloud ----------------

    // ---------------- Updates ----------------

    /** Beim Start und auf Knopfdruck: gibt es eine neuere Version auf GitHub? */
    fun checkForUpdate(manual: Boolean = false) = scope.launch {
        val u = try { updater.check() } catch (e: Exception) { null }
        _state.update { it.copy(update = u) }
        if (manual) { showMessage(if (u != null) "Neue Version ${u.versionName} verfügbar." else "Du hast die neueste Version."); delay(2500); showMessage(null) }
    }

    /** Lädt das Update und öffnet den Installer. Android fragt einmal nach Erlaubnis. */
    fun installUpdate() = scope.launch {
        val u = _state.value.update ?: return@launch
        if (!updater.canInstall()) { showMessage("Bitte „Installation aus dieser Quelle erlauben“ einschalten, dann nochmal tippen."); updater.openInstallPermission(); return@launch }
        try {
            val file = updater.download(u) { p -> _state.update { it.copy(updateProgress = p) } }
            _state.update { it.copy(updateProgress = null) }
            updater.install(file)
        } catch (e: Exception) {
            Log.w(TAG, "Update fehlgeschlagen", e)
            _state.update { it.copy(updateProgress = null) }
            showMessage("Update konnte nicht geladen werden. Internet prüfen.")
        }
    }

    fun connectCloud() {
        scope.launch {
            try { account.ensureSignedIn(); _state.update { it.copy(cloudOnline = true) } }
            catch (e: Exception) { Log.w(TAG, "Cloud-Anmeldung fehlgeschlagen", e); _state.update { it.copy(cloudOnline = false) } }
        }
        scope.launch { account.info.collect { a -> _state.update { it.copy(account = a) } } }
        // Neue/geänderte Stellen von allen Nutzern -> Gebetbuch neu rechnen (kurz gesammelt)
        cloudJob?.cancel()
        cloudJob = scope.launch {
            cloud.hazards.collect { list ->
                _state.update { it.copy(cloudHazards = list) }
                if (_state.value.book != null) { delay(800); rebuildKeepingSpoken() }
            }
        }
    }

    // ---------------- Navigator ----------------

    fun attachNavigator(nav: Navigator) {
        navigator = nav
        nav.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
        nav.addRouteChangedListener { buildPaceBook() }            // auch bei Neuberechnung
        nav.addArrivalListener {
            _state.update { it.copy(mode = Mode.ARRIVED) }
            speak("Ziel erreicht.", rateFor(_state.value.style))
            stopSensors()
        }
        nav.addRemainingTimeOrDistanceChangedListener(10, 50) {
            nav.currentTimeAndDistance?.let { td -> _state.update { it.copy(remainingMeters = td.meters, remainingSeconds = td.seconds) } }
        }
        // Abbiege-Infos für Banner und Android Auto (kommen im NavInfoReceivingService an)
        nav.registerServiceForNavUpdates(app.packageName, NavInfoReceivingService::class.java.name, 1)

        locationProvider = NavigationApi.getRoadSnappedLocationProvider(app)
        locationProvider?.addLocationListener(object : RoadSnappedLocationProvider.LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)
        })
        _state.update { it.copy(navigatorReady = true) }
    }

    fun onNavInfo(info: NavInfo) {
        val step = info.currentStep
        _state.update { it.copy(navStepText = step?.fullInstructionText, navStepMeters = info.distanceToCurrentStepMeters) }
    }

    fun showMessage(msg: String?) = _state.update { it.copy(message = msg) }

    // ---------------- Route ----------------

    fun startRoute(dest: Destination) {
        val nav = navigator ?: return showMessage("Navigation wird noch geladen …")
        _state.update { it.copy(mode = Mode.ROUTING, destination = dest, message = null, book = null, current = null) }
        val waypoint = try {
            Waypoint.builder().setPlaceIdString(dest.placeId).setTitle(dest.name).build()
        } catch (e: Exception) {
            _state.update { it.copy(mode = Mode.IDLE) }
            return showMessage("Dieses Ziel kann nicht angefahren werden.")
        }
        nav.setDestination(waypoint).setOnResultListener { status ->
            if (status == Navigator.RouteStatus.OK) {
                recent.add(dest); _state.update { it.copy(recent = recent.load()) }
                nav.startGuidance()
                if (_state.value.simulate) nav.simulator.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(1.5f))
                _state.update { it.copy(mode = Mode.DRIVING) }
                startSensors()
                buildPaceBook()
            } else {
                _state.update { it.copy(mode = Mode.IDLE) }
                showMessage(routeError(status))
            }
        }
    }

    fun stop() {
        navigator?.apply { simulator.unsetUserLocation(); stopGuidance(); clearDestinations() }
        stopSensors()
        overWarned.clear(); reminded.clear()
        cloud.stop()
        aiVoice.stop(); prepJob?.cancel()
        spoken.clear()
        _state.update { it.copy(mode = Mode.IDLE, book = null, current = null, upcoming = emptyList(), navStepText = null) }
    }

    /** Route von Google holen, Höhen dazu, Gebetbuch erzeugen. */
    private fun buildPaceBook() {
        val nav = navigator ?: return
        buildJob?.cancel()
        buildJob = scope.launch {
            val latLngs = nav.routeSegments.flatMap { it.latLngs }
            if (latLngs.size < 2) return@launch
            routeGeo = latLngs.map { Geo(it.latitude, it.longitude) }
            val p = LocalProjection(routeGeo.first()).also { proj = it }
            cloud.watchRoute(routeGeo)
            routeZ = try {
                withContext(Dispatchers.IO) { elevation.heightsAlong(routeGeo, p) }
            } catch (e: Exception) {
                Log.w(TAG, "Höhendaten fehlen", e)
                showMessage("Keine Höhendaten – Kuppen werden diesmal nicht angesagt.")
                DoubleArray(routeGeo.size)
            }
            rebuildBook()
            spoken.clear()
            prepareAiVoice()
            val book = _state.value.book ?: return@launch
            speak("Gebetbuch geladen.", rateFor(_state.value.style))
        }
    }

    /** Analyse (schnell, < 50 ms) mit aktuellem Gefahren-Stand neu laufen lassen. */
    private suspend fun rebuildBook() {
        val p = proj ?: return
        val pts = routeGeo.mapIndexed { i, g -> p.toLocal(g, routeZ.getOrElse(i) { 0.0 }) }
        val book = withContext(Dispatchers.Default) {
            val plain = PaceNoteEngine.analyze(pts) // nur für die Projektion der Gefahren
            val set = _state.value.settings
            val stored = cloud.hazards.value.filter { it.active && it.visibleFor(set.group) }
            val hz = projectHazards(plain, p, stored.map { Geo(it.lat, it.lng) to it.kind }, stored.map { it.sensor })
            PaceNoteEngine.analyze(pts, hz)
        }
        tracker = RouteTracker(book, p)
        _state.update { it.copy(book = book) }
    }

    // ---------------- Fahrt ----------------

    private fun onLocation(loc: Location) {
        lastLocation = loc
        bumps.speedMs = loc.speed
        val st = _state.value
        val book = st.book ?: return
        if (st.mode != Mode.DRIVING) return
        val s = tracker?.locate(Geo(loc.latitude, loc.longitude)) ?: return
        val idx = book.notes.indexOfFirst { (if (it.type == NoteType.CURVE) it.sEnd else it.s + 5) > s }
        val cur = book.notes.getOrNull(idx)
        _state.update {
            it.copy(
                posS = s, speedKmh = (loc.speed * 3.6f).toInt(), current = cur,
                upcoming = if (idx >= 0) book.notes.drop(idx + 1).take(3) else emptyList(),
                distToCurrent = cur?.let { n -> n.s - s } ?: 0.0,
            )
        }
        announce(book, s, loc.speed.toDouble())
        checkOverspeed(book, s, loc.speed * 3.6)
    }

    private fun vmax(n: at.beifahrer.pacenote.PaceNote?): Int? {
        val st = _state.value
        return CurveSpeed.forNote(n, st.driveStyle, st.wet, st.speedLimit)
    }

    /** Warnt, wenn man bis 150 m vor oder in einer Kurve mehr als 5 km/h über der Empfehlung fährt. */
    private fun checkOverspeed(book: PaceBook, s: Double, kmh: Float) {
        val tol = _state.value.settings.overTol
        val next = book.notes.firstOrNull { it.type == NoteType.CURVE && it.sEnd > s && it.s - s < 200 }
        val v = vmax(next)
        // Vor der Kurve nur warnen, wenn normales Bremsen (3 m/s²) nicht mehr reicht; in der Kurve direkt vergleichen
        val allowed = if (next != null && v != null && next.s > s) kotlin.math.sqrt((v / 3.6) * (v / 3.6) + 2 * 3.0 * (next.s - s)) * 3.6 else v?.toDouble()
        val over = tol > 0 && next != null && allowed != null && kmh > allowed + tol
        _state.update { it.copy(currentVmax = vmax(it.current), overspeed = over) }
        // Zu schnell wird nur angezeigt (rot), nicht gesagt
    }

    private fun announce(book: PaceBook, s: Double, v: Double) {
        val set = _state.value.settings
        val lead = (v * set.leadSec).coerceIn(50.0, 280.0) // Vorlauf aus den Einstellungen
        val notes = book.notes
        for (i in notes.indices) {
            val n = notes[i]
            if (n.id in spoken) continue
            if (n.s - s > lead) break
            if (n.sEnd < s) { spoken += n.id; continue }
            if (!wanted(n)) { spoken += n.id; continue }
            val style = _state.value.style
            val text = StringBuilder(spokenText(n, style)); spoken += n.id
            var j = i
            var vmin = vmax(n)
            while (notes[j].link != null && j + 1 < notes.size) {
                j++; spoken += notes[j].id; text.append(' ').append(spokenText(notes[j], style))
                vmax(notes[j])?.let { v2 -> vmin = vmin?.let { minOf(it, v2) } ?: v2 }
            }
            val st = _state.value
            val sentence = text.toString()
            speak(sentence, rateFor(style))
            _state.update { it.copy(lastSpoken = sentence) }
            return
        }
        // Zweite, kurze Ansage etwa 2 s vor engen oder gefährlichen Kurven: "Rechts 3!"
        if (!set.remind) return
        val near = maxOf(35.0, v * 2)
        for (n in notes) {
            if (n.s - s > near) break
            if (n.type != NoteType.CURVE || n.id in reminded || n.id !in spoken || n.s < s) continue
            reminded += n.id
            if (!(n.danger || n.hairpin || n.grade <= 3) || !wanted(n)) continue
            if (announcer.speaking || aiVoice.playing) continue      // nicht dazwischenreden
            speak(PaceText.title(n, set.style) + "!", rateFor(set.style))
            return
        }
    }

    /** Was der Fahrer angesagt haben will (Einstellungen → "Was ansagen?"). */
    private fun wanted(n: at.beifahrer.pacenote.PaceNote): Boolean {
        val set = _state.value.settings
        fun on(k: String) = k !in set.warnOff
        return when (n.type) {
            NoteType.CURVE -> when {
                n.danger -> true                                   // gefährliche Kurven immer
                n.hairpin -> on("haarnadel")
                on("ziehtzu") && "zieht zu" in n.mods -> true
                else -> { val g = if (n.square) 2 else n.grade; on(if (g <= 2) "eng" else if (g <= 4) "mittel" else "schnell") }
            }
            NoteType.VERTICAL -> on(if ("Kuppe" in n.kind) "kuppe" else "senke")
            NoteType.HAZARD -> on(n.kind.substringBefore(" (").let { if (it == "Glätte") "Nässe / Eis" else it })
        }
    }

    /** Satz für eine Ansage, ggf. ohne Abstand ("150"). */
    private fun spokenText(n: at.beifahrer.pacenote.PaceNote, style: CallStyle): String {
        val t = PaceText.spoken(n, style)
        if (_state.value.settings.sayDist) return t
        return t.replace(Regex(", (\\d+|lange Gerade)$"), "").replace(", danach lange Gerade", "")
    }

    // ---------------- Stimme ----------------

    private fun rateFor(style: CallStyle) = if (style == CallStyle.RALLY) announcer.preset.rate else minOf(announcer.preset.rate, 1.1f)

    /** KI-Stimme, wenn die Ansage schon erzeugt ist – sonst sofort die Handy-Stimme (nie warten!). */
    private fun speak(sentence: String, rate: Float) {
        if (!_state.value.voiceOn) return
        if (aiVoice.active && aiVoice.play(sentence, rate)) return
        announcer.say(sentence, rate)
    }

    /** Alle Sätze, die bei dieser Route gesagt werden (gleiche Verkettung wie in announce()). */
    private fun allCalls(book: PaceBook, style: CallStyle): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val n = book.notes
        while (i < n.size) {
            val sb = StringBuilder(spokenText(n[i], style))
            var j = i
            var vmin = vmax(n[i])
            while (n[j].link != null && j + 1 < n.size) {
                j++; sb.append(' ').append(spokenText(n[j], style))
                vmax(n[j])?.let { v2 -> vmin = vmin?.let { minOf(it, v2) } ?: v2 }
            }
            val st = _state.value
            out += sb.toString(); i = j + 1
        }
        val reminders = book.notes.filter { it.type == NoteType.CURVE }.map { PaceText.title(it, style) + "!" }.distinct()
        return out + reminders + listOf("Ziel erreicht.", "Gebetbuch geladen.")
    }

    /** Erzeugt die Ansagen der Route einmal mit der KI-Stimme (läuft im Hintergrund, Fahrt kann sofort los). */
    fun prepareAiVoice() {
        val book = _state.value.book ?: return
        if (!aiVoice.active) return
        val style = _state.value.style
        prepJob?.cancel()
        prepJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    aiVoice.prefetch(allCalls(book, style), rateFor(style)) { done, total ->
                        _state.update { it.copy(voicePrep = "KI-Stimme: $done von $total Ansagen erzeugt") }
                    }
                }
                _state.update { it.copy(voicePrep = null) }
            } catch (e: Exception) {
                Log.w(TAG, "KI-Stimme fehlgeschlagen", e)
                _state.update { it.copy(voicePrep = "KI-Stimme nicht erreichbar – Handy-Stimme spricht.") }
                delay(4000); _state.update { it.copy(voicePrep = null) }
            }
        }
    }

    fun chooseVoice(v: CloudVoice) {
        aiVoice.settings.voice = v
        if (v.provider == Provider.DEVICE) announcer.selectVoice(v.id)
        _state.update { it.copy(aiVoiceLabel = label(v), voiceName = v.name) }
        prepareAiVoice()
    }

    suspend fun loadVoices(p: Provider): List<CloudVoice> = when (p) {
        Provider.DEVICE -> announcer.germanVoices().map {
            CloudVoice(Provider.DEVICE, it.name, it.name.substringAfterLast('-').ifBlank { it.name }, at.beifahrer.ai.Gender.UNKNOWN,
                if (it.isNetworkConnectionRequired) "online" else "offline")
        }
        else -> withContext(Dispatchers.IO) { aiVoice.engine(p)?.listVoices().orEmpty() }
    }

    suspend fun previewVoice(v: CloudVoice, text: String) {
        val rate = rateFor(_state.value.style)
        if (v.provider == Provider.DEVICE) { announcer.selectVoice(v.id); announcer.say(text, rate, force = true); return }
        withContext(Dispatchers.IO) { aiVoice.preview(v, text, rate) }
    }

    private fun label(v: CloudVoice) = when (v.provider) {
        Provider.DEVICE -> "Handy-Stimme"
        else -> "${v.name} · ${v.provider.label}"
    }

    // ---------------- Gefahren ----------------

    /** Gefahr an der aktuellen Position melden (Knopf während der Fahrt / in Android Auto). */
    fun reportHazard(kind: String, note: String = "") {
        val loc = lastLocation ?: return showMessage("Noch keine Position.")
        reportHazardAt(loc.latitude, loc.longitude, kind, note)
    }

    /** Gefahr an einer beliebigen Stelle eintragen (lang auf die Karte drücken). */
    fun reportHazardAt(lat: Double, lng: Double, kind: String, note: String, vis: String = _state.value.settings.vis) {
        val acc = account.info.value ?: return showMessage("Keine Verbindung zur Cloud – bitte Internet prüfen.")
        if (!acc.google) return showMessage("Zum Eintragen bitte zuerst mit Google anmelden.")
        val group = _state.value.settings.group
        if (vis == "group" && group.isBlank()) return showMessage("Für „Meine Gruppe“ zuerst in den Einstellungen einen Gruppen-Code eintragen.")
        cloud.report(lat, lng, kind, note, sensor = false, firstName = acc.firstName, group = if (vis == "group") group else "")
        if (_state.value.book == null) cloud.watchAround(Geo(lat, lng))
        showMessage(if (vis == "group") "$kind gespeichert. Deine Gruppe bekommt die Stelle." else "$kind gespeichert. Alle Fahrer bekommen die Stelle.")
        scope.launch { delay(3000); showMessage(null) }
    }

    private fun needGoogle(): Boolean {
        if (account.info.value?.google == true) return false
        showMessage("Zum Abstimmen bitte mit Google anmelden."); return true
    }
    fun confirmHazard(h: CloudHazard) { if (!needGoogle()) cloud.confirm(h.id) }
    fun hazardGone(h: CloudHazard) { if (!needGoogle()) cloud.markGone(h.id) }
    fun deleteHazard(h: CloudHazard) = cloud.delete(h.id)
    fun isMine(h: CloudHazard) = h.createdBy == cloud.uid
    fun currentPosition(): Pair<Double, Double>? = lastLocation?.let { it.latitude to it.longitude }

    fun signInWithGoogle(activity: android.app.Activity) = scope.launch {
        try { account.signInWithGoogle(activity); showMessage("Angemeldet als ${account.info.value?.firstName ?: "Google-Nutzer"}.") }
        catch (e: Exception) { Log.w(TAG, "Google-Anmeldung", e); showMessage("Anmeldung abgebrochen oder fehlgeschlagen.") }
        delay(2500); showMessage(null)
    }

    fun signOut() = scope.launch { account.signOut(app) }

    /** Gefahrenliste ohne Route: Umgebung der letzten Position beobachten. */
    fun watchNearby() {
        if (_state.value.book != null) return
        lastLocation?.let { cloud.watchAround(Geo(it.latitude, it.longitude)) }
    }

    /** Sensor-Fund: wird erst angesagt, wenn ein zweiter Nutzer (oder eine zweite Fahrt) ihn bestätigt. */
    private fun onBumpDetected(strength: Float) {
        val loc = lastLocation ?: return
        Log.i(TAG, "Bodenwelle erkannt (%.1f m/s²)".format(strength))
        val acc = account.info.value ?: return
        if (acc.google) cloud.report(loc.latitude, loc.longitude, "Bodenwelle", "", sensor = true, firstName = acc.firstName, group = "")
    }

    private suspend fun rebuildKeepingSpoken() {
        val before = _state.value.book?.notes?.filter { it.id in spoken }?.map { it.s.toInt() }?.toSet() ?: emptySet()
        rebuildBook()
        val pos = _state.value.posS
        spoken.clear()
        _state.value.book?.notes?.forEach { if (it.s.toInt() in before || it.s <= pos) spoken += it.id }
    }

    private fun startSensors() { if (_state.value.settings.sensor) bumps.start() }
    private fun stopSensors() = bumps.stop()

    // ---------------- Einstellungen ----------------

    // ---------------- Einstellungen ----------------

    /** Ändert Einstellungen, speichert sie und wendet sie sofort an. */
    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val before = _state.value.settings
        val after = change(before)
        _state.update { it.copy(settings = after) }
        settingsStore.save(after)
        applyToEngines(after)
        if (before.driveStyle != after.driveStyle || before.wet != after.wet || before.speedLimit != after.speedLimit || before.overTol != after.overTol) overWarned.clear()
        val textChanged = before.style != after.style || before.preset != after.preset || before.sayDist != after.sayDist ||
            before.warnOff != after.warnOff
        if (textChanged) prepareAiVoice()
        if (before.group != after.group) scope.launch { rebuildKeepingSpoken() }
        if (before.sensor != after.sensor && _state.value.mode == Mode.DRIVING) { if (after.sensor) bumps.start() else bumps.stop() }
    }

    private fun applyToEngines(s: AppSettings) {
        announcer.enabled = s.voiceOn
        announcer.preset = s.preset
        announcer.volume = s.volume
        announcer.duck = s.duck
        aiVoice.volume = s.volume
    }

    fun toggleStyle() = updateSettings { it.copy(style = if (it.style == CallStyle.RALLY) CallStyle.PLAIN else CallStyle.RALLY) }
    fun setVoice(on: Boolean) = updateSettings { it.copy(voiceOn = on) }
    fun setSimulate(on: Boolean) = _state.update { it.copy(simulate = on) }
    fun setDriveStyle(d: DriveStyle) = updateSettings { it.copy(driveStyle = d) }
    fun setWet(wet: Boolean) = updateSettings { it.copy(wet = wet) }
    fun setSpeedLimit(kmh: Int) = updateSettings { it.copy(speedLimit = kmh.coerceIn(30, 130)) }
    fun setPreset(p: at.beifahrer.pacenote.VoicePreset) { updateSettings { it.copy(preset = p) }; announcer.preview() }
    fun germanVoices() = announcer.germanVoices()
    fun selectVoice(name: String) { announcer.selectVoice(name); _state.update { it.copy(voiceName = name) }; announcer.preview("Links 3 in Rechts Haarnadel") }

    private fun routeError(status: Navigator.RouteStatus) = when (status) {
        Navigator.RouteStatus.NO_ROUTE_FOUND -> "Keine Route gefunden."
        Navigator.RouteStatus.NETWORK_ERROR -> "Kein Internet."
        Navigator.RouteStatus.QUOTA_CHECK_FAILED -> "API-Schlüssel oder Kontingent prüfen."
        Navigator.RouteStatus.LOCATION_UNKNOWN -> "Standort unbekannt – GPS an?"
        else -> "Route konnte nicht berechnet werden ($status)."
    }

    companion object { private const val TAG = "Beifahrer" }
}
