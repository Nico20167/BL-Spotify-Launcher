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
        repeat(CONFIRM_POLLS) {
            delay(CONFIRM_STEP_MS)
            if (audio.isMusicActive) {
                Log.i(TAG, "Lecture confirmée (audio actif)")
                return PlayResult.Success
            }
        }
        Log.w(TAG, "Lecture non confirmée après ${CONFIRM_POLLS * CONFIRM_STEP_MS} ms")
        return PlayResult.NotConfirmed
    }

    companion object {
        const val TAG = "SpotifyIntentPlayer"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val CONFIRM_POLLS = 20
        private const val CONFIRM_STEP_MS = 500L

        /**
         * L'app peut-elle ouvrir Spotify depuis l'arrière-plan ? Oui si
         * l'utilisateur a accordé « Afficher par-dessus les autres apps ».
         */
        fun canStartFromBackground(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
    }
}
