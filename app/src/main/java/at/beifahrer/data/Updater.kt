package at.beifahrer.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import at.beifahrer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Ein verfügbares Update aus GitHub-Releases. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Selbst-Update ohne Play Store:
 *  1. GitHub Actions baut bei jeder Änderung eine neue APK und veröffentlicht sie als Release "v<Nummer>".
 *  2. Die App fragt beim Start das neueste Release ab. Ist die Nummer größer als die eigene, gibt es ein Update.
 *  3. Ein Tipp lädt die APK herunter und öffnet den Android-Installer (Android verlangt diese eine Bestätigung).
 *
 * Das Repo muss öffentlich sein, damit die App ohne Passwort darauf zugreifen kann.
 */
class Updater(private val context: Context) {

    /** "besitzer/repo", wird beim Bauen aus GitHub übernommen. Leer = Updates aus. */
    private val repo = BuildConfig.UPDATE_REPO

    val enabled get() = repo.isNotBlank()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val con = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 8000; readTimeout = 8000
        }
        if (con.responseCode != 200) return@withContext null
        val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
        val code = json.optString("tag_name").removePrefix("v").toIntOrNull() ?: return@withContext null
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
            ?: return@withContext null
        UpdateInfo(code, json.optString("name").ifBlank { "1.$code" }, apk.getString("browser_download_url"), json.optString("body").take(500))
    }

    /** Lädt die APK in den App-Cache. [progress] bekommt 0..100. */
    suspend fun download(u: UpdateInfo, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "Beifahrer-${u.versionCode}.apk")
        val con = (URL(u.apkUrl).openConnection() as HttpURLConnection).apply { instanceFollowRedirects = true; connectTimeout = 10_000; readTimeout = 30_000 }
        val total = con.contentLengthLong.coerceAtLeast(1)
        con.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val p = (done * 100 / total).toInt().coerceIn(0, 100)
                    if (p != last) { last = p; progress(p) }
                }
            }
        }
        file
    }

    /** Darf die App APKs installieren? (Einmalige Erlaubnis in den Android-Einstellungen) */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Installiert die APK über den Android-PackageInstaller. Ab Android 12 darf sich die App selbst
     * ohne Nachfrage aktualisieren, sobald sie einmal ihr eigenes Update installiert hat.
     * Verlangt Android trotzdem eine Bestätigung, öffnet [UpdateReceiver] den Dialog.
     */
    fun installSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("beifahrer.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java).setPackage(context.packageName), flags)
            session.commit(pending.intentSender)
        }
    }

    /** Öffnet den Android-Installer für die heruntergeladene APK (Reserve, falls die Sitzung nicht geht). */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Rückmeldung vom Android-Installer: bei Bedarf den Bestätigungsdialog zeigen. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("Beifahrer", "Update installiert")
            else -> Log.w("Beifahrer", "Update fehlgeschlagen: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
