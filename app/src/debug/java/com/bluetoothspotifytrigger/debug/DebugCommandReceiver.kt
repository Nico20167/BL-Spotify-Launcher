package com.bluetoothspotifytrigger.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.data.local.triggerDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver présent UNIQUEMENT dans le build debug (source set `src/debug`).
 * Déclaré au manifest debug (fonctionne même app fermée), protégé par la
 * permission DUMP : seul adb peut l'appeler.
 *
 *   adb shell am broadcast -p com.bluetoothspotifytrigger \
 *       -a com.bluetoothspotifytrigger.debug.SET_FAKE_DEVICES --ez enabled true
 */
class DebugCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DebugActions.SET_FAKE_DEVICES -> {
                val enabled = intent.getBooleanExtra(DebugActions.EXTRA_ENABLED, true)
                // Écriture DataStore asynchrone : goAsync() garde le receiver vivant
                // jusqu'à finish() (sinon le process peut être tué en cours d'écriture).
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        TriggerRepository(context.applicationContext.triggerDataStore)
                            .setFakeDevicesEnabled(enabled)
                        Log.i(TAG, "Appareils simulés : ${if (enabled) "activés" else "désactivés"}")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DebugCommand"
    }
}
