package com.kroombox.konsol.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import com.kroombox.konsol.data.Host
import com.kroombox.konsol.data.Vault
import com.kroombox.konsol.data.newId
import com.kroombox.konsol.service.SessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pusat semua sesi: koneksi SSH dipakai bersama per host, tab terminal, port forward,
 * serta foreground service yang menjaga koneksi tetap hidup di latar belakang.
 */
class SessionManager(private val context: Context, val vault: Vault) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val connections = HashMap<String, SshConnection>()
    private val hostLocks = HashMap<String, Any>()
    private val users = HashMap<String, MutableSet<String>>()

    val forwards = PortForwardManager(this)

    /** Naik setiap ada perubahan status sesi; UI meng-collect ini untuk menggambar ulang indikator. */
    private val _stateVersion = MutableStateFlow(0L)
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        registerNetworkCallback()
    }

    fun latestHost(id: String): Host? = vault.host(id)

    private fun lockFor(hostId: String): Any = synchronized(hostLocks) { hostLocks.getOrPut(hostId) { Any() } }

    /**
     * Ambil koneksi tersambung untuk host (sambungkan bila perlu). Blocking — panggil dari thread IO.
     */
    fun connectionFor(hostId: String, depth: Int = 0): SshConnection {
        if (depth > 3) throw SshException("Rantai jump host terlalu panjang")
        val host = vault.host(hostId) ?: throw SshException("Host tidak ada")
        synchronized(lockFor(hostId)) {
            val existing = synchronized(connections) { connections[hostId] }
            if (existing != null && existing.isConnected) return existing
            existing?.close()
            if (existing != null) forwards.onHostDisconnected(hostId)
            val jump = host.jumpHostId?.takeIf { it != hostId }?.let { connectionFor(it, depth + 1) }
            val conn = SshConnection(host, vault, jump)
            conn.connect()
            synchronized(connections) { connections[hostId] = conn }
            vault.touchHost(hostId)
            return conn
        }
    }

    fun existingConnection(hostId: String): SshConnection? = synchronized(connections) { connections[hostId] }

    /** Tandai bahwa sebuah fitur (sftp/monitor/forward) memakai koneksi host. */
    fun acquire(hostId: String, tag: String) {
        synchronized(users) { users.getOrPut(hostId) { mutableSetOf() }.add(tag) }
    }

    fun release(hostId: String, tag: String) {
        synchronized(users) { users[hostId]?.remove(tag) }
        maybeDisconnect(hostId)
    }

    /** Hapus penanda pemakaian tanpa memutus koneksi (dipakai saat koneksi sedang dibuat ulang). */
    internal fun dropUser(hostId: String, tag: String) {
        synchronized(users) { users[hostId]?.remove(tag) }
    }

    private fun maybeDisconnect(hostId: String, depth: Int = 0) {
        val inUse = synchronized(users) { users[hostId]?.isNotEmpty() == true } ||
            _sessions.value.any { it.host.id == hostId && it.state.value != SessionState.CLOSED } ||
            vault.current.hosts.any { it.jumpHostId == hostId && existingConnection(it.id)?.isConnected == true }
        if (!inUse) {
            val c = synchronized(connections) { connections.remove(hostId) }
            if (c != null) scope.launch(Dispatchers.IO) { c.close() }
            // Lepaskan juga jump host bila tidak dipakai lagi.
            if (depth < 3) vault.host(hostId)?.jumpHostId?.takeIf { it != hostId }?.let { maybeDisconnect(it, depth + 1) }
        }
        refreshService()
    }

    // ------------------------------------------------------------------ Terminal
    fun openTerminal(hostId: String, tmuxOverride: String? = null, forcePlain: Boolean = false): TerminalSession? {
        val host = vault.host(hostId) ?: return null
        val tmux = when {
            forcePlain -> null
            tmuxOverride != null -> Tmux.sanitize(tmuxOverride)
            host.persistentSession -> Tmux.sanitize(host.tmuxSessionName)
            else -> null
        }
        val s = TerminalSession(newId(), host, tmux, this, vault.current.settings.scrollbackLines, scope)
        _sessions.update { it + s }
        s.start()
        refreshService()
        return s
    }

    fun session(id: String?): TerminalSession? = _sessions.value.firstOrNull { it.id == id }

    /** Tutup tab. Sesi tmux di server tetap berjalan kecuali [killTmux] = true. */
    fun closeTerminal(id: String, killTmux: Boolean = false) {
        val s = session(id) ?: return
        s.close()
        _sessions.update { list -> list.filterNot { it.id == id } }
        if (killTmux && s.tmuxName != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    existingConnection(s.host.id)?.takeIf { it.isConnected }
                        ?.exec("sh -s", stdin = Tmux.killCommand(s.tmuxName))
                } catch (_: Exception) {
                }
                maybeDisconnect(s.host.id)
            }
        } else {
            maybeDisconnect(s.host.id)
        }
    }

    fun closeAll() {
        _sessions.value.forEach { it.close() }
        _sessions.value = emptyList()
        scope.launch(Dispatchers.IO) { forwards.stopAll() }
        val all = synchronized(connections) { connections.values.toList().also { connections.clear() } }
        scope.launch(Dispatchers.IO) { all.forEach { it.close() } }
        refreshService()
    }

    internal fun onSessionStateChanged() {
        _stateVersion.update { it + 1 }
    }

    internal fun onSessionConnected(@Suppress("UNUSED_PARAMETER") s: TerminalSession) = refreshService()
    internal fun onSessionEnded(@Suppress("UNUSED_PARAMETER") s: TerminalSession) = refreshService()

    // ------------------------------------------------------------------ Tmux jarak jauh
    suspend fun listTmux(hostId: String): List<Tmux.RemoteSession>? = withContext(Dispatchers.IO) {
        val conn = connectionFor(hostId)
        acquire(hostId, "tmux-list")
        try {
            Tmux.parseList(conn.exec("sh -s", stdin = Tmux.LIST_SCRIPT).stdout)
        } finally {
            release(hostId, "tmux-list")
        }
    }

    suspend fun killTmux(hostId: String, name: String) = withContext(Dispatchers.IO) {
        val conn = connectionFor(hostId)
        conn.exec("sh -s", stdin = Tmux.killCommand(name))
        Unit
    }

    // ------------------------------------------------------------------ Lain-lain
    fun copyToClipboard(text: String) {
        mainHandler.post {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }
    }

    val activeSessionCount: Int
        get() = _sessions.value.count { it.state.value == SessionState.CONNECTED || it.state.value == SessionState.RECONNECTING || it.state.value == SessionState.CONNECTING }

    fun refreshService() {
        val needed = activeSessionCount > 0 || forwards.activeCount > 0
        mainHandler.post {
            if (needed) SessionService.start(context) else SessionService.stop(context)
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Jaringan kembali / berganti: percepat sambung ulang.
                    _sessions.value.filter { it.state.value == SessionState.RECONNECTING }.forEach { it.reconnectNow() }
                }
            })
        } catch (_: Exception) {
        }
    }
}
