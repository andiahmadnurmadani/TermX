package com.kroombox.konsol.ssh

import com.kroombox.konsol.data.Host
import com.kroombox.konsol.terminal.TerminalEmulator
import com.kroombox.konsol.terminal.TerminalListener
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
                pump(s)
                // Shell selesai: bedakan keluar normal vs koneksi putus.
                val connAlive = conn.isConnected
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
            val retryable = everConnected && host.autoReconnect && !isFatal(failure)
            if (!retryable) {
                setState(SessionState.FAILED)
                emulator.append("\r\n\u001b[31m[Gagal: ${msg ?: "koneksi terputus"}]\u001b[0m\r\n".toByteArray())
                manager.onSessionEnded(this)
                return
            }
            setState(SessionState.RECONNECTING)
            if (attempt == 0) {
                emulator.append("\r\n\u001b[33m[Koneksi terputus — menyambung ulang otomatis…]\u001b[0m\r\n".toByteArray())
            }
            val delayMs = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
            attempt++
            withTimeoutOrNull(delayMs) { kick.receive() }
        }
    }

    private fun isFatal(e: Throwable?): Boolean {
        val m = e?.message ?: return false
        return m.contains("Autentikasi") || m.contains("Host key ditolak") || m.contains("dibatalkan") ||
            m.contains("Kunci SSH")
    }

    private fun pump(s: Session) {
        val input = s.inputStream
        val buf = ByteArray(16 * 1024)
        try {
            while (!closed) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) emulator.append(buf, 0, n)
            }
        } catch (_: IOException) {
        } catch (_: Exception) {
        }
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
