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
import com.bluetoothspotifytrigger.BuildConfig
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.data.local.triggerDataStore
import com.bluetoothspotifytrigger.debug.DebugActions
import com.bluetoothspotifytrigger.trigger.TriggerHandler
import com.bluetoothspotifytrigger.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service qui héberge le [BtConnectionReceiver].
 *
 * Rôle : rester vivant en arrière-plan (notification persistante) pour pouvoir
 * écouter les connexions Bluetooth via un receiver enregistré dynamiquement.
 *
 * Chaque connexion (après débounce) est confiée au [TriggerHandler], qui
 * cherche l'URI Spotify associé à l'appareil.
 */
class BluetoothMonitorService : Service() {

    // Scope lié à la vie du service : annulé dans onDestroy. SupervisorJob pour
    // qu'un échec de traitement d'une connexion n'empêche pas les suivantes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var triggerHandler: TriggerHandler

    private val onConnected: (String, String) -> Unit = { address, name ->
        scope.launch {
            runCatching { triggerHandler.onDeviceConnected(address, name) }
                .onFailure { Log.e(TAG, "Erreur lors du traitement de $address", it) }
        }
    }

    // Deux instances : la vraie écoute système, et (debug uniquement) la
    // simulation adb protégée par la permission DUMP.
    private val receiver = BtConnectionReceiver(onConnected)
    private val debugReceiver = BtConnectionReceiver(onConnected)
    private val registeredReceivers = mutableListOf<BtConnectionReceiver>()

    override fun onCreate() {
        super.onCreate()
        triggerHandler = TriggerHandler(TriggerRepository(applicationContext.triggerDataStore))
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
        registeredReceivers.forEach { runCatching { unregisterReceiver(it) } }
        registeredReceivers.clear()
        scope.cancel()
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
        registeredReceivers += receiver

        if (BuildConfig.DEBUG) {
            // Simulation adb : exportée (le shell est un autre UID) mais réservée
            // aux émetteurs détenant DUMP, c.-à-d. adb uniquement.
            ContextCompat.registerReceiver(
                this,
                debugReceiver,
                IntentFilter(DebugActions.SIMULATE_ACL_CONNECTED),
                DebugActions.PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
            registeredReceivers += debugReceiver
            Log.i(TAG, "Simulation adb active (build debug)")
        }
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
