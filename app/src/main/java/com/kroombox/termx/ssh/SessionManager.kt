package com.kroombox.termx.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import com.kroombox.termx.data.Host
import com.kroombox.termx.data.SavedSession
import com.kroombox.termx.data.SecretBox
import com.kroombox.termx.data.Vault
import com.kroombox.termx.data.newId
import com.kroombox.termx.os.OsCatalog
import com.kroombox.termx.service.SessionService
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

    val history = SessionHistory(context)
    val commandHistory = CommandHistory(context)

    /** Saran autocomplete untuk host dan awal baris. */
    fun suggestions(hostId: String, prefix: String): List<String> =
        Autocomplete.suggest(prefix, commandHistory.list(hostId), vault.current.snippets.map { it.command })

    /** Diset dari notifikasi ("Putuskan semua…"); UI menampilkan konfirmasi. */
    val disconnectRequested = MutableStateFlow(false)

    /** Naik setiap ada perubahan status sesi; UI meng-collect ini untuk menggambar ulang indikator. */
    private val _stateVersion = MutableStateFlow(0L)
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        registerNetworkCallback()
        startHistorySaver()
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
            // OS dideteksi sekali saja (koneksi pertama) lalu disimpan sebagai penentu ikon; berikutnya
            // tidak perlu dideteksi lagi kecuali diminta ulang dari editor host.
            if (vault.host(hostId)?.osId == null) detectOs(conn)
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
    /**
     * Buka tab terminal. Terminal baru (dari tab Host) selalu kosong; riwayat hanya dimuat bila
     * [loadHistory] — membuka lagi sesi dari tab Sesi atau memulihkan tab setelah proses dimatikan.
     */
    fun openTerminal(
        hostId: String,
        tmuxOverride: String? = null,
        forcePlain: Boolean = false,
        historyKey: String? = null,
        loadHistory: Boolean = false,
    ): TerminalSession? {
        val host = vault.host(hostId) ?: return null
        val tmux = when {
            forcePlain -> null
            tmuxOverride != null -> Tmux.sanitize(tmuxOverride)
            host.persistentSession -> Tmux.sanitize(host.tmuxSessionName)
            else -> null
        }
        val sid = newId()
        val s = TerminalSession(sid, host, tmux, this, vault.current.settings.scrollbackLines, scope)
        s.historyKey = historyKey ?: if (tmux != null) history.key(host.id, tmux) else history.plainKey(host.id, sid)
        // Riwayat sebelumnya hanya untuk sesi yang dibuka lagi (bukan terminal baru dari host).
        if (loadHistory) history.load(s.historyKey)?.let { saved ->
            s.emulator.resize(saved.cols, saved.rows)
            s.emulator.append(saved.text)
            s.emulator.append("\r\n\u001b[2m── riwayat sebelumnya ──\u001b[0m\r\n")
        }
        s.savedVersion = s.screenVersion.get()
        _sessions.update { it + s }
        s.start()
        persistOpenSessions()
        refreshService()
        return s
    }

    /**
     * Buka lagi tab yang masih aktif saat proses aplikasi terakhir kali dimatikan Android. Sesi tmux
     * di server tidak pernah berhenti, jadi tab langsung attach ke sesi yang sama.
     */
    fun restoreSessions() {
        val d0 = vault.current
        history.prune((d0.openSessions + d0.recentSessions).mapNotNull { it.historyKey }.toSet())
        val saved = d0.openSessions
        if (saved.isEmpty() || _sessions.value.isNotEmpty()) return
        saved.forEach { ss ->
            if (vault.host(ss.hostId) == null) return@forEach
            openTerminal(
                ss.hostId, tmuxOverride = ss.tmuxName, forcePlain = ss.tmuxName == null,
                historyKey = ss.historyKey, loadHistory = true,
            )
        }
    }

    /** Simpan daftar tab yang masih hidup (bukan yang sudah selesai/ditutup). */
    private fun persistOpenSessions() {
        val list = _sessions.value
            .filter { it.state.value == SessionState.CONNECTING || it.state.value == SessionState.CONNECTED || it.state.value == SessionState.RECONNECTING }
            .map { SavedSession(it.host.id, it.tmuxName, historyKey = it.historyKey) }
        if (list != vault.current.openSessions) vault.update { it.copy(openSessions = list) }
    }

    fun session(id: String?): TerminalSession? = _sessions.value.firstOrNull { it.id == id }

    /** Tutup tab. Sesi tmux di server tetap berjalan kecuali [killTmux] = true. */
    fun closeTerminal(id: String, killTmux: Boolean = false) {
        val s = session(id) ?: return
        // Sesi tmux yang diakhiri dengan sengaja tidak perlu riwayat; selain itu simpan untuk dibuka lagi.
        if (killTmux) history.delete(s.historyKey) else saveHistory(s, force = true)
        rememberRecent(s, ended = killTmux)
        s.close()
        _sessions.update { list -> list.filterNot { it.id == id } }
        persistOpenSessions()
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
        _sessions.value.forEach { saveHistory(it, force = true); rememberRecent(it, ended = false); it.close() }
        _sessions.value = emptyList()
        vault.update { it.copy(openSessions = emptyList()) }
        scope.launch(Dispatchers.IO) { forwards.stopAll() }
        val all = synchronized(connections) { connections.values.toList().also { connections.clear() } }
        scope.launch(Dispatchers.IO) { all.forEach { it.close() } }
        refreshService()
    }

    internal fun onSessionStateChanged() {
        _stateVersion.update { it + 1 }
        persistOpenSessions()
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

    // ------------------------------------------------------------------ Baru ditutup
    private fun sameSession(a: SavedSession, key: String?, hostId: String, tmux: String?): Boolean =
        if (a.historyKey != null && key != null) a.historyKey == key else a.hostId == hostId && a.tmuxName == tmux

    private fun rememberRecent(s: TerminalSession, ended: Boolean) {
        var dropped: List<SavedSession> = emptyList()
        vault.update { d ->
            val rest = d.recentSessions.filterNot { sameSession(it, s.historyKey, s.host.id, s.tmuxName) }
            val entry = SavedSession(s.host.id, s.tmuxName, System.currentTimeMillis(), historyKey = s.historyKey)
            val all = (if (ended) rest else listOf(entry) + rest).filter { r -> d.hosts.any { it.id == r.hostId } }
            dropped = all.drop(8)
            d.copy(recentSessions = all.take(8))
        }
        // Riwayat terminal biasa yang keluar dari daftar tidak bisa dibuka lagi; hapus filenya.
        dropped.mapNotNull { it.historyKey }.filter { history.isPlain(it) }.forEach { history.delete(it) }
    }

    fun forgetRecent(r: SavedSession) {
        vault.update { d -> d.copy(recentSessions = d.recentSessions.filterNot { sameSession(it, r.historyKey, r.hostId, r.tmuxName) }) }
        r.historyKey?.takeIf { history.isPlain(it) }?.let { history.delete(it) }
    }

    // ------------------------------------------------------------------ Riwayat layar
    private fun saveHistory(s: TerminalSession, force: Boolean = false) {
        val v = s.screenVersion.get()
        if (!force && v == s.savedVersion) return
        val text = s.emulator.snapshot()
        val cols = s.emulator.cols
        val rows = s.emulator.rows
        s.savedVersion = v
        scope.launch(Dispatchers.IO) { history.save(s.historyKey, cols, rows, text) }
    }

    /** Simpan riwayat semua tab sekarang (dipanggil saat aplikasi ke latar belakang). */
    fun saveAllHistory() {
        _sessions.value.forEach { saveHistory(it) }
    }

    private fun startHistorySaver() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                try { saveAllHistory() } catch (_: Exception) {}
            }
        }
    }

    // ------------------------------------------------------------------ OS & sudo
    /** Deteksi OS server sekali per koneksi, lalu simpan ke host (untuk logo OS di daftar host). */
    /** Dipanggil di thread IO saat koneksi dibuat; singkat (skrip read-only, batas 8 detik). */
    private fun detectOs(conn: SshConnection) {
        try {
            val r = conn.exec("sh -s", timeoutSec = 8, stdin = OsCatalog.DETECT_SCRIPT)
            val d = OsCatalog.parse(r.stdout, r.stderr) ?: return
            val h = vault.host(conn.host.id) ?: return
            if (h.osId != d.id || h.osName != d.name) vault.saveHost(h.copy(osId = d.id, osName = d.name))
        } catch (_: Exception) {
        }
    }

    /** Password sudo tersimpan untuk host: password sudo khusus, atau password login bila diizinkan. */
    fun storedSudoPassword(hostId: String): String? {
        val h = vault.host(hostId) ?: return null
        SecretBox.decryptOrNull(h.sudoPasswordEnc)?.let { return it }
        if (h.sudoUseLoginPassword) SecretBox.decryptOrNull(h.passwordEnc)?.let { return it }
        return null
    }

    /**
     * Password sudo untuk host. Bila belum tersimpan dan [ask] true, tanyakan ke pengguna (dialog)
     * dan simpan terenkripsi bila diminta.
     */
    suspend fun sudoPassword(hostId: String, ask: Boolean = true): String? {
        storedSudoPassword(hostId)?.let { return it }
        if (!ask) return null
        val h = vault.host(hostId) ?: return null
        val ans = Prompts.askPassword("Password sudo · ${h.label}", "sudo untuk ${h.username}@${h.hostname}") ?: return null
        if (ans.save) {
            vault.host(hostId)?.let { vault.saveHost(it.copy(sudoPasswordEnc = SecretBox.encrypt(ans.password))) }
        }
        return ans.password
    }

    /** Lupakan password sudo tersimpan (mis. setelah ditolak server). */
    fun forgetSudoPassword(hostId: String) {
        vault.host(hostId)?.let { vault.saveHost(it.copy(sudoPasswordEnc = null)) }
    }

    /**
     * Jalankan skrip sebagai root dengan sudo, menanyakan password sekali bila diperlukan. Password
     * yang ditolak server dilupakan agar ditanya ulang lain kali.
     */
    suspend fun execSudo(hostId: String, script: String, timeoutSec: Long = 20): ExecResult = withContext(Dispatchers.IO) {
        val conn = connectionFor(hostId)
        try {
            conn.execSudo(script, storedSudoPassword(hostId), timeoutSec)
        } catch (e: SshConnection.SudoPasswordRequired) {
            val pw = sudoPassword(hostId, ask = true) ?: throw SshException("Password sudo dibatalkan")
            try {
                conn.execSudo(script, pw, timeoutSec)
            } catch (e2: SshException) {
                if (e2.message?.contains("salah") == true) forgetSudoPassword(hostId)
                throw e2
            }
        } catch (e: SshException) {
            if (e.message?.contains("salah") == true) forgetSudoPassword(hostId)
            throw e
        }
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

    @Volatile private var lastNetwork: Network? = null
    @Volatile private var networkLost = false

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val changed = lastNetwork != null && (network != lastNetwork || networkLost)
                    lastNetwork = network
                    networkLost = false
                    if (changed) {
                        // Jaringan berganti (Wi-Fi <-> data, VPN tersambung ulang, atau kembali setelah hilang).
                        // Koneksi TIDAK diputus begitu saja: lewat VPN seperti Tailscale alamatnya tetap sama
                        // dan koneksi TCP masih hidup. Setiap koneksi diperiksa; hanya yang tidak membalas
                        // dalam 8 detik yang ditutup lalu tersambung ulang.
                        val all = synchronized(connections) { connections.values.toList() }
                        all.forEach { conn ->
                            scope.launch(Dispatchers.IO) { if (!conn.probe(8)) runCatching { conn.close() } }
                        }
                    }
                    // Jaringan kembali / berganti: percepat sambung ulang.
                    _sessions.value.filter { it.state.value == SessionState.RECONNECTING }.forEach { it.reconnectNow() }
                }

                override fun onLost(network: Network) {
                    if (network == lastNetwork) networkLost = true
                }
            })
        } catch (_: Exception) {
        }
    }
}
