package at.beifahrer.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** Wer gerade angemeldet ist. [google] = mit Google-Konto (darf eintragen), sonst nur lesen. */
data class AccountInfo(val uid: String, val google: Boolean, val firstName: String?, val email: String?)

/**
 * Anmeldung per Google-Konto (über Firebase).
 *  - Ohne Anmeldung: anonymer Zugang, alle Gefahrenstellen lesen und angesagt bekommen.
 *  - Mit Google: Stellen eintragen, bestätigen, eigene löschen. Andere sehen deinen Vornamen beim Eintrag.
 * Wer vorher anonym war, behält beim Anmelden seine Einträge (das Konto wird verknüpft).
 */
class Account {
    /** false, solange Firebase nicht eingerichtet ist (keine google-services.json) – dann ohne Cloud. */
    val enabled: Boolean = runCatching { FirebaseApp.getInstance() }.isSuccess
    private val authOrNull: FirebaseAuth? = if (enabled) FirebaseAuth.getInstance() else null
    private val auth: FirebaseAuth get() = authOrNull ?: error("Cloud ist noch nicht eingerichtet")
    private val _info = MutableStateFlow<AccountInfo?>(null)
    val info: StateFlow<AccountInfo?> = _info

    init { authOrNull?.addAuthStateListener { refresh() } }

    private fun refresh() {
        val u = authOrNull?.currentUser
        _info.value = u?.let {
            AccountInfo(
                uid = it.uid,
                google = it.providerData.any { p -> p.providerId == GoogleAuthProvider.PROVIDER_ID },
                firstName = it.displayName?.substringBefore(' ')?.take(30),
                email = it.email,
            )
        }
    }

    /** Lesezugang ohne Konto. */
    suspend fun ensureSignedIn() { val a = authOrNull ?: error("Cloud ist noch nicht eingerichtet"); if (a.currentUser == null) a.signInAnonymously().await() }

    /** Öffnet die Google-Kontoauswahl des Handys. */
    suspend fun signInWithGoogle(activity: Activity) {
        val option = GetGoogleIdOption.Builder()
            // Wird vom google-services-Plugin aus google-services.json erzeugt (Web-Client-ID).
            .setServerClientId(activity.getString(activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName).also { check(it != 0) { "Cloud ist noch nicht eingerichtet" } }))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val cred = CredentialManager.create(activity).getCredential(activity, request).credential
        check(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Unerwartete Anmeldeart" }
        val firebaseCred = GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(cred.data).idToken, null)

        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            try { current.linkWithCredential(firebaseCred).await() }                  // Einträge bleiben erhalten
            catch (e: FirebaseAuthUserCollisionException) { auth.signInWithCredential(firebaseCred).await() } // Konto gab es schon
        } else auth.signInWithCredential(firebaseCred).await()
        auth.currentUser?.getIdToken(true)?.await() // frisches Token, damit die Regeln "Google" erkennen
        refresh()
    }

    suspend fun signOut(context: Context) {
        authOrNull?.signOut() ?: return
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        ensureSignedIn()
    }
}
