package com.bluetoothspotifytrigger.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Source de vérité des permissions runtime dont l'app a besoin, calculée
 * selon le niveau d'API de l'appareil.
 *
 * - BLUETOOTH_CONNECT n'existe comme permission runtime qu'à partir d'Android 12 (API 31).
 *   En dessous, l'accès aux appareils appairés passe par la permission legacy BLUETOOTH
 *   (accordée à l'installation) → rien à demander à l'exécution.
 * - POST_NOTIFICATIONS n'existe qu'à partir d'Android 13 (API 33).
 *   En dessous, les notifications sont autorisées par défaut.
 */
object AppPermissions {

    data class RuntimePermission(
        val permission: String,
        val label: String,
        val rationale: String,
    )

    fun required(): List<RuntimePermission> {
        val list = mutableListOf<RuntimePermission>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += RuntimePermission(
                permission = Manifest.permission.BLUETOOTH_CONNECT,
                label = "Bluetooth",
                rationale = "Pour lister vos appareils appairés et détecter leur connexion.",
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += RuntimePermission(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                label = "Notifications",
                rationale = "Pour afficher la notification du service qui tourne en arrière-plan.",
            )
        }

        return list
    }

    /** Toutes les permissions runtime nécessaires sont-elles accordées ? */
    fun allGranted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }
}
