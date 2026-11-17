# Beifahrer – Rallye-Ansagen für Android und Android Auto

Die App navigiert mit Google zu einem Ziel und sagt dabei jede Kurve an wie ein Rallye-Beifahrer:
„Links 4 in Rechts 2 zieht zu, 150, Kuppe“. Wahlweise im Klartext: „Scharfe Rechtskurve, zieht zu“.

## Was die App kann

| Funktion | Woher die Daten kommen |
|---|---|
| Ziel suchen (Handy und Auto) | Google Places |
| Karte, Route, Abbiegehinweise | Google Navigation SDK |
| Kurven: Stufe 1–6, Haarnadel, 90°, zieht zu, öffnet, lang | eigene Berechnung aus der Routen-Geometrie (`PaceNoteEngine`) |
| Kuppen, Senken, „steil bergab“, Kuppe vor Kurve (nicht einsehbar) | Google Elevation API + eigene Berechnung |
| Bodenwellen | Beschleunigungssensor des Handys (lernt beim Fahren) |
| Schlagloch, Rollsplitt, Steilkurve, Glätte, Wildwechsel, Baustelle | selbst eintragen (lang auf die Karte drücken) – geteilt mit allen Nutzern über die Cloud |
| Android Auto | Kartenansicht mit Ansage-Karte, Zielsuche, Knöpfe für Gefahr/Stil/Ton |

**Empfohlenes Höchsttempo je Kurve:** v = √(Querbeschleunigung × Radius), je nach Fahrstil (Gemütlich 2,5 · Normal 3,5 · Sportlich 4,5 m/s²), bei Nässe 30 % weniger, nie über dem Tempolimit (Standard 100 km/h). Wird **nur angezeigt, nie gesagt** – standardmäßig nur auf dem Android-Auto-Bildschirm („max 40“). Reicht normales Bremsen bis zur Kurve nicht mehr, wird die Anzeige rot. Das ist ein Schätzwert aus der Kartengeometrie – Straßenzustand, Reifen und Sicht gehen vor.

**Die Kurven-Skala** (Radius der engsten Stelle): 1 unter 20 m · 2 bis 35 m · 3 bis 55 m · 4 bis 85 m · 5 bis 130 m · 6 bis 350 m.
Haarnadel = mindestens 135° und höchstens 30 m Radius. Die Zahl beschreibt die Straße, sie ist keine Tempo-Empfehlung.

## Aussehen und Einstellungen

Design: Nacht-Cockpit in Navy mit Neon-Cyan für alles Normale und Rot/Orange für Gefahren, 3D-Karte während der Fahrt, kursive Großbuchstaben für Titel.

Unter **⚙ Einstellungen** (Startbildschirm und während der Fahrt):

| Bereich | Was man einstellen kann |
|---|---|
| Warnung | **Wie früh ansagen** (sehr früh 8 s · früh 6,5 s · normal 5,5 s · spät 4 s vor der Kurve) · zweite kurze Erinnerung kurz davor („Rechts 3“, nur wenn gerade nichts gesprochen wird) |
| **Warnen vor** | jede Kategorie einzeln an/aus: Haarnadeln, enge Kurven 1–2, mittlere 3–4, schnelle 5–6, „zieht zu“, Kuppen, Senken & Mulden, und jede Gefahrenart (Bodenwelle, Schlagloch, Rollsplitt, Nässe/Eis, Wildwechsel, Kuppe uneinsehbar, Steilkurve, Baustelle, Sonstiges). Rot markierte, gefährliche Kurven kommen immer. |
| Ansage | Abstände („150“) · Stil Rallye-kurz / Normal erklärt |
| Kurventempo (**nur Anzeige**, nie gesprochen) | Höchsttempo anzeigen: nur Android Auto (Standard) / Handy + Auto / aus · rot bei zu schnell (+5/+10/+15, aus) · Fahrstil · trocken/nass · Tempolimit 80/100/130 |
| Stimme | an/aus · Sprechtempo · Lautstärke · Musik leiser bei Ansagen |
| Cloud & Gruppe | neue Meldungen für alle oder nur „Meine Gruppe“ · Gruppen-Code · Bodenwellen automatisch erkennen |
| Anzeige | Karte 3D / 2D |

