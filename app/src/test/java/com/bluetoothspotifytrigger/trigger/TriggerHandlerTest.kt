package com.bluetoothspotifytrigger.trigger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bluetoothspotifytrigger.data.DeviceConfig
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.spotify.PlayResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Logique de déclenchement testée sur JVM avec un vrai DataStore (fichier
 * temporaire) et un faux lecteur Spotify qui enregistre les URI demandés.
 */
class TriggerHandlerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repository: TriggerRepository
    private val played = mutableListOf<String>()
    private val volumes = mutableListOf<Int?>()
    private var nextResult: PlayResult = PlayResult.Success

    private lateinit var handler: TriggerHandler

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("triggers.preferences_pb").also { it.delete() }
        }
        repository = TriggerRepository(dataStore)
        handler = TriggerHandler(repository) { uri, volume ->
            played += uri
            volumes += volume
            nextResult
        }
    }

    @Test fun `volume configure - transmis a la lecture`() = runBlocking {
        repository.setConfig(
            "02:00:00:00:00:01",
            DeviceConfig("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", volumePercent = 40),
        )
        handler.onDeviceConnected("02:00:00:00:00:01", "Autoradio")
        assertEquals(listOf<Int?>(40), volumes)
    }

    @Test fun `volume non configure - null (volume inchange)`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        handler.onDeviceConnected("02:00:00:00:00:01", "Autoradio")
        assertEquals(listOf<Int?>(null), volumes)
    }

    @Test fun `volume retire - redevient null, URI conserve`() = runBlocking {
        val mac = "02:00:00:00:00:01"
        repository.setConfig(mac, DeviceConfig("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", 70))
        repository.setConfig(mac, DeviceConfig("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", null))
        assertEquals(DeviceConfig("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", null), repository.configs.first()[mac])
    }

    @Test fun `URI d'ancienne version avec parametres - nettoye avant lecture`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:1a2B3c4D5e6F7g8H9i0J1k?si=abc&pi=def")
        handler.onDeviceConnected("02:00:00:00:00:01", "JBL")
        assertEquals(listOf("spotify:playlist:1a2B3c4D5e6F7g8H9i0J1k"), played)
    }

    @After fun tearDown() = scope.cancel()

    @Test fun `appareil sans association - aucune lecture`() = runBlocking {
        val outcome = handler.onDeviceConnected("AA:BB:CC:DD:EE:FF", "Inconnu")
        assertEquals(TriggerHandler.Outcome.NoAssociation, outcome)
        assertTrue(played.isEmpty())
    }

    @Test fun `appareil associe - lit le bon URI`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        repository.setUri("02:00:00:00:00:02", "spotify:album:1a2B3c4D5e6F7g8H9i0J1k")

        val outcome = handler.onDeviceConnected("02:00:00:00:00:02", "Casque")

        assertEquals(listOf("spotify:album:1a2B3c4D5e6F7g8H9i0J1k"), played)
        assertEquals(TriggerHandler.Outcome.Played("spotify:album:1a2B3c4D5e6F7g8H9i0J1k", PlayResult.Success), outcome)
    }

    @Test fun `echec Spotify remonte tel quel`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        nextResult = PlayResult.NotConfirmed

        val outcome = handler.onDeviceConnected("02:00:00:00:00:01", "Autoradio")

        assertEquals(
            TriggerHandler.Outcome.Played("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", PlayResult.NotConfirmed),
            outcome,
        )
    }

    @Test fun `association effacee - plus de lecture`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        repository.clear("02:00:00:00:00:01")

        assertEquals(
            TriggerHandler.Outcome.NoAssociation,
            handler.onDeviceConnected("02:00:00:00:00:01", "Autoradio"),
        )
        assertTrue(played.isEmpty())
    }
}
