package com.bluetoothspotifytrigger.bluetooth

/**
 * Représentation UI minimale d'un appareil Bluetooth appairé.
 * L'adresse MAC sert de clé stable (le nom peut être nul ou changer).
 */
data class PairedDevice(
    val name: String,
    val address: String,
)
