package com.bluetoothspotifytrigger.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.permissions.AppPermissions

private enum class PermStatus { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_REQUESTED }

private data class PermRow(
    val label: String,
    val rationale: String,
    val permission: String,
    val status: PermStatus,
)

@Composable
fun PermissionsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val required = remember { AppPermissions.required() }

    // Compteur de rafraîchissement : incrémenté à chaque retour de premier plan
    // et à chaque résultat de demande, pour relire l'état réel des permissions.
    var refreshKey by remember { mutableIntStateOf(0) }
    // Permissions déjà demandées au moins une fois (pour distinguer
    // "jamais demandé" de "refusé définitivement"). Persisté : survit au
    // redémarrage de l'app.
    val prefs = remember { AppPrefs(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshKey++
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshKey++
    }

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
    val anyPermanentlyDenied = rows.any { it.status == PermStatus.PERMANENTLY_DENIED }
    val allGranted = missing.isEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (allGranted) {
                "Toutes les permissions sont accordées. ✅"
            } else {
                "L'app a besoin des permissions ci-dessous pour fonctionner."
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (rows.isEmpty()) {
            Text(
                text = "Aucune permission runtime requise sur cette version d'Android.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        rows.forEach { row ->
            PermissionCard(row)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val toRequest = missing.map { it.permission }
                prefs.markRequested(toRequest)
                launcher.launch(toRequest.toTypedArray())
            },
            enabled = !allGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (allGranted) "Tout est accordé" else "Demander les permissions")
        }

        if (anyPermanentlyDenied) {
            Text(
                text = "Une permission a été refusée définitivement. " +
                    "Active-la manuellement dans les paramètres de l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ouvrir les paramètres de l'app")
            }
        }
    }
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
                Text(text = row.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.rationale,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            StatusBadge(row.status)
        }
    }
}

@Composable
private fun StatusBadge(status: PermStatus) {
    val (label, color) = when (status) {
        PermStatus.GRANTED -> "Accordée" to MaterialTheme.colorScheme.primary
        PermStatus.DENIED -> "Refusée" to MaterialTheme.colorScheme.error
        PermStatus.PERMANENTLY_DENIED -> "Refusée (paramètres)" to MaterialTheme.colorScheme.error
        PermStatus.NOT_REQUESTED -> "Non demandée" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        textAlign = TextAlign.End,
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
