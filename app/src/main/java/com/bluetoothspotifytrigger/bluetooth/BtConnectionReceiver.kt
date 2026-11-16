package com.bluetoothspotifytrigger.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.bluetoothspotifytrigger.debug.DebugActions

/**
 * Traduit les broadcasts Bluetooth bruts en events simples pour le
 * [ConnectionCoordinator] (qui décide quand déclencher) :
 * - ACTION_ACL_CONNECTED → lien radio établi ([Event.ACL]),
 * - A2DP CONNECTION_STATE_CHANGED à STATE_CONNECTED → audio prêt ([Event.A2DP]).
 *
 * Doit être enregistré DYNAMIQUEMENT (registerReceiver), pas dans le manifest :
 * depuis Android 8 (API 26), ces broadcasts ne sont plus délivrés aux
 * receivers déclarés au manifest.
 *
 * En build debug, accepte aussi les simulations adb
 * ([DebugActions.SIMULATE_ACL_CONNECTED], [DebugActions.SIMULATE_A2DP_CONNECTED])
 * qui empruntent exactement le même chemin.
 */
class BtConnectionReceiver(
    private val onEvent: (event: Event, address: String, name: String) -> Unit,
) : BroadcastReceiver() {

    enum class Event { ACL, A2DP }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = intent.bluetoothDevice() ?: return
                dispatch(Event.ACL, device.address ?: return, device.safeName())
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                if (state != BluetoothProfile.STATE_CONNECTED) return
                val device = intent.bluetoothDevice() ?: return
                dispatch(Event.A2DP, device.address ?: return, device.safeName())
            }
            DebugActions.SIMULATE_ACL_CONNECTED -> simulated(Event.ACL, intent)
            DebugActions.SIMULATE_A2DP_CONNECTED -> simulated(Event.A2DP, intent)
        }
    }

    private fun simulated(event: Event, intent: Intent) {
        val address = intent.getStringExtra(DebugActions.EXTRA_ADDRESS) ?: run {
            Log.w(TAG, "Simulation ignorée : extra '${DebugActions.EXTRA_ADDRESS}' manquant")
            return
        }
        Log.i(TAG, "[SIMULATION] $event injecté via adb")
        dispatch(event, address, intent.getStringExtra(DebugActions.EXTRA_NAME) ?: "(simulé)")
    }

    private fun dispatch(event: Event, address: String, name: String) {
        Log.i(TAG, "$event connecté : $name [$address]")
        onEvent(event, address, name)
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
    }
}
