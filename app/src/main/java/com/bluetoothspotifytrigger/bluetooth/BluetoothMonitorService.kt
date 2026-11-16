package com.bluetoothspotifytrigger.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bluetoothspotifytrigger.BuildConfig
import com.bluetoothspotifytrigger.data.TriggerRepository
import com.bluetoothspotifytrigger.data.local.AppPrefs
import com.bluetoothspotifytrigger.data.local.triggerDataStore
import com.bluetoothspotifytrigger.debug.DebugActions
import com.bluetoothspotifytrigger.spotify.PlayResult
import com.bluetoothspotifytrigger.spotify.describe
import com.bluetoothspotifytrigger.spotify.SpotifyIntentPlayer
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
 * Les events Bluetooth passent par le [ConnectionCoordinator] (attente de
 * l'audio A2DP, anti-doublons), puis au [TriggerHandler] qui cherche l'URI
 * Spotify associé à l'appareil et lance la lecture.
 */
class BluetoothMonitorService : Service() {

    // Scope lié à la vie du service : annulé dans onDestroy. SupervisorJob pour
    // qu'un échec de traitement d'une connexion n'empêche pas les suivantes.
    // Thread principal : le coordinateur n'est pas thread-safe, et les receivers
    // sont appelés sur Main. Les traitements longs sont suspendants (pas bloquants).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var triggerHandler: TriggerHandler

    /** Attend l'audio A2DP avant de déclencher (voir ConnectionCoordinator). */
    private val coordinator = ConnectionCoordinator(
        scope = scope,
        clock = SystemClock::elapsedRealtime,
    ) { address, name ->
        runCatching { triggerHandler.onDeviceConnected(address, name) }
            .onSuccess { reportOutcome(name, it) }
            .onFailure { Log.e(TAG, "Erreur lors du traitement de $address", it) }
    }

    private val onBtEvent: (BtConnectionReceiver.Event, String, String) -> Unit =
        { event, address, name ->
            when (event) {
                BtConnectionReceiver.Event.ACL -> coordinator.onAclConnected(address, name)
                BtConnectionReceiver.Event.A2DP -> coordinator.onA2dpConnected(address, name)
            }
        }

    /** Met à jour la notification persistante ; alerte l'utilisateur en cas d'échec. */
    private fun reportOutcome(deviceName: String, outcome: TriggerHandler.Outcome) {
        if (outcome !is TriggerHandler.Outcome.Played) return
        val time = DateFormat.getTimeFormat(this).format(Date())
        val ok = outcome.result == PlayResult.Success
        val summary = "$time · $deviceName → ${outcome.result.describe()}"
        ServiceState.setLastEvent(summary)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text = "Dernier : $summary"))
        if (!ok) {
            manager.notify(
                ALERT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setSmallIcon(com.bluetoothspotifytrigger.R.drawable.ic_bt_notification)
                    .setContentTitle("Lecture Spotify impossible ($deviceName)")
                    .setContentText(outcome.result.describe())
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(outcome.result.describe()),
                    )
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    // Deux instances : la vraie écoute système, et (debug uniquement) la
    // simulation adb protégée par la permission DUMP.
    private val receiver = BtConnectionReceiver(onBtEvent)
    private val debugReceiver = BtConnectionReceiver(onBtEvent)
    private val registeredReceivers = mutableListOf<BtConnectionReceiver>()

    override fun onCreate() {
        super.onCreate()
        val player = SpotifyIntentPlayer(this)
        triggerHandler = TriggerHandler(
            repository = TriggerRepository(applicationContext.triggerDataStore),
            // Volume et URI par appareil (voir DeviceConfig).
            play = { uri, config -> player.play(uri, config.volumePercent, config.shuffle) },
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
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        // Ces broadcasts sont émis par le SYSTÈME (un autre UID), pas par notre app.
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
                IntentFilter().apply {
                    addAction(DebugActions.SIMULATE_ACL_CONNECTED)
                    addAction(DebugActions.SIMULATE_A2DP_CONNECTED)
                },
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
