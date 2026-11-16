package com.bluetoothspotifytrigger.bluetooth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Temps virtuel (kotlinx-coroutines-test) : 8 s d'attente testées instantanément. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {

    private val scope = TestScope()
    private val triggers = mutableListOf<String>()
    private val coordinator = ConnectionCoordinator(
        scope = scope,
        clock = { scope.testScheduler.currentTime },
        a2dpWaitMs = 8_000,
        cooldownMs = 60_000,
        log = {},
        onTrigger = { address, _ -> triggers += address },
    )

    private val car = "02:00:00:00:00:01"

    @Test fun `A2DP seul - declenchement immediat`() {
        coordinator.onA2dpConnected(car, "Autoradio")
        scope.runCurrent()
        assertEquals(listOf(car), triggers)
    }

    @Test fun `ACL puis A2DP - un seul declenchement, au moment de A2DP`() {
        coordinator.onAclConnected(car, "Autoradio")
        scope.advanceTimeBy(3_000); scope.runCurrent()
        assertEquals(emptyList<String>(), triggers) // on attend l'audio

        coordinator.onA2dpConnected(car, "Autoradio")
        scope.runCurrent()
        assertEquals(listOf(car), triggers)

        scope.advanceTimeBy(20_000); scope.runCurrent() // le repli ACL ne doit pas tirer
        assertEquals(listOf(car), triggers)
    }

    @Test fun `ACL sans A2DP - declenchement apres le delai d'attente`() {
        coordinator.onAclConnected(car, "Montre")
        scope.advanceTimeBy(7_999); scope.runCurrent()
        assertEquals(emptyList<String>(), triggers)
        scope.advanceTimeBy(2); scope.runCurrent()
        assertEquals(listOf(car), triggers)
    }

    @Test fun `ACL en double - un seul declenchement`() {
        coordinator.onAclConnected(car, "Autoradio")
        coordinator.onAclConnected(car, "Autoradio")
        scope.advanceTimeBy(10_000); scope.runCurrent()
        assertEquals(listOf(car), triggers)
    }

    @Test fun `cooldown - reconnexion rapide ignoree, puis acceptee apres expiration`() {
        coordinator.onA2dpConnected(car, "Autoradio")
        scope.runCurrent()
        scope.advanceTimeBy(30_000)
        coordinator.onA2dpConnected(car, "Autoradio") // micro-coupure
        scope.runCurrent()
        assertEquals(1, triggers.size)

        scope.advanceTimeBy(31_000) // > 60 s après le 1er déclenchement
        coordinator.onA2dpConnected(car, "Autoradio")
        scope.runCurrent()
        assertEquals(2, triggers.size)
    }

    @Test fun `appareils differents - independants`() {
        val headset = "02:00:00:00:00:02"
        coordinator.onA2dpConnected(car, "Autoradio")
        coordinator.onA2dpConnected(headset, "Casque")
        scope.runCurrent()
        assertEquals(listOf(car, headset), triggers)
    }
}
