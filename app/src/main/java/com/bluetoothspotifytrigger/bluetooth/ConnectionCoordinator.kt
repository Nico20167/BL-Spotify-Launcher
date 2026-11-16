package com.bluetoothspotifytrigger.bluetooth

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Décide QUAND déclencher la lecture à partir des events Bluetooth bruts.
 *
 * Problème : ACL_CONNECTED (lien radio établi) arrive AVANT que le profil audio
 * A2DP soit connecté. Lancer Spotify à ce moment-là ferait jouer la musique sur
 * le haut-parleur du téléphone quelques secondes, avant la bascule vers
 * l'autoradio. Les autoradios connectent souvent d'abord le kit mains-libres
 * (HFP), puis A2DP plusieurs secondes après.
 *
 * Stratégie :
 * - A2DP connecté → déclenchement immédiat (cas nominal : l'audio part bien
 *   dans l'appareil).
 * - ACL connecté → on attend jusqu'à [a2dpWaitMs] qu'A2DP suive ; sinon on
 *   déclenche quand même (appareil sans profil audio, ou A2DP non signalé).
 * - Après un déclenchement, [cooldownMs] pendant lequel les events du même
 *   appareil sont ignorés (doublons ACL/A2DP, micro-coupures radio).
 *
 * Doit être utilisé depuis un seul thread (le service l'utilise sur Main) :
 * pas de synchronisation interne.
 */
class ConnectionCoordinator(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val a2dpWaitMs: Long = DEFAULT_A2DP_WAIT_MS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
    private val onTrigger: suspend (address: String, name: String) -> Unit,
) {
    private val pendingAcl = mutableMapOf<String, Job>()
    private val lastTrigger = mutableMapOf<String, Long>()

    fun onAclConnected(address: String, name: String) {
        if (inCooldown(address)) {
            log("ACL $name : ignoré (déclenché il y a moins de ${cooldownMs / 1000} s)")
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
        if (inCooldown(address)) {
            log("A2DP $name : ignoré (déjà déclenché il y a moins de ${cooldownMs / 1000} s)")
            return
        }
        log("A2DP $name : audio prêt, déclenchement")
        scope.launch { trigger(address, name) }
    }

    private suspend fun trigger(address: String, name: String) {
        lastTrigger[address] = clock()
        onTrigger(address, name)
    }

    private fun inCooldown(address: String): Boolean {
        val last = lastTrigger[address] ?: return false
        return clock() - last < cooldownMs
    }

    companion object {
        const val TAG = "ConnectionCoordinator"
        const val DEFAULT_A2DP_WAIT_MS = 8_000L
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}
