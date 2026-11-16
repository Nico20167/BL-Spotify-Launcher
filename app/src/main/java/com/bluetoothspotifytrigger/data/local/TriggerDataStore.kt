package com.bluetoothspotifytrigger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Instance unique (par process) du DataStore stockant les associations.
 * Le délégué `preferencesDataStore` garantit un seul DataStore pour ce nom de
 * fichier, ce qui évite les corruptions liées à des instances concurrentes.
 */
val Context.triggerDataStore: DataStore<Preferences> by preferencesDataStore(name = "triggers")
