package com.kroombox.konsol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kroombox.konsol.KonsolApp
import com.kroombox.konsol.MainActivity
import com.kroombox.konsol.R
import com.kroombox.konsol.ssh.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service yang menjaga koneksi SSH dan port forward tetap hidup ketika aplikasi
 * di latar belakang atau layar mati.
 */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updater: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            KonsolApp.container.sessions.closeAll()
            running = false
            stopSelfSafely()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        // startForeground wajib dipanggil walau akan langsung berhenti (aturan startForegroundService).
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        foregroundStarted = true
        val mgr = KonsolApp.container.sessions
        if (mgr.activeSessionCount == 0 && mgr.forwards.activeCount == 0) {
            // Diminta berhenti sebelum sempat jalan, atau dimulai ulang sistem tanpa sesi.
            running = false
            stopSelfSafely()
            return START_NOT_STICKY
        }
        acquireLocks()
        if (updater?.isActive != true) {
            updater = scope.launch {
                while (isActive) {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIF_ID, buildNotification())
                    delay(5000)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        val keepAwake = KonsolApp.container.vault.current.settings.keepScreenOn
        if (!keepAwake) return
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Konsol::ssh").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Konsol::ssh").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun buildNotification(): Notification {
        val mgr = KonsolApp.container.sessions
        val sessions = mgr.sessions.value
        val connected = sessions.count { it.state.value == SessionState.CONNECTED }
        val reconnecting = sessions.count { it.state.value == SessionState.RECONNECTING || it.state.value == SessionState.CONNECTING }
        val fwd = mgr.forwards.activeCount
        val parts = mutableListOf<String>()
        if (connected > 0) parts += "$connected sesi aktif"
        if (reconnecting > 0) parts += "$reconnecting menyambung ulang"
        if (fwd > 0) parts += "$fwd port forward"
        val text = parts.joinToString(" · ").ifEmpty { "Menjaga koneksi" }
        val hosts = sessions.map { it.host.label }.distinct().joinToString(", ")

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, SessionService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(text)
            .setContentText(hosts.ifEmpty { "Konsol" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Putuskan semua", disconnect)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        foregroundStarted = false
        updater?.cancel()
        scope.cancel()
        try { wakeLock?.release() } catch (_: Exception) {}
        try { wifiLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIF_ID = 42
        private const val ACTION_DISCONNECT_ALL = "com.kroombox.konsol.DISCONNECT_ALL"

        @Volatile
        private var running = false

        @Volatile
        private var foregroundStarted = false

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Sesi SSH aktif", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Menampilkan sesi SSH dan port forward yang sedang berjalan"
                        setShowBadge(false)
                    },
                )
            }
        }

        fun start(ctx: Context) {
            if (running) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, SessionService::class.java))
                running = true
            } catch (_: Exception) {
                // Android 12+ bisa menolak start dari latar belakang; akan dicoba lagi saat aplikasi aktif.
            }
        }

        fun stop(ctx: Context) {
            if (!running) return
            // Bila onStartCommand belum jalan, biarkan service menghentikan dirinya sendiri setelah
            // startForeground (menghindari crash "did not then call startForeground").
            if (!foregroundStarted) return
            running = false
            ctx.stopService(Intent(ctx, SessionService::class.java))
        }
    }
}
