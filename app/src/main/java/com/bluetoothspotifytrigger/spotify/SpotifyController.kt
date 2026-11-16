package com.bluetoothspotifytrigger.spotify

import android.content.Context
import android.util.Log
import com.bluetoothspotifytrigger.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.UnsupportedFeatureVersionException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wrapper du Spotify App Remote SDK : se connecte à l'app Spotify installée,
 * lance un URI, puis se déconnecte.
 *
 * Pièges gérés :
 * - `SpotifyAppRemote.connect` est asynchrone (callback) → converti en suspend.
 * - Toujours `disconnect` après usage, sinon la connexion au service Spotify fuit.
 * - L'écran d'autorisation (« autoriser cette app à contrôler Spotify ») est une
 *   Activity : Android interdit de l'ouvrir depuis l'arrière-plan. Il ne doit
 *   donc être demandé qu'au premier plan ([allowAuthUi] = true, depuis l'UI).
 *   Depuis le service, on passe false : si l'utilisateur n'a jamais autorisé,
 *   on obtient [PlayResult.NotAuthorized] au lieu d'un écran invisible.
 */
class SpotifyController(context: Context) {

    private val appContext = context.applicationContext

    sealed interface PlayResult {
        data object Success : PlayResult
        data object MissingClientId : PlayResult
        data object SpotifyNotInstalled : PlayResult
        data object NotLoggedIn : PlayResult
        data object NotAuthorized : PlayResult
        data object Offline : PlayResult
        data object SpotifyTooOld : PlayResult
        data object Timeout : PlayResult
        /** Mode sans SDK : intent envoyé mais aucun son détecté ensuite. */
        data object NotConfirmed : PlayResult
        data class Error(val message: String) : PlayResult
    }

    suspend fun connectAndPlay(uri: String, allowAuthUi: Boolean): PlayResult {
        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) return PlayResult.MissingClientId
        if (!SpotifyAppRemote.isSpotifyInstalled(appContext)) return PlayResult.SpotifyNotInstalled

        // Le SDK attend d'être appelé depuis le thread principal (callbacks via Looper).
        return withContext(Dispatchers.Main) {
            var remote: SpotifyAppRemote? = null
            try {
                // Délai large si l'écran d'autorisation s'affiche (l'utilisateur doit cliquer).
                val connectTimeout = if (allowAuthUi) 60_000L else CONNECT_TIMEOUT_MS
                remote = withTimeout(connectTimeout) { connect(allowAuthUi) }
                Log.i(TAG, "Connecté à Spotify, lecture de $uri")
                withTimeout(PLAY_TIMEOUT_MS) { play(remote, uri) }
                Log.i(TAG, "Lecture lancée : $uri")
                PlayResult.Success
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Délai dépassé (connexion ou lecture)")
                PlayResult.Timeout
            } catch (e: Throwable) {
                mapError(e)
            } finally {
                remote?.let { SpotifyAppRemote.disconnect(it) }
            }
        }
    }

    private suspend fun connect(allowAuthUi: Boolean): SpotifyAppRemote =
        suspendCancellableCoroutine { cont ->
            val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
                .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
                .showAuthView(allowAuthUi)
                .build()
            SpotifyAppRemote.connect(appContext, params, object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    if (cont.isActive) {
                        cont.resume(appRemote)
                    } else {
                        // Arrivé après timeout/annulation : ne pas fuiter la connexion.
                        SpotifyAppRemote.disconnect(appRemote)
                    }
                }

                override fun onFailure(error: Throwable) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            })
        }

    private suspend fun play(remote: SpotifyAppRemote, uri: String) =
        suspendCancellableCoroutine { cont ->
            remote.playerApi.play(uri)
                .setResultCallback { if (cont.isActive) cont.resume(Unit) }
                .setErrorCallback { if (cont.isActive) cont.resumeWithException(it) }
        }

    private fun mapError(e: Throwable): PlayResult {
        Log.w(TAG, "Échec Spotify : ${e::class.simpleName}: ${e.message}")
        return when (e) {
            is CouldNotFindSpotifyApp -> PlayResult.SpotifyNotInstalled
            is NotLoggedInException -> PlayResult.NotLoggedIn
            is UserNotAuthorizedException -> PlayResult.NotAuthorized
            is AuthenticationFailedException -> PlayResult.NotAuthorized
            is OfflineModeException -> PlayResult.Offline
            is UnsupportedFeatureVersionException -> PlayResult.SpotifyTooOld
            else -> PlayResult.Error(e.message ?: e::class.simpleName ?: "Erreur inconnue")
        }
    }

    companion object {
        const val TAG = "SpotifyController"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val PLAY_TIMEOUT_MS = 10_000L

        /** Message lisible pour l'utilisateur. */
        fun describe(result: PlayResult): String = when (result) {
            PlayResult.Success -> "Lecture lancée ✅"
            PlayResult.MissingClientId -> "Client ID Spotify non configuré (local.properties)."
            PlayResult.SpotifyNotInstalled -> "L'app Spotify n'est pas installée."
            PlayResult.NotLoggedIn -> "Connecte-toi d'abord dans l'app Spotify."
            PlayResult.NotAuthorized ->
                "Autorisation Spotify requise : ouvre l'app et appuie sur « Tester » une première fois."
            PlayResult.Offline -> "Spotify est en mode hors connexion."
            PlayResult.SpotifyTooOld -> "Mets à jour l'app Spotify."
            PlayResult.Timeout -> "Spotify ne répond pas (délai dépassé)."
            PlayResult.NotConfirmed ->
                "Lecture non confirmée (aucun son détecté). En arrière-plan, vérifie " +
                    "l'autorisation « Afficher par-dessus les autres apps »."
            is PlayResult.Error -> "Erreur Spotify : ${result.message}"
        }
    }
}
