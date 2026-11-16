package com.bluetoothspotifytrigger.debug

import com.bluetoothspotifytrigger.bluetooth.PairedDevice

/**
 * Commandes de test pilotables depuis adb, actives UNIQUEMENT en build debug
 * (le code qui les écoute est conditionné par BuildConfig.DEBUG ou vit dans le
 * source set `debug`).
 *
 * Sécurité : les receivers correspondants exigent la permission
 * `android.permission.DUMP` chez l'émetteur. Seul le shell adb (et le système)
 * la possède : aucune autre app installée ne peut déclencher ces commandes.
 *
 * Exemples :
 *   adb shell am broadcast -p com.bluetoothspotifytrigger \
 *       -a com.bluetoothspotifytrigger.debug.SET_FAKE_DEVICES --ez enabled true
 *   adb shell am broadcast -p com.bluetoothspotifytrigger \
 *       -a com.bluetoothspotifytrigger.debug.SIMULATE_ACL_CONNECTED \
 *       --es address 02:00:00:00:00:01 --es name "Autoradio (simulé)"
 */
object DebugActions {
    const val PERMISSION = "android.permission.DUMP"

    /** Simule un ACTION_ACL_CONNECTED. Extras : [EXTRA_ADDRESS], [EXTRA_NAME]. */
    const val SIMULATE_ACL_CONNECTED = "com.bluetoothspotifytrigger.debug.SIMULATE_ACL_CONNECTED"

    /** Affiche/masque des appareils appairés fictifs. Extra booléen : [EXTRA_ENABLED]. */
    const val SET_FAKE_DEVICES = "com.bluetoothspotifytrigger.debug.SET_FAKE_DEVICES"

    const val EXTRA_ADDRESS = "address"
    const val EXTRA_NAME = "name"
    const val EXTRA_ENABLED = "enabled"

    /**
     * Appareils fictifs. Adresses « localement administrées » (bit 0x02 du premier
     * octet) : elles ne peuvent pas entrer en collision avec un vrai appareil.
     */
    val FAKE_DEVICES = listOf(
        PairedDevice(name = "Autoradio (simulé)", address = "02:00:00:00:00:01"),
        PairedDevice(name = "Casque (simulé)", address = "02:00:00:00:00:02"),
    )
}
