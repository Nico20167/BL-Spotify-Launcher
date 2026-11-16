package com.bluetoothspotifytrigger.data.local

import android.content.Context
import com.bluetoothspotifytrigger.spotify.PlaybackMode

/**
 * Petits réglages techniques lus de façon SYNCHRONE (SharedPreferences),
 * là où une lecture asynchrone DataStore serait inutilement lourde :
 * - dans un BroadcastReceiver de boot (doit décider vite),
 * - pendant la composition de l'écran Permissions.
 *
 * Les données « métier » (associations MAC -> URI) restent dans DataStore.
 */
class AppPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    /**
     * L'utilisateur veut-il que le service tourne ? Mis à true par « Démarrer »,
     * false par « Arrêter ». Sert à relancer le service après un reboot ou une
     * mise à jour de l'app, mais seulement s'il était actif.
     */
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    /** Moteur de lecture choisi (SDK Spotify ou prototype sans SDK). */
    var playbackMode: PlaybackMode
        get() = prefs.getString(KEY_PLAYBACK_MODE, null)
            ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
            ?: PlaybackMode.SDK
        set(value) = prefs.edit().putString(KEY_PLAYBACK_MODE, value.name).apply()

    /**
     * Permissions déjà demandées au moins une fois. Android ne fournit pas cette
     * info : sans elle, impossible de distinguer « jamais demandée » de
     * « refusée définitivement » (dans les deux cas
     * shouldShowRequestPermissionRationale renvoie false).
     */
    fun wasRequested(permission: String): Boolean =
        prefs.getStringSet(KEY_REQUESTED, emptySet())!!.contains(permission)

    fun markRequested(permissions: Collection<String>) {
        val current = prefs.getStringSet(KEY_REQUESTED, emptySet())!!
        prefs.edit().putStringSet(KEY_REQUESTED, current + permissions).apply()
    }

    private companion object {
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_REQUESTED = "requested_permissions"
        const val KEY_PLAYBACK_MODE = "playback_mode"
    }
}
