package com.bluetoothspotifytrigger.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État « le service tourne-t-il ? » partagé entre le service et l'UI.
 * Service et UI vivent dans le même process, donc un simple StateFlow en mémoire
 * suffit pour que l'écran reflète l'état réel du service.
 */
object ServiceState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}