**Gefahr melden** ist eine eigene Seite: Ort (lang auf die Karte drücken oder aktuelle Position, „Pin verschieben“), Art als Kacheln (Bodenwelle, Schlagloch, Rollsplitt, Nässe/Eis, Wildwechsel, Kuppe uneinsehbar, Steilkurve, Baustelle, Sonstiges), Notiz, „Sichtbar für: Alle Fahrer / Meine Gruppe“. Hinweis: Gruppen-Meldungen werden bei anderen nur ausgeblendet, sie sind nicht verschlüsselt.

**Stimme wählen**: Max (Rallye-Beifahrer, schnell & knapp), Lena (ruhig), Jonas (locker), Sophie (sportlich) – alles Google-KI-Stimmen – plus alle weiteren Google-Stimmen, ElevenLabs und Handy-Stimmen. „Eigene Stimme aufnehmen“ ist vorgesehen, aber noch nicht gebaut.

## Aufs Handy mit automatischen Updates

Siehe **AUFS-HANDY.md**: GitHub baut bei jeder Änderung automatisch die APK (`.github/workflows/android.yml`) und veröffentlicht sie. Die App prüft beim Start auf eine neue Version und installiert sie mit einem Tipp (Android fragt einmal nach). Code: `data/Updater.kt`.

## Einrichten (ca. 30 Minuten)

1. **Android Studio** installieren (aktuelle Version) und diesen Ordner öffnen. Gradle lädt alles selbst herunter. Wenn Android Studio neuere Bibliotheksversionen vorschlägt, übernehmen.
2. **Google Cloud Console** (console.cloud.google.com):
   - Projekt anlegen, Abrechnung aktivieren (Google verlangt eine Karte, für eigene Testfahrten reicht meist das Gratis-Kontingent – Preise vorher in der Console prüfen).
   - Diese APIs aktivieren: **Navigation SDK**, **Places API (New)**, **Elevation API** und für KI-Stimmen **Cloud Text-to-Speech API**.
   - Unter „Anmeldedaten“ einen **API-Schlüssel** erstellen.
3. `local.properties.example` nach `local.properties` kopieren und den Schlüssel bei `MAPS_API_KEY=` eintragen.
4. Am Handy **USB-Debugging** einschalten (Einstellungen → Telefoninfo → 7× auf Build-Nummer → Entwickleroptionen), Handy anstecken, in Android Studio auf ▶ drücken.
5. Beim ersten Start Standort erlauben und Googles Navigationsbedingungen akzeptieren.

### Ausprobieren ohne zu fahren
Auf dem Startbildschirm **„Testfahrt simulieren“** einschalten, Ziel wählen. Das Navigation SDK fährt die Route dann virtuell ab und die Ansagen kommen wie bei einer echten Fahrt.

### Android Auto testen
- **Im Auto:** Android-Auto-App am Handy öffnen → Einstellungen → ganz unten 10× auf „Version“ tippen → Menü (⋮) → Entwicklereinstellungen → **„Unbekannte Quellen“** aktivieren. Dann erscheint „Beifahrer“ im Auto.
- **Am PC:** Mit der *Desktop Head Unit* aus dem Android SDK (SDK Manager → SDK Tools → „Android Auto Desktop Head Unit Emulator“).

## Nur für dich und Freunde (ohne Play Store)

1. In Android Studio: **Build → Generate Signed App Bundle / APK → APK**. Einen Schlüssel anlegen und gut aufheben (Updates müssen mit demselben Schlüssel signiert sein).
2. Die APK-Datei per WhatsApp, Mail oder Google Drive an Freunde schicken. Beim Öffnen fragt Android einmal, ob die Installation aus dieser Quelle erlaubt ist.
3. Für Android Auto muss jeder einmal den Entwicklermodus der Android-Auto-App einschalten und **„Unbekannte Quellen“** aktivieren (siehe oben).
4. Alle nutzen deinen API-Schlüssel – die Kosten laufen über dein Google-Konto. Schränke den Schlüssel in der Cloud Console auf die App ein (Paketname `at.beifahrer` + SHA-1 deines Signaturschlüssels) und setze dort ein Budget-Limit.
5. Gefahrenstellen teilt ihr automatisch über die Cloud (siehe unten). Dafür muss jeder dieselbe APK (mit deiner google-services.json) nutzen.

