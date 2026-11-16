package com.bluetoothspotifytrigger.trigger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.spotify.SpotifyController.PlayResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var nextResult: PlayResult = PlayResult.Success

    private lateinit var handler: TriggerHandler

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("triggers.preferences_pb").also { it.delete() }
        }
        repository = TriggerRepository(dataStore)
        handler = TriggerHandler(repository) { uri ->
            played += uri
            nextResult
        }
    }

    @After fun tearDown() = scope.cancel()

    @Test fun `appareil sans association - aucune lecture`() = runBlocking {
        val outcome = handler.onDeviceConnected("AA:BB:CC:DD:EE:FF", "Inconnu")
        assertEquals(TriggerHandler.Outcome.NoAssociation, outcome)
        assertTrue(played.isEmpty())
    }

    @Test fun `appareil associe - lit le bon URI`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:AAA")
        repository.setUri("02:00:00:00:00:02", "spotify:album:BBB")

        val outcome = handler.onDeviceConnected("02:00:00:00:00:02", "Casque")

        assertEquals(listOf("spotify:album:BBB"), played)
        assertEquals(TriggerHandler.Outcome.Played("spotify:album:BBB", PlayResult.Success), outcome)
    }

    @Test fun `echec Spotify remonte tel quel`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:AAA")
        nextResult = PlayResult.NotAuthorized

        val outcome = handler.onDeviceConnected("02:00:00:00:00:01", "Autoradio")

        assertEquals(
            TriggerHandler.Outcome.Played("spotify:playlist:AAA", PlayResult.NotAuthorized),
            outcome,
        )
    }

    @Test fun `association effacee - plus de lecture`() = runBlocking {
        repository.setUri("02:00:00:00:00:01", "spotify:playlist:AAA")
        repository.clearUri("02:00:00:00:00:01")

        assertEquals(
            TriggerHandler.Outcome.NoAssociation,
            handler.onDeviceConnected("02:00:00:00:00:01", "Autoradio"),
        )
        assertTrue(played.isEmpty())
    }
}
