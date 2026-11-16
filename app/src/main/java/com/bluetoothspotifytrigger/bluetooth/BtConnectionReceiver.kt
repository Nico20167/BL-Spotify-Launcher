package com.bluetoothspotifytrigger.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.bluetoothspotifytrigger.debug.DebugActions

/**
 * Reçoit les events de connexion Bluetooth (ACTION_ACL_CONNECTED) et transmet
 * l'appareil connecté à [onConnected] (adresse MAC, nom).
 *
 * Doit être enregistré DYNAMIQUEMENT (registerReceiver), pas dans le manifest :
 * depuis Android 8 (API 26), ce broadcast n'est plus délivré aux receivers
 * déclarés au manifest.
 *
 * En build debug, accepte aussi [DebugActions.SIMULATE_ACL_CONNECTED] (envoyé
 * via adb) : la simulation emprunte exactement le même chemin (débounce compris)
 * que la vraie connexion.
 *
 * Débounce : les events ACL arrivent fréquemment en double. On ignore une
 * nouvelle connexion du même appareil si la précédente date de moins de
 * [DEBOUNCE_MS].
 */
class BtConnectionReceiver(
    private val onConnected: (address: String, name: String) -> Unit,
) : BroadcastReceiver() {

    private val lastSeenByAddress = mutableMapOf<String, Long>()

    override fun onReceive(context: Context, intent: Intent) {
        val (address, name) = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = intent.bluetoothDevice() ?: return
                val address = device.address ?: return
                address to device.safeName()
            }
            DebugActions.SIMULATE_ACL_CONNECTED -> {
                val address = intent.getStringExtra(DebugActions.EXTRA_ADDRESS) ?: run {
                    Log.w(TAG, "Simulation ignorée : extra '${DebugActions.EXTRA_ADDRESS}' manquant")
                    return
                }
                Log.i(TAG, "[SIMULATION] ACL_CONNECTED injecté via adb")
                address to (intent.getStringExtra(DebugActions.EXTRA_NAME) ?: "(simulé)")
            }
            else -> return
        }

        val now = SystemClock.elapsedRealtime()
        val previous = lastSeenByAddress[address]
        if (previous != null && now - previous < DEBOUNCE_MS) {
            Log.d(TAG, "Connexion ignorée (debounce) : $address")
            return
        }
        lastSeenByAddress[address] = now

        Log.i(TAG, "Appareil Bluetooth connecté : $name [$address]")
        onConnected(address, name)
    }

    // BLUETOOTH_CONNECT est garantie par le service ; lecture du nom protégée quand même.
    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String =
        try {
            name
        } catch (e: SecurityException) {
            null
        } ?: "(sans nom)"

    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        const val TAG = "BtConnectionReceiver"
        private const val DEBOUNCE_MS = 3_000L
    }
}
