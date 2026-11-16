package com.bluetoothspotifytrigger.spotify

import android.content.Context
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.spotify.SpotifyController.PlayResult

/** Moyen utilisé pour lancer la lecture. */
enum class PlaybackMode(val label: String, val description: String) {
    SDK(
        "SDK Spotify (App Remote)",
        "Fiable et silencieux. Nécessite un Client ID ; 5 utilisateurs max (mode Développement).",
    ),
    INTENT(
        "Sans SDK (prototype)",
        "Aucun compte développeur. Ouvre Spotify ; nécessite « Afficher par-dessus les autres apps ».",
    ),
}

/**
 * Point d'entrée unique de la lecture : choisit le moteur selon le réglage
 * de l'utilisateur (relu à chaque appel, donc changeable à chaud).
 */
class SpotifyPlayer(context: Context) {

    private val prefs = AppPrefs(context)
    private val sdk = SpotifyController(context)
    private val intent = SpotifyIntentPlayer(context)

    /** [foreground] : appel depuis l'UI (autorise l'écran d'autorisation du SDK). */
    suspend fun play(uri: String, foreground: Boolean): PlayResult =
        when (prefs.playbackMode) {
            PlaybackMode.SDK -> sdk.connectAndPlay(uri, allowAuthUi = foreground)
            PlaybackMode.INTENT -> intent.play(uri)
        }
}
