package com.bluetoothspotifytrigger.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.Build
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluetoothspotifytrigger.BuildConfig
import com.bluetoothspotifytrigger.bluetooth.PairedDevice
import com.bluetoothspotifytrigger.debug.DebugActions
import com.bluetoothspotifytrigger.spotify.SpotifyController
import com.bluetoothspotifytrigger.spotify.SpotifyPlayer
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.data.local.triggerDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Appareil appairé + l'URI Spotify qu'on lui a associé (ou null). */
data class DeviceUi(
    val name: String,
    val address: String,
    val spotifyUri: String?,
)

sealed interface DeviceListUiState {
    data object Loading : DeviceListUiState
    data object BluetoothUnsupported : DeviceListUiState
    data object BluetoothDisabled : DeviceListUiState
    data object MissingPermission : DeviceListUiState
    data class Loaded(val devices: List<DeviceUi>) : DeviceListUiState
}

/**
 * Combine la lecture (ponctuelle) des appareils appairés avec le flux (réactif)
 * des associations persistées. Toute modification d'URI met l'UI à jour
 * automatiquement via le Flow du repository.
 */
class DeviceListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TriggerRepository(application.triggerDataStore)

    private val bluetoothRead = MutableStateFlow<BluetoothRead>(BluetoothRead.Loading)

    val state: StateFlow<DeviceListUiState> =
        combine(
            bluetoothRead,
            repository.associations,
            repository.fakeDevicesEnabled,
        ) { bt, associations, fakeEnabled ->
            // Debug : ajoute les appareils fictifs aux vrais appareils appairés.
            val fakes = if (BuildConfig.DEBUG && fakeEnabled) DebugActions.FAKE_DEVICES else emptyList()
            when (bt) {
                BluetoothRead.Loading -> DeviceListUiState.Loading
                BluetoothRead.Unsupported -> DeviceListUiState.BluetoothUnsupported
                BluetoothRead.Disabled -> DeviceListUiState.BluetoothDisabled
                BluetoothRead.MissingPermission -> DeviceListUiState.MissingPermission
                is BluetoothRead.Devices -> DeviceListUiState.Loaded(
                    (bt.devices + fakes).map { device ->
                        DeviceUi(
                            name = device.name,
                            address = device.address,
                            spotifyUri = associations[device.address],
                        )
                    },
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeviceListUiState.Loading,
        )

    // L'écran garantit que BLUETOOTH_CONNECT est accordée avant d'appeler refresh().
    @SuppressLint("MissingPermission")
    fun refresh() {
        val context = getApplication<Application>()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        bluetoothRead.value = when {
            adapter == null -> BluetoothRead.Unsupported
            !adapter.isEnabled -> BluetoothRead.Disabled
            else -> try {
                BluetoothRead.Devices(
                    adapter.bondedDevices.orEmpty()
                        .map { PairedDevice(name = it.displayName(), address = it.address) }
                        .sortedBy { it.name.lowercase() },
                )
            } catch (e: SecurityException) {
                BluetoothRead.MissingPermission
            }
        }
    }

    private val player = SpotifyPlayer(application)

    /** Résultat du dernier test de lecture (null = aucun / en cours masqué). */
    private val _testMessage = MutableStateFlow<String?>(null)
    val testMessage: StateFlow<String?> = _testMessage

    /**
     * Test manuel depuis l'UI. Premier plan → on autorise l'écran
     * d'autorisation Spotify : c'est ici que l'utilisateur donne son accord une
     * fois pour toutes, ce qui permet ensuite les lancements en arrière-plan.
     */
    fun testPlay(deviceName: String, uri: String) = viewModelScope.launch {
        _testMessage.value = "$deviceName : connexion à Spotify…"
        val result = player.play(uri, foreground = true)
        _testMessage.value = "$deviceName : ${SpotifyController.describe(result)}"
    }

    /**
     * Nom affiché dans les réglages Bluetooth d'Android : l'alias choisi par
     * l'utilisateur (ex. « JBL de NICO ») s'il existe, sinon le nom diffusé par
     * l'appareil (ex. « JBL Charge 5 »). getAlias() existe depuis Android 11.
     */
    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.displayName(): String {
        val alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias else null
        return alias ?: name ?: "(sans nom)"
    }

    fun setUri(macAddress: String, uri: String) = viewModelScope.launch {
        repository.setUri(macAddress, uri)
    }

    fun clearUri(macAddress: String) = viewModelScope.launch {
        repository.clearUri(macAddress)
    }

    private sealed interface BluetoothRead {
        data object Loading : BluetoothRead
        data object Unsupported : BluetoothRead
        data object Disabled : BluetoothRead
        data object MissingPermission : BluetoothRead
        data class Devices(val devices: List<PairedDevice>) : BluetoothRead
    }
}