## Gemeinsame Gefahrenstellen (Cloud)

Alle Nutzer teilen sich eine Datenbank mit Gefahrenstellen (Google **Firebase Firestore**):

- **Eintragen:** lang auf die Karte drücken → Art wählen (Bodenwelle, Schlagloch, Rollsplitt, Steilkurve, Glätte, Wildwechsel, Baustelle, Sonstiges) → Notiz → „Für alle speichern“. Während der Fahrt: roter Knopf (Handy) bzw. Warn-Knopf (Android Auto) an der aktuellen Stelle.
- **Alle bekommen sie:** Die App beobachtet live alle Stellen entlang der Route. Trägt ein Freund etwas ein, sagt deine App es kurz danach an und zeigt einen roten Marker.
- **Aktuell halten:** „Noch da“ / „Weg“ in der Liste *Gefahrenstellen (Cloud)*. Haben mindestens 2 Leute mehr „Weg“ als „Noch da“ gesagt, wird die Stelle nicht mehr angesagt. Rollsplitt, Glätte und Wildwechsel laufen nach 30 Tagen ab, Baustellen nach 60, Schlaglöcher nach 120.
- **Sensor:** Erkennt ein Handy eine Bodenwelle, wird sie erst angesagt, wenn ein zweiter Nutzer sie auch erkennt oder bestätigt.
- **Offline:** Ohne Netz wird gespeichert und später hochgeladen. Bekannte Stellen bleiben im Zwischenspeicher.
- **Anmeldung per Google:** Ansagen hören und Stellen sehen geht ohne Konto. Zum **Eintragen, Bestätigen und Löschen** meldet man sich mit dem Google-Konto an (ein Tipp, Kontoauswahl des Handys). Bei jedem Eintrag wird der **Vorname** gespeichert, damit alle sehen, von wem er ist. Die Regeln in der Cloud lassen Schreiben nur mit Google-Konto zu – das hält Spam fern.

### Firebase einrichten (ca. 10 Minuten, einmalig)
1. console.firebase.google.com → **Projekt hinzufügen** → dein bestehendes Google-Cloud-Projekt auswählen (das mit dem Maps-Schlüssel).
2. **Android-App hinzufügen**, Paketname `at.beifahrer`, dazu den SHA-1-Fingerabdruck (Android Studio: Gradle → signingReport).
3. Die Datei **google-services.json** herunterladen und in den Ordner `app/` legen.
4. **Authentication** → Anmeldemethode → **Anonym** und **Google** aktivieren. Danach `google-services.json` **neu herunterladen** (sie enthält dann die Web-Client-ID, die die Google-Anmeldung braucht).
5. **Firestore Database** erstellen (Standort `eur3` / Europa), dann unter **Regeln** den Inhalt von `firestore.rules` einfügen und veröffentlichen.
6. Optional: In der Google Cloud Console unter Firestore → **TTL** eine Richtlinie für das Feld `expiresAt` der Sammlung `hazards` anlegen. Dann löscht Firestore abgelaufene Stellen selbst.

Kosten: Firestore hat ein tägliches Gratis-Kontingent, das für dich und Freunde in der Regel reicht. Trotzdem ein Budget-Limit setzen.

## KI-Stimmen

Im Startbildschirm auf **Stimme ›** tippen. Dort gibt es drei Anbieter:

| Anbieter | Was du brauchst | Hinweis |
|---|---|---|
| **Google KI** (Chirp 3 HD, Studio, Neural2, WaveNet) | im selben Cloud-Projekt zusätzlich die **Cloud Text-to-Speech API** aktivieren | gleicher Schlüssel wie die Karte, Stimmenliste wird live geladen |
| **ElevenLabs** | eigenen Schlüssel von elevenlabs.io in der App eintragen | sehr ausdrucksstark, Gratis-Kontingent ist klein |
| **Handy** | nichts | offline, kostenlos |

Jede Stimme lässt sich mit einem Beispielsatz probehören. Die Ansagen einer Route werden beim Start einmal mit der KI-Stimme erzeugt und gespeichert. Während der Fahrt wird nur abgespielt, also ohne Verzögerung und ohne Netz. Fehlt eine Ansage (z. B. kurz nach dem Start), spricht so lange die Handy-Stimme. Sätze wie „Links 3“ werden für alle Fahrten wiederverwendet, die Kosten bleiben dadurch sehr gering.

