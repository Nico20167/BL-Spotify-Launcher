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
import kotlinx.coroutines.delay

/**
 * Lecture Spotify SANS SDK (pas de Client ID, pas de compte développeur, pas de
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

    private val session = SpotifySessionControl(appContext)

    /**
     * Lance [uri] dans Spotify.
     *
     * [volumePercent] (null = inchangé) : réglé avant le lancement, puis
     * réappliqué une fois la lecture confirmée (à la connexion, Android peut
     * restaurer le volume mémorisé de l'appareil juste après notre réglage).
     *
     * [shuffle] (null = inchangé) : nécessite l'accès aux sessions média
     * ([SpotifySessionControl]). Si Spotify tourne déjà, on règle le mode
     * AVANT le lancement pour démarrer directement sur un titre au hasard.
     * Sinon (démarrage à froid, pas encore de session), on le règle une fois la
     * lecture partie.
     *
     * En aléatoire, on passe au titre suivant (donc au hasard) si la lecture
     * n'a pas démarré « proprement » sur un titre au hasard :
     * - démarrage à froid : l'aléatoire n'a été activé qu'après le 1er titre ;
     * - reprise par la touche média : Spotify a ignoré `:play` (playlist déjà
     *   chargée, en pause) et a simplement repris le titre en pause — observé
     *   sur téléphone : « la même musique à chaque fois ».
     * Sans aléatoire, une reprise est conservée (on continue là où on était).
     */
    suspend fun play(uri: String, volumePercent: Int? = null, shuffle: Boolean? = null): PlayResult {
        val shuffleSetBefore = shuffle?.let { session.setShuffle(it) } ?: false
        val launch = launch(uri, volumePercent)
        if (launch.result != PlayResult.Success) return launch.result

        volumePercent?.let { applyVolume(it) }
        if (shuffle == null) return launch.result

        if (!shuffleSetBefore || launch.resumedByMediaKey) {
            delay(SESSION_SETTLE_MS) // laisse la session Spotify s'initialiser / se stabiliser
        }
        if (!shuffleSetBefore && !session.setShuffle(shuffle)) {
            if (!session.hasAccess()) {
                Log.w(TAG, "Aléatoire demandé mais « Accès aux notifications » non accordé")
            }
            return launch.result
        }
        if (shuffle && (!shuffleSetBefore || launch.resumedByMediaKey)) {
            session.skipToNext()
        }
        return launch.result
    }

    /** [resumedByMediaKey] : la lecture a démarré grâce à la touche média (reprise). */
    private data class Launch(val result: PlayResult, val resumedByMediaKey: Boolean = false)

    private fun applyVolume(percent: Int) {
        val audio = appContext.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = Math.round(percent.coerceIn(0, 100) * max / 100f)
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            Log.i(TAG, "Volume réglé à $percent % ($index/$max)")
        } catch (e: SecurityException) {
            // Ex. mode Ne pas déranger restrictif : on joue quand même.
            Log.w(TAG, "Impossible de régler le volume", e)
        }
    }

    private suspend fun launch(uri: String, volumePercent: Int?): Launch {
        if (!isSpotifyInstalled()) return Launch(PlayResult.SpotifyNotInstalled)
        volumePercent?.let { applyVolume(it) }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$uri:play"))
            .setPackage(SPOTIFY_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Log.i(TAG, "Intent VIEW ${intent.data} (overlay autorisé : ${canStartFromBackground(appContext)})")
        try {
            appContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return Launch(PlayResult.SpotifyNotInstalled)
        }

        // Pas d'exception si Android bloque le lancement en arrière-plan : il est
        // ignoré silencieusement. On attend donc qu'un son démarre.
        val audio = appContext.getSystemService(AudioManager::class.java)
        if (waitForMusic(audio, FIRST_WAIT_MS)) {
            Log.i(TAG, "Lecture confirmée (audio actif)")
            return Launch(PlayResult.Success)
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
                Log.i(TAG, "Lecture confirmée après touche média (reprise du titre en pause)")
                return Launch(PlayResult.Success, resumedByMediaKey = true)
            }
        }
        Log.w(TAG, "Lecture non confirmée")
        return Launch(PlayResult.NotConfirmed)
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
        private const val SESSION_SETTLE_MS = 500L
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
