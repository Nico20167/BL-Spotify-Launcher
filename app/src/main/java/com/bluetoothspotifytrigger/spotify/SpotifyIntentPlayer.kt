package com.bluetoothspotifytrigger.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.bluetoothspotifytrigger.spotify.SpotifyController.PlayResult
import kotlinx.coroutines.delay

/**
 * PROTOTYPE — lecture SANS le SDK Spotify (pas de Client ID, pas de limite de
 * 5 utilisateurs du mode Développement).
 *
 * Principe : on envoie à l'app Spotify un intent VIEW sur l'URI suffixé de
 * `:play` (ex. `spotify:playlist:<id>:play`). Comportement non documenté par
 * Spotify, observé par la communauté (Tasker, MacroDroid, Home Assistant) :
 * Spotify ouvre le contenu ET lance la lecture.
 *
 * Limites connues :
 * - C'est une Activity : depuis Android 10, une app en arrière-plan ne peut pas
 *   en ouvrir une, sauf exemption. Exemption utilisée ici : l'autorisation
 *   « Afficher par-dessus les autres apps » (SYSTEM_ALERT_WINDOW).
 * - Aucun retour de Spotify : on vérifie qu'un son démarre
 *   (AudioManager.isMusicActive) pour confirmer la lecture.
 */
class SpotifyIntentPlayer(context: Context) {

    private val appContext = context.applicationContext

    fun isSpotifyInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SPOTIFY_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun play(uri: String): PlayResult {
        if (!isSpotifyInstalled()) return PlayResult.SpotifyNotInstalled

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$uri:play"))
            .setPackage(SPOTIFY_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Log.i(TAG, "Intent VIEW ${intent.data} (overlay autorisé : ${canStartFromBackground(appContext)})")
        try {
            appContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return PlayResult.SpotifyNotInstalled
        }

        // Pas d'exception si Android bloque le lancement en arrière-plan : il est
        // ignoré silencieusement. On attend donc qu'un son démarre.
        val audio = appContext.getSystemService(AudioManager::class.java)
        if (waitForMusic(audio, FIRST_WAIT_MS)) {
            Log.i(TAG, "Lecture confirmée (audio actif)")
            return PlayResult.Success
        }

        // Observé sur Pixel / Spotify 9.2 : si Spotify avait déjà ce contenu en
        // mémoire (dernier écouté), il l'ouvre mais ignore « :play » et reste en
        // pause. Le contenu est pourtant chargé : la touche média « Lecture »
        // (envoyée à la session média active, donc Spotify) le démarre.
        //
        // Écran éteint, Spotify démarre plus lentement : une touche envoyée trop
        // tôt arrive avant que sa session soit prête et est perdue. On la renvoie
        // donc périodiquement. Sans risque : MEDIA_PLAY n'est pas une bascule
        // (contrairement à PLAY_PAUSE), la renvoyer pendant la lecture ne fait rien.
        // Chaque touche donne aussi à Spotify une autorisation temporaire de
        // démarrer son service de lecture depuis l'arrière-plan (Android 12+).
        repeat(MEDIA_KEY_ATTEMPTS) { attempt ->
            Log.i(TAG, "Pas de son : touche média Lecture (essai ${attempt + 1}/$MEDIA_KEY_ATTEMPTS)")
            sendMediaPlay(audio)
            if (waitForMusic(audio, MEDIA_KEY_INTERVAL_MS)) {
                Log.i(TAG, "Lecture confirmée après touche média")
                return PlayResult.Success
            }
        }
        Log.w(TAG, "Lecture non confirmée")
        return PlayResult.NotConfirmed
    }

    private suspend fun waitForMusic(audio: AudioManager, timeoutMs: Long): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            delay(POLL_MS)
            waited += POLL_MS
            if (audio.isMusicActive) return true
        }
        return false
    }

    private fun sendMediaPlay(audio: AudioManager) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    companion object {
        const val TAG = "SpotifyIntentPlayer"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val POLL_MS = 250L
        private const val FIRST_WAIT_MS = 4_000L
        private const val MEDIA_KEY_ATTEMPTS = 6
        private const val MEDIA_KEY_INTERVAL_MS = 3_000L

        /**
         * L'app peut-elle ouvrir Spotify depuis l'arrière-plan ? Oui si
         * l'utilisateur a accordé « Afficher par-dessus les autres apps ».
         */
        fun canStartFromBackground(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
    }
}
