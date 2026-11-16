package com.bluetoothspotifytrigger.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État du service partagé avec l'UI. Service et UI vivent dans le même
 * process, donc de simples StateFlow en mémoire suffisent pour que l'écran
 * reflète l'état réel du service.
 */
object ServiceState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Description du dernier déclenchement (null = aucun depuis le démarrage). */
    private val _lastEvent = MutableStateFlow<String?>(null)
    val lastEvent: StateFlow<String?> = _lastEvent.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setLastEvent(description: String) {
        _lastEvent.value = description
    }
}
