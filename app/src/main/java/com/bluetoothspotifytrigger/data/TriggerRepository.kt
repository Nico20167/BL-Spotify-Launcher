package com.bluetoothspotifytrigger.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Réglages d'un appareil : quoi jouer, et à quel volume (null = ne pas toucher). */
data class DeviceConfig(
    val uri: String,
    val volumePercent: Int? = null,
)

/**
 * CRUD des réglages par appareil (`adresse MAC -> URI Spotify + volume`).
 *
 * Chaque valeur est une entrée DataStore dont la clé est préfixée
 * ([URI_PREFIX], [VOLUME_PREFIX]) + adresse MAC. Cela permet de cohabiter avec
 * d'autres réglages dans le même DataStore tout en sachant lesquels sont quoi.
 */
class TriggerRepository(private val dataStore: DataStore<Preferences>) {

    private val safeData: Flow<Preferences> = dataStore.data
        .catch { e ->
            // Une lecture DataStore peut échouer sur IOException (fichier corrompu,
            // I/O) : on retombe sur des préférences vides plutôt que de crasher.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    /** Émis à chaque modification : map MAC -> réglages (seulement les appareils ayant un URI). */
    val configs: Flow<Map<String, DeviceConfig>> = safeData.map { prefs ->
        val entries = prefs.asMap()
        entries.mapNotNull { (key, value) ->
            if (!key.name.startsWith(URI_PREFIX) || value !is String) return@mapNotNull null
            val mac = key.name.removePrefix(URI_PREFIX)
            val volume = entries[intPreferencesKey(VOLUME_PREFIX + mac)] as? Int
            mac to DeviceConfig(uri = value, volumePercent = volume)
        }.toMap()
    }

    suspend fun setConfig(macAddress: String, config: DeviceConfig) {
        dataStore.edit {
            it[uriKey(macAddress)] = config.uri
            if (config.volumePercent != null) {
                it[volumeKey(macAddress)] = config.volumePercent.coerceIn(0, 100)
            } else {
                it.remove(volumeKey(macAddress))
            }
        }
    }

    /** Raccourci : URI seul, volume inchangé. */
    suspend fun setUri(macAddress: String, uri: String) {
        dataStore.edit { it[uriKey(macAddress)] = uri }
    }

    suspend fun clear(macAddress: String) {
        dataStore.edit {
            it.remove(uriKey(macAddress))
            it.remove(volumeKey(macAddress))
        }
    }

    private fun uriKey(macAddress: String) = stringPreferencesKey(URI_PREFIX + macAddress)
    private fun volumeKey(macAddress: String) = intPreferencesKey(VOLUME_PREFIX + macAddress)

    /** Debug : afficher des appareils appairés fictifs (voir DebugActions). */
    val fakeDevicesEnabled: Flow<Boolean> = safeData.map { it[FAKE_DEVICES_KEY] ?: false }

    suspend fun setFakeDevicesEnabled(enabled: Boolean) {
        dataStore.edit { it[FAKE_DEVICES_KEY] = enabled }
    }

    companion object {
        // "device_uri:" : préfixe historique, conservé pour garder les associations existantes.
        private const val URI_PREFIX = "device_uri:"
        private const val VOLUME_PREFIX = "device_volume:"
        private val FAKE_DEVICES_KEY = booleanPreferencesKey("debug_fake_devices")
    }
}
