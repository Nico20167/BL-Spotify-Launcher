package com.bluetoothspotifytrigger.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bluetoothspotifytrigger.bluetooth.BluetoothMonitorService
import com.bluetoothspotifytrigger.bluetooth.ServiceState
import com.bluetoothspotifytrigger.spotify.SpotifyIntentPlayer

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
    val batteryExempt = remember(refreshKey) { isIgnoringBatteryOptimizations(context) }
    val overlayOk = remember(refreshKey) { SpotifyIntentPlayer.canStartFromBackground(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        // Ouverture de Spotify depuis l'arrière-plan : Android 10+ l'interdit sauf
        // exemption ; on utilise « Afficher par-dessus les autres apps ».
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Lancement de Spotify", style = MaterialTheme.typography.titleMedium)
                if (overlayOk) {
                    Text(
                        "Ouverture de Spotify en arrière-plan autorisée ✅",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Requis : autorise « Afficher par-dessus les autres apps », sinon " +
                            "Android bloque l'ouverture de Spotify quand l'app est fermée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = { openOverlaySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Autoriser l'affichage par-dessus")
                    }
                }
            }
        }

        // Optimisation batterie : sur beaucoup de téléphones (Samsung, Xiaomi…),
        // le système finit par tuer le service malgré la notification si l'app
        // n'est pas exemptée.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Optimisation batterie", style = MaterialTheme.typography.titleMedium)
                if (batteryExempt) {
                    Text(
                        "L'app est exemptée ✅ : le service ne sera pas mis en veille.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Recommandé : sans exemption, certains téléphones arrêtent le " +
                            "service en arrière-plan et la musique ne se lance plus.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { requestBatteryExemption(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Désactiver l'optimisation batterie")
                    }
                }
            }
        }

        Text(
            text = "Une fois le service actif, une notification persistante apparaît. " +
                "Il redémarre automatiquement après un redémarrage du téléphone.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Usage personnel assumé (voir manifest).
private fun requestBatteryExemption(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        // Certains OEM n'implémentent pas le dialogue direct : liste générale.
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
