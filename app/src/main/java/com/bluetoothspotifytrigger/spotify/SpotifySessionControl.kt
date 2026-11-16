package com.bluetoothspotifytrigger.spotify

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Pilotage de la session média de Spotify (API standard Android, celle des
 * montres et autoradios) : sert au mode aléatoire, que l'intent `:play` ne
 * permet pas de choisir.
 *
 * Prérequis : autorisation « Accès aux notifications » pour [MediaAccessListener].
 * Sans elle, toutes les méthodes renvoient false/null sans erreur : la lecture
 * se fait simplement sans toucher au mode aléatoire.
 *
 * La session n'existe que si Spotify tourne : avant un démarrage à froid, il
 * n'y a rien à piloter.
 */
class SpotifySessionControl(context: Context) {

    private val appContext = context.applicationContext

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)

    private fun spotifyController(): MediaControllerCompat? {
        if (!hasAccess()) return null
        val manager = appContext.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessions = try {
            manager.getActiveSessions(ComponentName(appContext, MediaAccessListener::class.java))
        } catch (e: SecurityException) {
            Log.w(TAG, "Accès aux sessions média refusé", e)
            return null
        }
        val spotify = sessions.firstOrNull { it.packageName == SpotifyIntentPlayer.SPOTIFY_PACKAGE }
            ?: return null
        return MediaControllerCompat(
            appContext,
            MediaSessionCompat.Token.fromToken(spotify.sessionToken),
        )
    }

    /** Règle le mode aléatoire. false si Spotify n'est pas pilotable (pas d'accès / pas lancé). */
    fun setShuffle(enabled: Boolean): Boolean {
        val controller = spotifyController() ?: return false
        controller.transportControls.setShuffleMode(
            if (enabled) {
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            } else {
                PlaybackStateCompat.SHUFFLE_MODE_NONE
            },
        )
        Log.i(TAG, "Mode aléatoire Spotify : ${if (enabled) "activé" else "désactivé"}")
        return true
    }

    fun skipToNext(): Boolean {
        val controller = spotifyController() ?: return false
        controller.transportControls.skipToNext()
        Log.i(TAG, "Titre suivant (départ aléatoire)")
        return true
    }

    companion object {
        const val TAG = "SpotifySessionControl"
    }
}
