package com.bluetoothspotifytrigger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluetoothspotifytrigger.bluetooth.BluetoothMonitorService
import com.bluetoothspotifytrigger.bluetooth.ServiceState

@Composable
fun ServiceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isRunning by ServiceState.isRunning.collectAsStateWithLifecycle()
    val lastEvent by ServiceState.lastEvent.collectAsStateWithLifecycle()

    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    // Le FGS de type connectedDevice (API 34+) exige BLUETOOTH_CONNECT ; la
    // notification persistante exige POST_NOTIFICATIONS (API 33+).
    val btOk = remember(refreshKey) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
    val notifOk = remember(refreshKey) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val canStart = btOk && notifOk

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Service de surveillance", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isRunning) "État : actif ✅" else "État : inactif",
                    style = MaterialTheme.typography.titleMedium,
                )
                lastEvent?.let {
                    Text("Dernier déclenchement : $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (!canStart) {
            Text(
                text = buildString {
                    append("Permissions manquantes pour démarrer le service : ")
                    val missing = buildList {
                        if (!btOk) add("Bluetooth")
                        if (!notifOk) add("Notifications")
                    }
                    append(missing.joinToString(", "))
                    append(". Accorde-les dans l'onglet Permissions.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (isRunning) {
            Button(
                onClick = { BluetoothMonitorService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Arrêter le service")
            }
        } else {
            Button(
                onClick = { BluetoothMonitorService.start(context) },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Démarrer le service")
            }
        }

        Text(
            text = "Une fois le service actif, une notification persistante apparaît. " +
                "Connecte un appareil Bluetooth appairé et observe logcat :\n" +
                "adb logcat -s BluetoothMonitor BtConnectionReceiver",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
