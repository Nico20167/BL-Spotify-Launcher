package com.bluetoothspotifytrigger.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Reçoit les events de connexion Bluetooth (ACTION_ACL_CONNECTED) et, pour
 * l'instant (étape 5), se contente de logger l'appareil connecté.
 *
 * Doit être enregistré DYNAMIQUEMENT (registerReceiver), pas dans le manifest :
 * depuis Android 8 (API 26), ce broadcast n'est plus délivré aux receivers
 * déclarés au manifest.
 *
 * Débounce : les events ACL arrivent fréquemment en double. On ignore une
 * nouvelle connexion du même appareil si la précédente date de moins de
 * [DEBOUNCE_MS].
 */
class BtConnectionReceiver : BroadcastReceiver() {

    private val lastSeenByAddress = mutableMapOf<String, Long>()

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT garantie par le service ; lecture du nom enrobée en try/catch.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val device = intent.bluetoothDevice() ?: return
        val address = device.address ?: return

        val now = SystemClock.elapsedRealtime()
        val previous = lastSeenByAddress[address]
        if (previous != null && now - previous < DEBOUNCE_MS) {
            Log.d(TAG, "Connexion ignorée (debounce) : $address")
            return
        }
        lastSeenByAddress[address] = now

        val name = try {
            device.name
        } catch (e: SecurityException) {
            null
        } ?: "(sans nom)"

        Log.i(TAG, "Appareil Bluetooth connecté : $name [$address]")
    }

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
