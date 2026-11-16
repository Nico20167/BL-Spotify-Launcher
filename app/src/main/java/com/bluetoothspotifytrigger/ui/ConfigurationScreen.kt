package com.bluetoothspotifytrigger.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluetoothspotifytrigger.bluetooth.BluetoothMonitorService
import com.bluetoothspotifytrigger.bluetooth.ServiceState
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.permissions.AppPermissions
import com.bluetoothspotifytrigger.spotify.MediaAccessListener
import com.bluetoothspotifytrigger.spotify.SpotifyIntentPlayer
import com.bluetoothspotifytrigger.spotify.SpotifySessionControl

/**
 * Tout ce qui concerne la configuration du téléphone, dans l'ordre où
 * l'utilisateur doit le faire :
 * 1. permissions runtime (Bluetooth, notifications),
 * 2. autorisations spéciales (réglages système : affichage par-dessus,
 *    batterie, accès aux notifications),
 * 3. démarrage du service.
 *
 * Tout est relu à chaque retour au premier plan (ON_RESUME), puisque la
 * plupart des réglages se font dans les écrans système.
 */
@Composable
fun ConfigurationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { AppPrefs(context) }
    val required = remember { AppPermissions.required() }

    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshKey++ }

    val rows = remember(refreshKey) {
        required.map { info ->
            val granted = ContextCompat.checkSelfPermission(context, info.permission) ==
                PackageManager.PERMISSION_GRANTED
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, info.permission)
            } ?: false
            val wasRequested = prefs.wasRequested(info.permission)
            val status = when {
                granted -> PermStatus.GRANTED
                wasRequested && !showRationale -> PermStatus.PERMANENTLY_DENIED
                wasRequested -> PermStatus.DENIED
                else -> PermStatus.NOT_REQUESTED
            }
            PermRow(info.label, info.rationale, info.permission, status)
        }
    }
    val missing = rows.filter { it.status != PermStatus.GRANTED }
    val allGranted = missing.isEmpty()

    val overlayOk = remember(refreshKey) { SpotifyIntentPlayer.canStartFromBackground(context) }
    val batteryExempt = remember(refreshKey) { isIgnoringBatteryOptimizations(context) }
    val sessionAccess = remember(refreshKey) { SpotifySessionControl(context).hasAccess() }

    val isRunning by ServiceState.isRunning.collectAsStateWithLifecycle()
    val lastEvent by ServiceState.lastEvent.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineSmall)

        // --- 1. Permissions -------------------------------------------------
        SectionTitle("1. Permissions")
        rows.forEach { PermissionCard(it) }
        if (!allGranted) {
            Button(
                onClick = {
                    val toRequest = missing.map { it.permission }
                    prefs.markRequested(toRequest)
                    launcher.launch(toRequest.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Demander les permissions") }
        }
        if (rows.any { it.status == PermStatus.PERMANENTLY_DENIED }) {
            Text(
                "Une permission a été refusée définitivement : active-la dans les " +
                    "paramètres de l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir les paramètres de l'app")
            }
        }

        // --- 2. Autorisations spéciales ------------------------------------
        SectionTitle("2. Autorisations spéciales")
        SpecialAccessCard(
            title = "Afficher par-dessus les autres apps",
            level = "Indispensable",
            ok = overlayOk,
            explanation = "Permet d'ouvrir Spotify quand l'app est fermée ou le téléphone verrouillé.",
            buttonText = "Autoriser",
            onClick = { openOverlaySettings(context) },
        )
        SpecialAccessCard(
            title = "Ignorer l'optimisation batterie",
            level = "Recommandé",
            ok = batteryExempt,
            explanation = "Sans exemption, certains téléphones arrêtent la surveillance en arrière-plan.",
            buttonText = "Autoriser",
            onClick = { requestBatteryExemption(context) },
        )
        SpecialAccessCard(
            title = "Accès aux notifications",
            level = "Facultatif",
            ok = sessionAccess,
            explanation = "Nécessaire uniquement pour la lecture aléatoire : donne accès aux " +
                "commandes de lecture de Spotify. L'app ne lit aucune notification.",
            buttonText = "Autoriser",
            onClick = { openNotificationAccessSettings(context) },
            hint = "Option grisée ? Ouvre « Infos de l'appli », menu ⋮ en haut à droite → " +
                "« Autoriser les paramètres restreints », puis réessaie.",
            hintButtonText = "Infos de l'appli",
            onHintClick = { openAppSettings(context) },
        )

        // --- 3. Service -----------------------------------------------------
        SectionTitle("3. Surveillance Bluetooth")
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
        if (!allGranted) {
            Text(
                "Accorde d'abord les permissions (section 1) pour démarrer la surveillance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (isRunning) {
            Button(onClick = { BluetoothMonitorService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Arrêter la surveillance")
            }
        } else {
            Button(
                onClick = { BluetoothMonitorService.start(context) },
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Démarrer la surveillance") }
        }
        Text(
            "Une fois démarrée, une notification persistante apparaît. La surveillance " +
                "redémarre automatiquement après un redémarrage du téléphone.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private enum class PermStatus { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_REQUESTED }

private data class PermRow(
    val label: String,
    val rationale: String,
    val permission: String,
    val status: PermStatus,
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun PermissionCard(row: PermRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.titleMedium)
                Text(row.rationale, style = MaterialTheme.typography.bodySmall)
            }
            val (label, color) = when (row.status) {
                PermStatus.GRANTED -> "Accordée" to MaterialTheme.colorScheme.primary
                PermStatus.DENIED -> "Refusée" to MaterialTheme.colorScheme.error
                PermStatus.PERMANENTLY_DENIED -> "Refusée (paramètres)" to MaterialTheme.colorScheme.error
                PermStatus.NOT_REQUESTED -> "Non demandée" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SpecialAccessCard(
    title: String,
    level: String,
    ok: Boolean,
    explanation: String,
    buttonText: String,
    onClick: () -> Unit,
    hint: String? = null,
    hintButtonText: String? = null,
    onHintClick: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (ok) "Accordée" else level,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        ok -> MaterialTheme.colorScheme.primary
                        level == "Indispensable" -> MaterialTheme.colorScheme.error
                        else -> Color.Unspecified
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(explanation, style = MaterialTheme.typography.bodySmall)
            if (!ok) {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                    if (hintButtonText != null && onHintClick != null) {
                        OutlinedButton(onClick = onHintClick, modifier = Modifier.fillMaxWidth()) {
                            Text(hintButtonText)
                        }
                    }
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

private fun openNotificationAccessSettings(context: Context) {
    val component = ComponentName(context, MediaAccessListener::class.java)
    // Android 11+ : écran direct pour notre app ; sinon, liste générale.
    val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else {
        null
    }
    try {
        context.startActivity(direct ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Usage personnel assumé (voir manifest).
private fun requestBatteryExemption(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    } catch (e: ActivityNotFoundException) {
        // Certains constructeurs n'implémentent pas le dialogue direct : liste générale.
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
