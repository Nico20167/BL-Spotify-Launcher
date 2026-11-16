package com.bluetoothspotifytrigger.trigger

import android.util.Log
import com.bluetoothspotifytrigger.data.DeviceConfig
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.spotify.PlayResult
import com.bluetoothspotifytrigger.spotify.SpotifyLink
import kotlinx.coroutines.flow.first

/**
 * Logique métier du déclenchement : « un appareil vient de se connecter, que
 * fait-on ? ». Indépendante de la source de l'event (vrai Bluetooth ou
 * simulation adb), ce qui permet de la tester sans matériel.
 *
 * [play] lance la lecture d'un URI (en prod : SpotifyIntentPlayer, ouverture
 * de Spotify depuis l'arrière-plan).
 */
class TriggerHandler(
    private val repository: TriggerRepository,
    /** Lance la lecture de l'URI (normalisé) avec les réglages de l'appareil. */
    private val play: suspend (uri: String, config: DeviceConfig) -> PlayResult,
) {

    sealed interface Outcome {
        data object NoAssociation : Outcome
        data class Played(val uri: String, val result: PlayResult) : Outcome
    }

    suspend fun onDeviceConnected(address: String, name: String): Outcome {
        val config = repository.configs.first()[address]
        if (config == null) {
            Log.i(TAG, "Aucune association pour $name [$address] : rien à faire")
            return Outcome.NoAssociation
        }
        val stored = config.uri
        // Re-normalise : des données enregistrées par une ancienne version (validation
        // plus laxiste) peuvent contenir des paramètres de partage (?si=…).
        val uri = (SpotifyLink.parse(stored) as? SpotifyLink.Result.Valid)?.uri ?: run {
            Log.w(TAG, "URI enregistré invalide pour $name : $stored")
            return Outcome.Played(stored, PlayResult.Error("URI invalide : $stored (à corriger dans l'app)"))
        }
        Log.i(
            TAG,
            "Association trouvée pour $name [$address] -> $uri " +
                "(volume : ${config.volumePercent ?: "inchangé"}, aléatoire : ${config.shuffle ?: "inchangé"})",
        )
        val result = play(uri, config)
        Log.i(TAG, "Résultat pour $name : $result")
        return Outcome.Played(uri, result)
    }

    companion object {
        const val TAG = "TriggerHandler"
    }
}
