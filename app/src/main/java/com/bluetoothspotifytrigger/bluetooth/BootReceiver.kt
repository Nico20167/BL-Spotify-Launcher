package com.bluetoothspotifytrigger.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.permissions.AppPermissions

/**
 * Relance le service de surveillance :
 * - après un redémarrage du téléphone (BOOT_COMPLETED),
 * - après une mise à jour de l'app (MY_PACKAGE_REPLACED) : l'installation
 *   d'une nouvelle version tue le process, donc le service.
 *
 * Uniquement si l'utilisateur l'avait démarré (AppPrefs.serviceEnabled) et si
 * les permissions sont toujours accordées.
 *
 * Ces deux broadcasts font partie des exceptions qui autorisent le démarrage
 * d'un foreground service depuis l'arrière-plan (Android 12+). Ils sont
 * « protégés » : seul le système peut les émettre, d'où exported="true" sans
 * risque.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (!AppPrefs(context).serviceEnabled) {
            Log.i(TAG, "${intent.action} : service désactivé par l'utilisateur, rien à faire")
            return
        }
        if (!AppPermissions.allGranted(context)) {
            Log.w(TAG, "${intent.action} : permissions manquantes, service non relancé")
            return
        }
        Log.i(TAG, "${intent.action} : relance du service")
        BluetoothMonitorService.start(context)
    }

    private companion object {
        // Pas "BootReceiver" : ce tag est déjà utilisé par un composant système.
        const val TAG = "BstBootReceiver"
    }
}