## Stimmen

**Flüssiger sprechen:** ruhigere Sprechtempi (schnell = 1,12-fach statt 1,35), neue Ansagen werden angehängt statt die laufende abzuschneiden, Sätze werden mit Kommas statt Ausrufezeichen gebaut („Vorsicht, Links 4, in Rechts 2 zieht zu, 150.“). Am natürlichsten klingen die Google-KI-Stimmen (Max, Lena, Jonas, Sophie) – die Handy-Stimme ist nur der Notfall-Ersatz.


Die App spricht mit der Sprachausgabe des Handys (meist „Google Sprachausgabe“). Im Startbildschirm wählst du die **Stimme** (alle deutschen Stimmen des Geräts, jede wird kurz vorgespielt) und die **Sprechweise**: Rallye-Profi (schnell), Normal, Ruhig, Tief. Fehlen deutsche Stimmen: Einstellungen → System → Sprache → Sprachausgabe → Google → Sprachdaten installieren → Deutsch.

## Aufbau des Codes

```
app/src/main/java/at/beifahrer/
├── pacenote/
│   ├── PaceNoteEngine.kt   Kurven-, Kuppen- und Gefahren-Erkennung (reines Kotlin, getestet)
│   ├── Model.kt            Datenklassen + Ansagetexte (Rallye/Klartext)
│   ├── Geo.kt              Koordinaten → Meter, Position auf der Route, Polyline-Kodierung
│   ├── Glyph.kt            Form des Kurven-Symbols (Handy + Auto)
│   ├── PaceNoteAnnouncer.kt Handy-Sprachausgabe mit Musik-Absenkung
│   └── BumpDetector.kt     Bodenwellen über Beschleunigungssensor
├── ai/                     KI-Stimmen: Google Cloud TTS, ElevenLabs, Speicher + Abspielen
├── data/                   Places-Suche, Höhendaten, Cloud-Gefahrenstellen (Firebase), Zielverlauf
├── nav/                    DriveController (verbindet alles), Zustand, Abbiege-Infos
├── ui/                     Handy-Oberfläche (Jetpack Compose)
└── car/                    Android Auto (Zielsuche, Navigationsbildschirm, Ansage-Overlay)
```

Einstellbare Werte:
- Vorlauf der Ansage: `DriveController.announce()` → `v * 5.5` (≈ 5,5 Sekunden vor der Kurve, mindestens 60 m)
- Stufen-Grenzen: `PaceNoteEngine.GRADES`
- Empfindlichkeit Bodenwellen: `BumpDetector.THRESHOLD`

## Stand und Grenzen

- **Die Kurven-Engine ist getestet:** 4 Unit-Tests (`./gradlew test`) und auf drei Demo-Strecken liefert sie exakt dieselben Ansagen wie der Web-Prototyp.
- **Die Android-Teile wurden noch nicht kompiliert** (keine Android-Umgebung bei der Erstellung). Beim ersten Build kann es kleine Anpassungen bei Google-APIs geben, z. B. wenn sich ein Methodenname in einer neuen SDK-Version geändert hat. Android Studio zeigt die Stelle an.
- Google-Karten kennen keine Bodenwellen, Schlaglöcher oder echte Steilkurven (Querneigung). Diese lernt die App über Sensor und Meldungen.
- Die Höhendaten von Google sind grob (Raster von einigen zehn Metern). Große Kuppen klappen gut, kleine Wellen nicht.
- Für den **Play Store** muss eine Android-Auto-Navigations-App von Google geprüft werden. Für dich selbst reicht „Unbekannte Quellen“.
- Der Elevation-Aufruf nutzt den App-Schlüssel direkt. Für eine veröffentlichte App gehört er hinter einen kleinen eigenen Server.

## Sicherheit

Die Ansagen sollen Überraschungen vermeiden, nicht schneller machen. Auf öffentlichen Straßen gelten Tempolimits und StVO, egal was die App sagt. Die Daten können falsch sein (Baustellen, neue Straßenführung) – die Straße vor dir hat immer recht.
