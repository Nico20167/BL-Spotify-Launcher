package com.bluetoothspotifytrigger.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluetoothspotifytrigger.BuildConfig
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.spotify.PlaybackMode
import com.bluetoothspotifytrigger.spotify.SpotifyLink

@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    viewModel: DeviceListViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // BLUETOOTH_CONNECT n'est une permission runtime qu'à partir d'Android 12 (API 31).
    val needsBtPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var refreshKey by remember { mutableIntStateOf(0) }
    val prefs = remember { AppPrefs(context) }

    // Appareil en cours d'édition d'URI (null = aucun dialogue ouvert).
    var editing by remember { mutableStateOf<DeviceUi?>(null) }

    val btGranted = remember(refreshKey) {
        if (!needsBtPermission) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshKey++
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    // Recharge la liste dès que la permission devient accordée ou au rafraîchissement.
    LaunchedEffect(btGranted, refreshKey) {
        if (btGranted) viewModel.refresh()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val testMessage by viewModel.testMessage.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Appareils appairés",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank() &&
            AppPrefs(context).playbackMode == PlaybackMode.SDK
        ) {
            Text(
                "Client ID Spotify non configuré : la lecture ne fonctionnera pas " +
                    "(renseigner SPOTIFY_CLIENT_ID dans local.properties puis rebuild).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        testMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        if (!btGranted) {
            val permanentlyDenied = needsBtPermission && activity != null &&
                prefs.wasRequested(Manifest.permission.BLUETOOTH_CONNECT) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.BLUETOOTH_CONNECT,
                )
            BluetoothPermissionNeeded(
                permanentlyDenied = permanentlyDenied,
                onRequest = {
                    prefs.markRequested(listOf(Manifest.permission.BLUETOOTH_CONNECT))
                    launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
                onOpenSettings = { openAppSettings(context) },
            )
            return@Column
        }

        when (val s = state) {
            DeviceListUiState.Loading ->
                Text("Chargement…", style = MaterialTheme.typography.bodyMedium)

            DeviceListUiState.BluetoothUnsupported ->
                Text(
                    "Cet appareil ne dispose pas de Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

            DeviceListUiState.BluetoothDisabled -> {
                Text(
                    "Le Bluetooth est désactivé. Active-le pour voir tes appareils appairés.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { openBluetoothSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ouvrir les réglages Bluetooth")
                }
            }

            DeviceListUiState.MissingPermission ->
                Text(
                    "Permission Bluetooth manquante.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

            is DeviceListUiState.Loaded -> {
                if (s.devices.isEmpty()) {
                    Text(
                        "Aucun appareil appairé. Appaire d'abord ton autoradio / casque / " +
                            "enceinte dans les réglages Bluetooth d'Android, puis rafraîchis.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "${s.devices.size} appareil(s) appairé(s) :",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(s.devices, key = { it.address }) { device ->
                            DeviceCard(
                                device = device,
                                onEdit = { editing = device },
                                onTest = { uri -> viewModel.testPlay(device.name, uri) },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { refreshKey++ },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Rafraîchir")
                }
            }
        }
    }

    editing?.let { device ->
        UriEditDialog(
            device = device,
            onDismiss = { editing = null },
            onSave = { uri ->
                viewModel.setUri(device.address, uri)
                editing = null
            },
            onClear = {
                viewModel.clearUri(device.address)
                editing = null
            },
        )
    }
}

@Composable
private fun BluetoothPermissionNeeded(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text(
        "La permission Bluetooth est requise pour lister les appareils appairés.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (permanentlyDenied) {
        Text(
            "Permission refusée définitivement. Active-la dans les paramètres de l'app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Ouvrir les paramètres de l'app")
        }
    } else {
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Autoriser le Bluetooth")
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceUi, onEdit: () -> Unit, onTest: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = device.name, style = MaterialTheme.typography.titleMedium)
            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = device.spotifyUri ?: "Aucun URI associé",
                style = MaterialTheme.typography.bodyMedium,
                color = if (device.spotifyUri == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text(if (device.spotifyUri == null) "Associer un URI" else "Modifier l'URI")
                }
                device.spotifyUri?.let { uri ->
                    TextButton(onClick = { onTest(uri) }) { Text("Tester") }
                }
            }
        }
    }
}

@Composable
private fun UriEditDialog(
    device: DeviceUi,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember { mutableStateOf(device.spotifyUri ?: "") }
    // Accepte un URI spotify:… ou un lien de partage open.spotify.com, converti en URI.
    val parsed = SpotifyLink.parse(text)
    val showError = text.isNotBlank() && parsed is SpotifyLink.Result.Invalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(device.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Lien ou URI Spotify") },
                    singleLine = true,
                    isError = showError,
                    supportingText = {
                        Text(
                            when {
                                showError -> (parsed as SpotifyLink.Result.Invalid).reason
                                parsed is SpotifyLink.Result.Valid && parsed.uri != text.trim() ->
                                    "→ ${parsed.uri}"
                                else ->
                                    "Dans Spotify : Partager → Copier le lien, puis colle-le ici."
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (parsed as? SpotifyLink.Result.Valid)?.let { onSave(it.uri) } },
                enabled = parsed is SpotifyLink.Result.Valid,
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (device.spotifyUri != null) {
                    TextButton(onClick = onClear) { Text("Effacer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openBluetoothSettings(context: Context) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
