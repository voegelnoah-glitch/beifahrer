# Beifahrer aufs Handy – mit automatischen Updates

So funktioniert es: Der Code liegt auf **GitHub**. Bei jeder Änderung baut GitHub die App von selbst
(ca. 5 Minuten) und stellt sie als Download bereit. Die App auf dem Handy merkt das beim Start und zeigt
**„Neue Version – Aktualisieren“**. Ein Tipp, Android fragt einmal „Installieren?“, fertig.

Einmal einrichten dauert ca. 30–45 Minuten. Danach musst du nie wieder etwas bauen.

---

## 1. Vorbereitung (einmalig)

Du brauchst aus der README schon:
- den **Google-API-Schlüssel** (Maps/Navigation/Places/Elevation/Text-to-Speech)
- die Datei **google-services.json** aus Firebase

## 2. Signatur-Schlüssel erstellen (einmalig)

Android installiert Updates nur, wenn jede Version mit demselben Schlüssel unterschrieben ist.

1. Projekt in Android Studio öffnen → **Build → Generate Signed App Bundle / APK → APK → Weiter**
2. **Create new…** → Datei speichern als `release.keystore` (NICHT in den Projektordner), Passwort wählen,
   Alias z. B. `beifahrer`, Name eintragen → OK. Danach kannst du den Dialog abbrechen.
3. Schlüssel und Passwörter gut aufheben. Geht der Schlüssel verloren, muss die App einmal neu installiert werden.

## 3. GitHub einrichten (einmalig)

1. Auf **github.com** ein Konto anlegen (gratis).
2. **New repository** → Name `beifahrer` → **Public** (damit die App Updates ohne Passwort findet) → Create.
3. **uploading an existing file** → den Inhalt des Ordners `Beifahrer` hineinziehen → **Commit changes**.
   (Die Dateien `google-services.json` und `release.keystore` NICHT hochladen – die kommen als Geheimnis rein.)
4. Im Repo: **Settings → Secrets and variables → Actions → New repository secret**, diese sechs anlegen:

| Name | Inhalt |
|---|---|
| `MAPS_API_KEY` | dein Google-API-Schlüssel |
| `GOOGLE_SERVICES_JSON` | Inhalt von google-services.json **als Base64** (siehe unten) |
| `KEYSTORE_BASE64` | release.keystore **als Base64** |
| `KEYSTORE_PASSWORD` | Passwort des Keystores |
| `KEY_ALIAS` | z. B. `beifahrer` |
| `KEY_PASSWORD` | Passwort des Schlüssels |

**Base64 erzeugen**
- Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Pfad\release.keystore")) | Set-Clipboard`
- Mac: `base64 -i release.keystore | pbcopy`

Danach steht der Text in der Zwischenablage → in das Secret einfügen. Genauso für google-services.json.

5. **Actions** → „APK bauen und veröffentlichen“ → **Run workflow**. Nach ca. 5 Minuten ist es grün.
   Siehst du unter Actions nichts: Der Browser lädt versteckte Ordner manchmal nicht hoch. Dann im Repo
   **Add file → Create new file**, als Name `.github/workflows/android.yml` eintippen und den Inhalt dieser Datei einfügen.

## 4. App aufs Handy (einmalig)

1. Am Handy öffnen: `https://github.com/DEIN-NAME/beifahrer/releases/latest`
2. **Beifahrer.apk** antippen → herunterladen → öffnen.
3. Android fragt: „Installation aus dieser Quelle erlauben?“ → erlauben → **Installieren**.
4. Für Android Auto: Android-Auto-App → Einstellungen → 10× auf „Version“ → ⋮ → Entwicklereinstellungen → **Unbekannte Quellen** an.

**Wichtig:** Den SHA-1 deines Release-Schlüssels in der Google Cloud Console beim API-Schlüssel und in Firebase
eintragen (Android Studio → Gradle → signingReport, Variante „release“), sonst bleibt die Karte leer.

## 5. Updates

- Jede Änderung auf GitHub (von dir oder mir) baut automatisch eine neue Version.
- Die App prüft beim Start. Ist etwas Neues da: Banner **„Neue Version 1.x – Aktualisieren“** → Tipp → Android fragt kurz → fertig.
- Manuell: ⚙ Einstellungen → App → **Nach Updates suchen**.
- Deine Freunde bekommen die Updates genauso.

**Damit ich Änderungen direkt einspielen kann:** In claude.ai unter Einstellungen → Connectors **GitHub verbinden**
und dem Repo `beifahrer` Zugriff geben. Dann lade ich jede Änderung hoch, und dein Handy bietet sie automatisch an.

### Ganz ohne Tippen?
Komplett stille Updates gehen auf Android nur über den **Play Store**. Dafür braucht es ein Google-Play-Entwicklerkonto
(einmalig 25 US-Dollar) und einen „internen Test“ für dich und deine Freunde. Kann ich bei Bedarf vorbereiten.
