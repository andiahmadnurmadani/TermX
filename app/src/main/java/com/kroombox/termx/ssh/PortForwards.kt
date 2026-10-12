package com.kroombox.termx.ssh

import com.kroombox.termx.data.ForwardRule
import com.kroombox.termx.data.ForwardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import java.io.Closeable
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

data class ForwardStatus(val running: Boolean, val error: String? = null, val connections: Int = 0)

/** Mengelola port forwarding lokal (-L), remote (-R), dan dinamis/SOCKS5 (-D). */
class PortForwardManager(private val manager: SessionManager) {
    private val _status = MutableStateFlow<Map<String, ForwardStatus>>(emptyMap())
    val status: StateFlow<Map<String, ForwardStatus>> = _status.asStateFlow()

    private val active = HashMap<String, Closeable>()

    val activeCount: Int get() = synchronized(active) { active.size }

    private fun set(id: String, s: ForwardStatus?) = _status.update { m -> if (s == null) m - id else m + (id to s) }

    /** Mulai forward. Dipanggil dari thread IO. */
    fun start(rule: ForwardRule) {
        stop(rule.id)
        try {
            val conn = manager.connectionFor(rule.hostId)
            manager.acquire(rule.hostId, "fwd:${rule.id}")
            val client = conn.client ?: throw SshException("Belum tersambung")
            val handle: Closeable = when (rule.type) {
                ForwardType.LOCAL -> startLocal(client, rule)
                ForwardType.REMOTE -> startRemote(client, rule)
                ForwardType.DYNAMIC -> startDynamic(client, rule)
            }
            synchronized(active) { active[rule.id] = handle }
            set(rule.id, ForwardStatus(true))
            manager.refreshService()
        } catch (e: Exception) {
            manager.release(rule.hostId, "fwd:${rule.id}")
            set(rule.id, ForwardStatus(false, e.message ?: e.javaClass.simpleName))
        }
    }

    /** Hentikan forward. Melakukan I/O jaringan — panggil dari thread IO. */
    fun stop(id: String) {
        val h = synchronized(active) { active.remove(id) }
        try { h?.close() } catch (_: Exception) {}
        if (h != null) {
            manager.vault.current.forwards.firstOrNull { it.id == id }?.let { manager.release(it.hostId, "fwd:$id") }
        }
        set(id, null)
        manager.refreshService()
    }

    fun stopAll() {
        val ids = synchronized(active) { active.keys.toList() }
        ids.forEach { stop(it) }
    }

    /** Dipanggil saat koneksi sebuah host putus: tandai forward-nya error. */
    fun onHostDisconnected(hostId: String) {
        val rules = manager.vault.current.forwards.filter { it.hostId == hostId }
        for (r in rules) {
            val h = synchronized(active) { active.remove(r.id) } ?: continue
            try { h.close() } catch (_: Exception) {}
            manager.dropUser(hostId, "fwd:${r.id}")
            set(r.id, ForwardStatus(false, "Koneksi terputus"))
        }
    }

    private fun bindServer(rule: ForwardRule): ServerSocket {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName(rule.bindHost.ifBlank { "127.0.0.1" }), rule.bindPort))
        return ss
    }

    private fun startLocal(client: SSHClient, rule: ForwardRule): Closeable {
        val ss = bindServer(rule)
        val params = Parameters(rule.bindHost, rule.bindPort, rule.destHost, rule.destPort)
        val fwd: LocalPortForwarder = client.newLocalPortForwarder(params, ss)
        val t = Thread({
            try {
                fwd.listen()
            } catch (e: Exception) {
                if (!ss.isClosed) set(rule.id, ForwardStatus(false, e.message))
            }
        }, "fwd-L-${rule.bindPort}")
        t.isDaemon = true
        t.start()
        return Closeable {
            try { fwd.close() } catch (_: Exception) {}
            try { ss.close() } catch (_: Exception) {}
        }
    }

    private fun startRemote(client: SSHClient, rule: ForwardRule): Closeable {
        val rpf: RemotePortForwarder = client.remotePortForwarder
        val bindAddr = rule.bindHost.ifBlank { "127.0.0.1" }
        val forward = rpf.bind(
            RemotePortForwarder.Forward(bindAddr, rule.bindPort),
            SocketForwardingConnectListener(InetSocketAddress(rule.destHost, rule.destPort)),
        )
        return Closeable { try { rpf.cancel(forward) } catch (_: Exception) {} }
    }

    private fun startDynamic(client: SSHClient, rule: ForwardRule): Closeable {
        val ss = bindServer(rule)
        val counter = AtomicInteger(0)
        val t = Thread({
            while (!ss.isClosed) {
                val sock = try { ss.accept() } catch (_: Exception) { break }
                Thread({ handleSocks(client, sock, rule, counter) }, "socks-conn").apply { isDaemon = true }.start()
            }
        }, "fwd-D-${rule.bindPort}")
        t.isDaemon = true
        t.start()
        return Closeable { try { ss.close() } catch (_: Exception) {} }
    }

    private fun handleSocks(client: SSHClient, sock: Socket, rule: ForwardRule, counter: AtomicInteger) {
        sock.use { s ->
            val input = DataInputStream(s.getInputStream())
            val out = s.getOutputStream()
            try {
                // Greeting
                if (input.readUnsignedByte() != 5) return
                val nMethods = input.readUnsignedByte()
                repeat(nMethods) { input.readUnsignedByte() }
                out.write(byteArrayOf(5, 0)); out.flush()
                // Request
                if (input.readUnsignedByte() != 5) return
                val cmd = input.readUnsignedByte()
                input.readUnsignedByte() // RSV
                val host = when (input.readUnsignedByte()) {
                    1 -> ByteArray(4).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress }
                    3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.let { String(it, Charsets.US_ASCII) }
                    4 -> ByteArray(16).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress }
                    else -> null
                }
                val port = input.readUnsignedShort()
                if (cmd != 1 || host == null) {
                    out.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)); out.flush()
                    return
                }
                val tunnel = try {
                    client.newDirectConnection(host, port)
                } catch (e: Exception) {
                    out.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)); out.flush()
                    return
                }
                out.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); out.flush()
                val n = counter.incrementAndGet()
                updateConnections(rule.id, n)
                try {
                    val up = Thread { pipe(input, tunnel.outputStream) }
                    up.isDaemon = true
                    up.start()
                    pipe(tunnel.inputStream, out)
                    up.join(1000)
                } finally {
                    try { tunnel.close() } catch (_: Exception) {}
                    updateConnections(rule.id, counter.decrementAndGet())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun updateConnections(id: String, n: Int) {
        _status.update { m -> m[id]?.let { m + (id to it.copy(connections = n)) } ?: m }
    }

    private fun pipe(from: InputStream, to: OutputStream) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = from.read(buf)
                if (n < 0) break
                to.write(buf, 0, n)
                to.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { to.close() } catch (_: Exception) {}
        }
    }
}
