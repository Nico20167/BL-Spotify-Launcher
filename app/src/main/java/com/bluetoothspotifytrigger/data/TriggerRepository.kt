package com.bluetoothspotifytrigger.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * CRUD des associations `adresse MAC -> URI Spotify`.
 *
 * Chaque association est une entrée DataStore dont la clé est préfixée par
 * [KEY_PREFIX]. Cela permet de cohabiter avec d'éventuels autres réglages dans
 * le même DataStore tout en sachant lesquels sont des associations.
 */
class TriggerRepository(private val dataStore: DataStore<Preferences>) {

    /** Émis à chaque modification : map MAC -> URI Spotify. */
    val associations: Flow<Map<String, String>> = dataStore.data
        .catch { e ->
            // Une lecture DataStore peut échouer sur IOException (fichier corrompu,
            // I/O) : on retombe sur des préférences vides plutôt que de crasher.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            prefs.asMap()
                .mapNotNull { (key, value) ->
                    if (key.name.startsWith(KEY_PREFIX) && value is String) {
                        key.name.removePrefix(KEY_PREFIX) to value
                    } else {
                        null
                    }
                }
                .toMap()
        }

    suspend fun setUri(macAddress: String, uri: String) {
        dataStore.edit { it[keyFor(macAddress)] = uri }
    }

    suspend fun clearUri(macAddress: String) {
        dataStore.edit { it.remove(keyFor(macAddress)) }
    }

    private fun keyFor(macAddress: String) = stringPreferencesKey(KEY_PREFIX + macAddress)

    companion object {
        private const val KEY_PREFIX = "device_uri:"
    }
}
