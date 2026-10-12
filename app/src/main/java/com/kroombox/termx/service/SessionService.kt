package com.kroombox.termx.service

import com.kroombox.termx.i18n.S
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
import com.kroombox.termx.TermXApp
import com.kroombox.termx.MainActivity
import com.kroombox.termx.R
import com.kroombox.termx.ssh.SessionState
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
            TermXApp.container.sessions.closeAll()
            running = false
            stopSelfSafely()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        // startForeground wajib dipanggil walau akan langsung berhenti (aturan startForegroundService).
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        } catch (e: Exception) {
            // Android 12+ bisa menolak foreground service yang dimulai ulang dari latar belakang
            // (mis. setelah proses dimatikan, bila aplikasi belum dikecualikan dari optimasi baterai).
            // Sesi tetap tersimpan dan dipulihkan saat aplikasi dibuka.
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        foregroundStarted = true
        running = true
        val mgr = TermXApp.container.sessions
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
                    // Diperbarui berkala; sekaligus memunculkan lagi notifikasi bila sempat diusap.
                    postNotification()
                    delay(5000)
                }
            }
        }
        if (intent?.action == ACTION_REPOST) postNotification()
        // STICKY: bila Android mematikan proses, service dijalankan ulang; TermXApp memulihkan sesi
        // yang tersimpan sehingga koneksi hanya berakhir saat pengguna memutuskannya.
        return START_STICKY
    }

    /**
     * Aplikasi ditutup dari daftar aplikasi terbaru. Sesi tetap berjalan (foreground service
     * menahan proses), notifikasi tetap tampil. Sebagian merek HP tetap mematikan prosesnya; untuk
     * itu service dijadwalkan hidup lagi sebentar kemudian, dan TermXApp memulihkan semua tab.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try { TermXApp.container.sessions.saveAllHistory() } catch (_: Exception) {}
        postNotification()
        scheduleRestart(this, 3_000)
    }

    private fun postNotification() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun acquireLocks() {
        val keepAwake = TermXApp.container.vault.current.settings.keepAliveInBackground
        if (!keepAwake) {
            releaseLocks()
            return
        }
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TermX::ssh").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TermX::ssh").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        try { wakeLock?.release() } catch (_: Exception) {}
        try { wifiLock?.release() } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    private fun buildNotification(): Notification {
        val mgr = TermXApp.container.sessions
        val sessions = mgr.sessions.value
        val live = sessions.filter { it.state.value != SessionState.CLOSED && it.state.value != SessionState.ENDED && it.state.value != SessionState.FAILED }
        val connected = live.count { it.state.value == SessionState.CONNECTED }
        val reconnecting = live.count { it.state.value == SessionState.RECONNECTING || it.state.value == SessionState.CONNECTING }
        val fwd = mgr.forwards.activeCount
        val parts = mutableListOf<String>()
        if (connected > 0) parts += S.get(R.string.svc_n_active, connected)
        if (reconnecting > 0) parts += S.get(R.string.svc_n_reconnecting, reconnecting)
        if (fwd > 0) parts += S.get(R.string.svc_n_forwards, fwd)
        val sftpCount = mgr.sftpSessions.value.size
        if (sftpCount > 0) parts += "$sftpCount SFTP"
        val title = parts.joinToString(" · ").ifEmpty { S.get(R.string.svc_keeping_connection) }
        val hosts = live.map { it.host.label }.distinct().joinToString(", ")

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        live.take(6).forEach { s ->
            val st = when (s.state.value) {
                SessionState.CONNECTED -> S.get(R.string.svc_state_connected)
                SessionState.RECONNECTING -> S.get(R.string.svc_state_reconnecting)
                SessionState.CONNECTING -> S.get(R.string.svc_state_connecting)
                else -> s.state.value.name.lowercase()
            }
            style.addLine("● ${s.host.label}${s.tmuxName?.let { " · tmux:$it" } ?: ""} — $st")
        }
        mgr.sftpSessions.value.take(4).forEach { f ->
            val label = TermXApp.container.vault.host(f.hostId)?.label ?: "?"
            style.addLine("● $label — SFTP ${f.path.ifEmpty { "~" }}")
        }
        style.setSummaryText(
            (if (live.size > 6) S.get(R.string.svc_n_more, live.size - 6) else "") + S.get(R.string.svc_stays_active),
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Memutus semua sesi adalah tindakan pengguna yang tidak bisa dibatalkan, jadi tombol di
        // notifikasi membuka aplikasi untuk konfirmasi lebih dulu.
        val disconnect = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_CONFIRM_DISCONNECT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Android 14+ mengizinkan notifikasi foreground service diusap; munculkan lagi segera.
        val repost = PendingIntent.getService(
            this, 2, Intent(this, SessionService::class.java).setAction(ACTION_REPOST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setColor(0xFF3DDC97.toInt())
            .setContentTitle(title)
            .setContentText(hosts.ifEmpty { S.get(R.string.svc_keeping_ssh) })
            .setStyle(style)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(repost)
            .addAction(0, S.get(R.string.svc_action_open), open)
            .addAction(0, S.get(R.string.svc_action_disconnect_all), disconnect)
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
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIF_ID = 42
        private const val ACTION_DISCONNECT_ALL = "com.kroombox.termx.DISCONNECT_ALL"
        private const val ACTION_REPOST = "com.kroombox.termx.REPOST"

        /** Awal sesi untuk penghitung waktu di notifikasi. */
        private var startedAt = System.currentTimeMillis()

        @Volatile
        private var running = false

        @Volatile
        private var foregroundStarted = false

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            // Dibuat ulang setiap start: membuat channel yang sudah ada hanya memperbarui nama/deskripsinya,
            // jadi nama channel mengikuti bahasa yang dipilih.
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, S.get(R.string.svc_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = S.get(R.string.svc_channel_desc)
                    setShowBadge(false)
                },
            )
        }

        /**
         * Jadwalkan service dimulai lagi (bila proses sempat dimatikan sistem). Aman bila service
         * masih hidup: onStartCommand hanya memperbarui notifikasi.
         */
        fun scheduleRestart(ctx: Context, delayMs: Long) {
            try {
                val am = ctx.getSystemService(android.app.AlarmManager::class.java) ?: return
                val pi = PendingIntent.getForegroundService(
                    ctx, 3, Intent(ctx, SessionService::class.java).setAction(ACTION_REPOST),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + delayMs, pi,
                )
            } catch (_: Exception) {
            }
        }

        fun start(ctx: Context) {
            if (running) return
            startedAt = System.currentTimeMillis()
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
