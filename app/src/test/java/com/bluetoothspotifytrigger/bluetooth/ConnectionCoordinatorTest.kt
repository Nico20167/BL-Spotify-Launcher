package com.bluetoothspotifytrigger.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Temps virtuel (kotlinx-coroutines-test) : les délais sont testés instantanément. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {

    private val scope = TestScope()
    private val triggers = mutableListOf<String>()
    private var blockTrigger: CompletableDeferred<Unit>? = null
    private val coordinator = ConnectionCoordinator(
        scope = scope,
        clock = { scope.testScheduler.currentTime },
        a2dpWaitMs = 8_000,
        reconnectGraceMs = 5_000,
        staleTriggerMs = 6 * 3_600_000L,
        log = {},
        onTrigger = { address, _ ->
            triggers += address
            blockTrigger?.await()
        },
    )

    private val car = "02:00:00:00:00:01"
    private val headset = "02:00:00:00:00:02"

    private fun step(ms: Long = 0) {
        if (ms > 0) scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test fun `A2DP seul - declenchement immediat`() {
        coordinator.onA2dpConnected(car, "Autoradio"); step()
        assertEquals(listOf(car), triggers)
    }

    @Test fun `ACL puis A2DP - un seul declenchement, au moment de A2DP`() {
        coordinator.onAclConnected(car, "Autoradio"); step(3_000)
        assertEquals(emptyList<String>(), triggers) // on attend l'audio
        coordinator.onA2dpConnected(car, "Autoradio"); step()
        assertEquals(listOf(car), triggers)
        step(20_000) // le repli ACL ne doit pas tirer
        assertEquals(listOf(car), triggers)
    }

    @Test fun `ACL sans A2DP - declenchement apres le delai d'attente`() {
        coordinator.onAclConnected(car, "Montre"); step(7_999)
        assertEquals(emptyList<String>(), triggers)
        step(2)
        assertEquals(listOf(car), triggers)
    }

    @Test fun `doublons d'une meme connexion - ignores meme longtemps apres`() {
        coordinator.onA2dpConnected(car, "Autoradio"); step()
        coordinator.onAclConnected(car, "Autoradio")
        coordinator.onA2dpConnected(car, "Autoradio"); step(120_000)
        coordinator.onA2dpConnected(car, "Autoradio"); step()
        assertEquals(1, triggers.size)
    }

    @Test fun `vraie reconnexion rapide (30 s) - redeclenche`() {
        // Cas réel : enceinte éteinte puis rallumée 30 s après le 1er déclenchement.
        coordinator.onA2dpConnected(car, "JBL"); step(20_000)
        coordinator.onDisconnected(car, "JBL"); step(10_000)
        coordinator.onA2dpConnected(car, "JBL"); step()
        assertEquals(listOf(car, car), triggers)
    }

    @Test fun `micro-coupure - reconnexion en moins de 5 s ignoree`() {
        coordinator.onA2dpConnected(car, "Autoradio"); step(600_000)
        coordinator.onDisconnected(car, "Autoradio"); step(2_000)
        coordinator.onA2dpConnected(car, "Autoradio"); step()
        assertEquals(1, triggers.size)
    }

    @Test fun `deconnexion pendant l'attente A2DP - pas de declenchement`() {
        coordinator.onAclConnected(car, "Autoradio"); step(2_000)
        coordinator.onDisconnected(car, "Autoradio"); step(10_000)
        assertEquals(emptyList<String>(), triggers)
    }

    @Test fun `deconnexion manquee - garde-fou apres 6 h`() {
        coordinator.onA2dpConnected(car, "Autoradio"); step(6 * 3_600_000L)
        coordinator.onA2dpConnected(car, "Autoradio"); step()
        assertEquals(2, triggers.size)
    }

    @Test fun `deux appareils simultanes - lectures executees l'une apres l'autre`() {
        blockTrigger = CompletableDeferred()
        coordinator.onA2dpConnected(car, "Autoradio")
        coordinator.onA2dpConnected(headset, "Casque"); step()
        assertEquals(listOf(car), triggers) // le casque attend la fin de la 1re lecture
        blockTrigger!!.complete(Unit); step()
        assertEquals(listOf(car, headset), triggers)
    }
}
