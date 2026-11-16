package com.bluetoothspotifytrigger.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bluetoothspotifytrigger.ui.MainActivity

/**
 * Foreground service qui héberge le [BtConnectionReceiver].
 *
 * Rôle : rester vivant en arrière-plan (notification persistante) pour pouvoir
 * écouter les connexions Bluetooth via un receiver enregistré dynamiquement.
 *
 * À l'étape 5, on se contente de logger les connexions. Le déclenchement Spotify
 * viendra plus tard.
 */
class BluetoothMonitorService : Service() {

    private val receiver = BtConnectionReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        registerBtReceiver()
        ServiceState.setRunning(true)
        Log.i(TAG, "Service démarré")
    }

    // Si le système relance le service après l'avoir tué (mémoire), on veut qu'il
    // redémarre : START_STICKY.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        ServiceState.setRunning(false)
        Log.i(TAG, "Service arrêté")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerBtReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        // ACL_CONNECTED est émis par le SYSTÈME (un autre UID), pas par notre app.
        // Sur Android 14+, un receiver context-enregistré qui écoute des broadcasts
        // du système doit être déclaré RECEIVER_EXPORTED, sinon ils sont filtrés.
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Ex. SecurityException si la permission du type FGS manque sur API 34+.
            Log.e(TAG, "Échec startForeground", e)
            ServiceState.setRunning(false)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surveillance Bluetooth active")
            .setContentText("En attente de la connexion d'un appareil configuré.")
            .setSmallIcon(com.bluetoothspotifytrigger.R.drawable.ic_bt_notification)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Surveillance Bluetooth",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notification persistante du service de détection Bluetooth."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "BluetoothMonitor"
        private const val CHANNEL_ID = "bt_monitor"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BluetoothMonitorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothMonitorService::class.java))
        }
    }
}
