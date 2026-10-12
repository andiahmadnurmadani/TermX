package com.kroombox.termx.ssh

import com.kroombox.termx.data.Host
import com.kroombox.termx.terminal.TerminalEmulator
import com.kroombox.termx.terminal.TerminalListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

enum class SessionState { CONNECTING, CONNECTED, RECONNECTING, ENDED, FAILED, CLOSED }

/**
 * Satu tab terminal. Bila host memakai sesi persisten, shell berjalan di dalam tmux di server sehingga
 * pekerjaan (mis. Claude Code, build panjang) tetap jalan walau HP terputus; saat tersambung lagi
 * aplikasi otomatis attach ke sesi tmux yang sama.
 */
class TerminalSession(
    val id: String,
    initialHost: Host,
    /** Nama sesi tmux; null = shell biasa (tidak persisten). */
    val tmuxName: String?,
    private val manager: SessionManager,
    scrollback: Int,
    private val scope: CoroutineScope,
) {
    var host: Host = initialHost
        private set

    private val _state = MutableStateFlow(SessionState.CONNECTING)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _title = MutableStateFlow(initialHost.label)
    val title: StateFlow<String> = _title.asStateFlow()

    /** Bertambah setiap ada output; UI memakainya untuk menggambar ulang. */
    val screenVersion = AtomicLong(0)

    @Volatile
    var onScreenUpdate: (() -> Unit)? = null
    @Volatile
    var onBell: (() -> Unit)? = null

    val isPersistent: Boolean get() = tmuxName != null

    val emulator = TerminalEmulator(80, 24, scrollback, object : TerminalListener {
        override fun onResponse(bytes: ByteArray) = write(bytes)
        override fun onScreenChanged() {
            screenVersion.incrementAndGet()
            onScreenUpdate?.invoke()
        }
        override fun onTitleChanged(title: String) {
            if (title.isNotBlank()) _title.value = title
        }
        override fun onBell() { onBell?.invoke() }
        override fun onClipboardSet(text: String) = manager.copyToClipboard(text)
    })

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "term-writer-$id").apply { isDaemon = true } }

    @Volatile private var session: Session? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var closed = false
    @Volatile private var cols = 80
    @Volatile private var rows = 24
    private var job: Job? = null
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private var everConnected = false
    @Volatile private var forceRestart = false

    private fun setState(s: SessionState) {
        _state.value = s
        manager.onSessionStateChanged()
    }
    private var startupSent = false

    fun start() {
        if (job?.isActive == true) return
        closed = false
        job = scope.launch(Dispatchers.IO) { runLoop() }
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (!closed && scope.isActive) {
            setState(if (attempt == 0 && !everConnected) SessionState.CONNECTING else SessionState.RECONNECTING)
            var failure: Throwable? = null
            try {
                host = manager.latestHost(host.id) ?: host
                val conn = manager.connectionFor(host.id)
                val command = tmuxName?.let { Tmux.attachCommand(it, host.tmuxMouse, host.startupCommand) }
                val (s, _) = conn.openShell(cols, rows, command)
                session = s
                output = s.outputStream
                setState(SessionState.CONNECTED)
                _error.value = null
                if (everConnected) emulator.append("\r\n\u001b[32m[Tersambung kembali]\u001b[0m\r\n".toByteArray())
                everConnected = true
                attempt = 0
                manager.onSessionConnected(this)
                if (tmuxName == null && !startupSent && host.startupCommand.isNotBlank()) {
                    startupSent = true
                    write((host.startupCommand.trimEnd() + "\r").toByteArray())
                }
                val eof = pump(s)
                // Shell selesai: bedakan keluar normal (EOF dari server) vs koneksi putus (I/O error).
                val connAlive = eof && conn.isConnected
                if (closed) break
                if (forceRestart) {
                    forceRestart = false
                    cleanupChannel()
                    attempt = 0
                    continue
                }
                if (connAlive) {
                    setState(SessionState.ENDED)
                    val code = (s as? Session.Command)?.exitStatus
                    emulator.append("\r\n\u001b[2m[Sesi selesai${if (code != null) " (kode $code)" else ""}]\u001b[0m\r\n".toByteArray())
                    cleanupChannel()
                    manager.onSessionEnded(this)
                    return
                }
            } catch (e: Throwable) {
                failure = e
            }
            cleanupChannel()
            if (closed) break

            val msg = failure?.let { (it as? SshException)?.message ?: it.message ?: it.javaClass.simpleName }
            _error.value = msg
            // Sesi tidak pernah dianggap selesai karena jaringan: selama pengguna belum menutup tab,
            // koneksi terus dicoba lagi (termasuk bila percobaan pertama gagal karena sedang offline).
            // Hanya kegagalan yang butuh tindakan pengguna (password/kunci/host key) yang berhenti.
            val retryable = host.autoReconnect && !isFatal(failure)
            if (!retryable) {
                setState(SessionState.FAILED)
                emulator.append("\r\n\u001b[31m[Gagal: ${msg ?: "koneksi terputus"}]\u001b[0m\r\n".toByteArray())
                manager.onSessionEnded(this)
                return
            }
            setState(SessionState.RECONNECTING)
            if (attempt == 0) {
                val text = if (everConnected) "Koneksi terputus — menyambung ulang otomatis…" else "Gagal tersambung (${msg ?: "?"}) — mencoba lagi…"
                emulator.append("\r\n\u001b[33m[$text]\u001b[0m\r\n".toByteArray())
            }
            // 1, 2, 4, 8, lalu tiap 15 detik — tanpa batas sampai pengguna memutuskan.
            val delayMs = (1000L shl attempt.coerceAtMost(4)).coerceAtMost(15_000L)
            attempt++
            withTimeoutOrNull(delayMs) { kick.receive() }
        }
    }

    private fun isFatal(e: Throwable?): Boolean {
        val m = e?.message ?: return false
        return m.contains("Autentikasi") || m.contains("Host key ditolak") || m.contains("dibatalkan") ||
            m.contains("Kunci SSH")
    }

    /** Salin output ke emulator. Mengembalikan true bila berakhir karena EOF (shell keluar normal). */
    private fun pump(s: Session): Boolean {
        val input = s.inputStream
        val buf = ByteArray(16 * 1024)
        try {
            while (!closed) {
                val n = input.read(buf)
                if (n < 0) return true
                if (n > 0) {
                    emulator.append(buf, 0, n)
                    watchSudoPrompt(buf, n)
                }
            }
        } catch (_: IOException) {
        } catch (_: Exception) {
        }
        return false
    }

    // ------------------------------------------------------------------ Snippet & sudo
    @Volatile private var sudoArmedUntil = 0L
    private val recent = StringBuilder()

    /**
     * Jalankan snippet di tab ini. Dengan [sudo], perintah dibungkus `sudo …`; bila sudo lalu
     * menampilkan prompt password (dalam 20 detik), password sudo host diketik otomatis — hanya
     * setelah prompt-nya benar-benar muncul.
     */
    fun runCommand(command: String, autoRun: Boolean, sudo: Boolean) {
        val cmd = if (sudo) Sudo.wrap(command) else command
        if (sudo && autoRun) {
            synchronized(recent) { recent.setLength(0) }
            sudoArmedUntil = System.currentTimeMillis() + 20_000
        }
        write((cmd + if (autoRun) "\r" else "").toByteArray())
    }

    private fun watchSudoPrompt(buf: ByteArray, n: Int) {
        if (sudoArmedUntil == 0L) return
        if (System.currentTimeMillis() > sudoArmedUntil) { sudoArmedUntil = 0L; return }
        val tail = synchronized(recent) {
            recent.append(String(buf, 0, n, Charsets.UTF_8))
            if (recent.length > 512) recent.delete(0, recent.length - 512)
            recent.toString()
        }
        if (!Sudo.isPasswordPrompt(tail)) return
        sudoArmedUntil = 0L
        scope.launch(Dispatchers.IO) { typeSudoPassword() }
    }

    /** Ketik password sudo host ke terminal (ditanyakan dulu bila belum tersimpan). */
    suspend fun typeSudoPassword(): Boolean {
        val pw = manager.sudoPassword(host.id, ask = true) ?: return false
        write((pw + "\r").toByteArray())
        return true
    }

    private fun cleanupChannel() {
        val s = session
        session = null
        output = null
        try { s?.close() } catch (_: Exception) {}
    }

    fun write(bytes: ByteArray) {
        if (closed) return
        try {
            writer.execute {
                try {
                    val out = output ?: return@execute
                    out.write(bytes)
                    out.flush()
                } catch (_: Exception) {
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        if (closed || (newCols == cols && newRows == rows)) return
        cols = newCols
        rows = newRows
        val s = session ?: return
        try {
            writer.execute {
                try {
                    (s as? Session.Shell)?.changeWindowDimensions(newCols, newRows, 0, 0)
                } catch (_: Exception) {
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    /** Paksa coba sambung ulang sekarang (mis. jaringan kembali atau tombol "Sambung ulang"). */
    fun reconnectNow() {
        if (closed) return
        when (_state.value) {
            SessionState.RECONNECTING -> kick.trySend(Unit)
            SessionState.CONNECTED -> {
                // Paksa buka ulang channel (mis. layar macet); tmux tetap memegang proses di server.
                forceRestart = true
                scope.launch(Dispatchers.IO) { cleanupChannel() }
            }
            SessionState.ENDED, SessionState.FAILED -> {
                emulator.append("\r\n\u001b[2m[Menyambung ulang…]\u001b[0m\r\n".toByteArray())
                everConnected = true
                start()
            }
            else -> {}
        }
    }

    fun close() {
        closed = true
        setState(SessionState.CLOSED)
        job?.cancel()
        // Menutup channel = I/O jaringan; jangan di main thread.
        scope.launch(Dispatchers.IO) {
            cleanupChannel()
            writer.shutdown()
        }
    }
}
