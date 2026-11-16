package com.bluetoothspotifytrigger.trigger

import android.util.Log
import com.bluetoothspotifytrigger.data.TriggerRepository
import kotlinx.coroutines.flow.first

/**
 * Logique métier du déclenchement : « un appareil vient de se connecter, que
 * fait-on ? ». Indépendante de la source de l'event (vrai Bluetooth ou
 * simulation adb), ce qui permet de la tester sans matériel.
 */
class TriggerHandler(private val repository: TriggerRepository) {

    suspend fun onDeviceConnected(address: String, name: String) {
        val uri = repository.associations.first()[address]
        if (uri == null) {
            Log.i(TAG, "Aucune association pour $name [$address] : rien à faire")
            return
        }
        Log.i(TAG, "Association trouvée pour $name [$address] -> $uri")
        // Étape 6 : SpotifyController.connectAndPlay(uri)
    }

    companion object {
        const val TAG = "TriggerHandler"
    }
}
