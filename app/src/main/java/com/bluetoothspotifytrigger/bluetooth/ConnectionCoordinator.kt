package com.bluetoothspotifytrigger.bluetooth

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Décide QUAND déclencher la lecture à partir des events Bluetooth bruts.
 *
 * 1. Attente de l'audio : ACL_CONNECTED (lien radio) arrive AVANT que le profil
 *    audio A2DP soit prêt. Lancer Spotify à ce moment-là ferait jouer la musique
 *    sur le haut-parleur du téléphone quelques secondes. Donc :
 *    - A2DP connecté → déclenchement immédiat ;
 *    - ACL seul → attente max [a2dpWaitMs], puis déclenchement quand même
 *      (appareil sans A2DP, ou audio LE Audio non signalé par A2DP).
 *
 * 2. Une seule fois par connexion : un appareil déjà déclenché est ignoré tant
 *    qu'il ne s'est pas déconnecté (doublons ACL/A2DP d'une même connexion).
 *    Après une déconnexion :
 *    - reconnexion en moins de [reconnectGraceMs] = micro-coupure radio →
 *      ignorée (ne pas relancer la playlist en pleine route) ;
 *    - au-delà = vraie reconnexion (appareil éteint/rallumé) → nouveau
 *      déclenchement.
 *    Garde-fou : si une déconnexion a été manquée (service redémarré…), un
 *    déclenchement redevient possible après [staleTriggerMs].
 *
 * 3. Les déclenchements sont exécutés un par un ([Mutex]) : deux appareils
 *    connectés en même temps ne lancent pas deux lectures entremêlées.
 *
 * Doit être utilisé depuis un seul thread (le service l'utilise sur Main).
 */
class ConnectionCoordinator(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val a2dpWaitMs: Long = DEFAULT_A2DP_WAIT_MS,
    private val reconnectGraceMs: Long = DEFAULT_RECONNECT_GRACE_MS,
    private val staleTriggerMs: Long = DEFAULT_STALE_TRIGGER_MS,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
    private val onTrigger: suspend (address: String, name: String) -> Unit,
) {
    private val pendingAcl = mutableMapOf<String, Job>()
    private val lastTrigger = mutableMapOf<String, Long>()
    private val lastDisconnect = mutableMapOf<String, Long>()
    private val triggerLock = Mutex()

    fun onAclConnected(address: String, name: String) {
        skipReason(address)?.let {
            log("ACL $name : ignoré ($it)")
            return
        }
        if (pendingAcl.containsKey(address)) {
            log("ACL $name : doublon ignoré (attente A2DP déjà en cours)")
            return
        }
        log("ACL $name : attente de l'audio A2DP (max ${a2dpWaitMs / 1000} s)")
        pendingAcl[address] = scope.launch {
            delay(a2dpWaitMs)
            pendingAcl.remove(address)
            log("ACL $name : pas d'A2DP après ${a2dpWaitMs / 1000} s, déclenchement quand même")
            trigger(address, name)
        }
    }

    fun onA2dpConnected(address: String, name: String) {
        pendingAcl.remove(address)?.cancel()
        skipReason(address)?.let {
            log("A2DP $name : ignoré ($it)")
            return
        }
        log("A2DP $name : audio prêt, déclenchement")
        scope.launch { trigger(address, name) }
    }

    fun onDisconnected(address: String, name: String) {
        pendingAcl.remove(address)?.cancel()
        lastDisconnect[address] = clock()
        log("$name déconnecté")
    }

    /** Raison d'ignorer une connexion, ou null s'il faut déclencher. */
    private fun skipReason(address: String): String? {
        val triggeredAt = lastTrigger[address] ?: return null
        val now = clock()
        if (now - triggeredAt >= staleTriggerMs) return null
        val disconnectedAt = lastDisconnect[address]
        if (disconnectedAt == null || disconnectedAt < triggeredAt) {
            return "déjà déclenché pour cette connexion"
        }
        if (now - disconnectedAt < reconnectGraceMs) {
            return "micro-coupure : reconnecté ${(now - disconnectedAt) / 1000} s après la déconnexion"
        }
        return null
    }

    private suspend fun trigger(address: String, name: String) {
        // Marqué AVANT d'attendre le verrou : les doublons arrivant pendant
        // l'attente sont ignorés.
        lastTrigger[address] = clock()
        triggerLock.withLock { onTrigger(address, name) }
    }

    companion object {
        const val TAG = "ConnectionCoordinator"
        const val DEFAULT_A2DP_WAIT_MS = 8_000L
        const val DEFAULT_RECONNECT_GRACE_MS = 5_000L
        const val DEFAULT_STALE_TRIGGER_MS = 6 * 60 * 60 * 1000L // 6 h
    }
}
