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
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bluetoothspotifytrigger.BuildConfig
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.data.local.triggerDataStore
import com.bluetoothspotifytrigger.debug.DebugActions
import com.bluetoothspotifytrigger.spotify.SpotifyController
import com.bluetoothspotifytrigger.trigger.TriggerHandler
import com.bluetoothspotifytrigger.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date

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
                .onSuccess { reportOutcome(name, it) }
                .onFailure { Log.e(TAG, "Erreur lors du traitement de $address", it) }
        }
    }

    /** Met à jour la notification persistante ; alerte l'utilisateur en cas d'échec. */
    private fun reportOutcome(deviceName: String, outcome: TriggerHandler.Outcome) {
        if (outcome !is TriggerHandler.Outcome.Played) return
        val time = DateFormat.getTimeFormat(this).format(Date())
        val ok = outcome.result == SpotifyController.PlayResult.Success
        val summary = "$time · $deviceName → ${SpotifyController.describe(outcome.result)}"
        ServiceState.setLastEvent(summary)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text = "Dernier : $summary"))
        if (!ok) {
            manager.notify(
                ALERT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setSmallIcon(com.bluetoothspotifytrigger.R.drawable.ic_bt_notification)
                    .setContentTitle("Lecture Spotify impossible ($deviceName)")
                    .setContentText(SpotifyController.describe(outcome.result))
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(SpotifyController.describe(outcome.result)),
                    )
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    // Deux instances : la vraie écoute système, et (debug uniquement) la
    // simulation adb protégée par la permission DUMP.
    private val receiver = BtConnectionReceiver(onConnected)
    private val debugReceiver = BtConnectionReceiver(onConnected)
    private val registeredReceivers = mutableListOf<BtConnectionReceiver>()

    override fun onCreate() {
        super.onCreate()
        val spotify = SpotifyController(this)
        triggerHandler = TriggerHandler(
            repository = TriggerRepository(applicationContext.triggerDataStore),
            // Arrière-plan : pas d'écran d'autorisation possible (voir SpotifyController).
            play = { uri -> spotify.connectAndPlay(uri, allowAuthUi = false) },
        )
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

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(
        text: String = "En attente de la connexion d'un appareil configuré.",
    ): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surveillance Bluetooth active")
            .setContentText(text)
            .setSmallIcon(com.bluetoothspotifytrigger.R.drawable.ic_bt_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Surveillance Bluetooth",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notification persistante du service de détection Bluetooth."
                },
            )
            // Canal séparé, plus visible : les échecs de lecture doivent se voir.
            manager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Échecs de lecture",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Prévient quand Spotify n'a pas pu être lancé."
                },
            )
        }
    }

    companion object {
        const val TAG = "BluetoothMonitor"
        private const val CHANNEL_ID = "bt_monitor"
        private const val ALERT_CHANNEL_ID = "playback_alerts"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2

        /** Démarre le service et mémorise le choix (relance auto au boot / après MAJ). */
        fun start(context: Context) {
            AppPrefs(context).serviceEnabled = true
            ContextCompat.startForegroundService(
                context,
                Intent(context, BluetoothMonitorService::class.java),
            )
        }

        /** Arrête le service ; il ne sera plus relancé automatiquement. */
        fun stop(context: Context) {
            AppPrefs(context).serviceEnabled = false
            context.stopService(Intent(context, BluetoothMonitorService::class.java))
        }
    }
}
