package com.kroombox.termx.ssh

import com.kroombox.termx.i18n.S
import com.kroombox.termx.R
import com.kroombox.termx.data.AuthType
import com.kroombox.termx.data.Host
import com.kroombox.termx.data.KeyManager
import com.kroombox.termx.data.KnownHost
import com.kroombox.termx.data.OpenSshKeyFormat
import com.kroombox.termx.data.SecretBox
import com.kroombox.termx.data.StaticPasswordFinder
import com.kroombox.termx.data.Vault
import kotlinx.coroutines.runBlocking
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.keepalive.KeepAliveRunner
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.util.concurrent.TimeUnit

/**
 * Error koneksi yang ditampilkan ke pengguna. [fatal] = butuh tindakan pengguna (login/kunci/host key),
 * jadi sesi tidak disambung ulang otomatis. Ditentukan lewat flag, bukan isi pesan, agar berlaku di
 * semua bahasa.
 */
open class SshException(message: String, cause: Throwable? = null, val fatal: Boolean = false) : Exception(message, cause)

/** Password sudo ditolak server. */
class SudoWrongPasswordException(message: String) : SshException(message)

data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int?)

/**
 * Satu koneksi SSH ke sebuah host. Dipakai bersama oleh tab terminal, SFTP, monitor, dan port forward.
 */
class SshConnection(
    val host: Host,
    private val vault: Vault,
    private val jump: SshConnection? = null,
) {
    @Volatile
    var client: SSHClient? = null
        private set

    private var sftpClient: SFTPClient? = null
    private val sftpLock = Any()

    val isConnected: Boolean
        get() = client?.let { it.isConnected && it.isAuthenticated } == true

    /** Lakukan koneksi + autentikasi. Dipanggil dari thread IO. */
    fun connect() {
        close()
        val config = DefaultConfig().apply {
            keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
        }
        val c = SSHClient(config)
        c.connectTimeout = 15_000
        c.connection.keepAlive.keepAliveInterval = host.keepAliveSec.coerceIn(5, 300)
        // Tiga keep-alive tanpa balasan = koneksi mati, supaya sambung ulang dimulai lebih cepat.
        (c.connection.keepAlive as? KeepAliveRunner)?.maxAliveCount = 3
        c.addHostKeyVerifier(Verifier())
        // Dialog verifikasi host key berjalan di tengah key exchange; beri waktu cukup untuk pengguna.
        c.transport.timeoutMs = 125_000
        try {
            if (jump != null) {
                if (!jump.isConnected) jump.connect()
                val jc = jump.client ?: throw SshException(S.get(R.string.ssh_jump_not_connected))
                val tunnel = jc.newDirectConnection(host.hostname, host.port)
                c.connectVia(tunnel)
            } else {
                c.connect(host.hostname, host.port)
            }
            authenticate(c)
            c.transport.timeoutMs = 30_000
        } catch (e: Exception) {
            try { c.disconnect() } catch (_: Exception) {}
            throw translate(e)
        }
        client = c
    }

    private fun authenticate(c: SSHClient) {
        when (host.authType) {
            AuthType.KEY -> {
                val key = vault.key(host.keyId) ?: throw SshException(S.get(R.string.ssh_key_not_found), fatal = true)
                val provider = c.loadKeys(
                    KeyManager.privateKeyText(key), null, StaticPasswordFinder(KeyManager.passphrase(key)),
                )
                c.authPublickey(host.username, provider)
                // Login dengan kunci berhasil: kunci ini jelas terpasang di server ini.
                vault.markKeyInstalled(key.id, host.id)
            }
            AuthType.PASSWORD -> {
                var password = SecretBox.decryptOrNull(host.passwordEnc)
                var save = false
                if (password == null) {
                    val ans = runBlocking {
                        Prompts.askPassword(S.get(R.string.ssh_password_for, host.label), host.address)
                    } ?: throw SshException(S.get(R.string.ssh_login_cancelled), fatal = true)
                    password = ans.password
                    save = ans.save
                }
                val finder = StaticPasswordFinder(password)
                try {
                    c.auth(host.username, AuthPassword(finder), AuthKeyboardInteractive(PasswordResponseProvider(finder)))
                } catch (e: UserAuthException) {
                    throw SshException(S.get(R.string.ssh_auth_failed_password), e, fatal = true)
                }
                if (save) {
                    val enc = SecretBox.encrypt(password)
                    vault.saveHost((vault.host(host.id) ?: host).copy(passwordEnc = enc))
                }
            }
        }
    }

    private fun translate(e: Exception): Exception = when (e) {
        is SshException -> e
        is UserAuthException -> SshException(S.get(R.string.ssh_auth_rejected), e, fatal = true)
        is java.net.UnknownHostException -> SshException(S.get(R.string.ssh_host_not_found, host.hostname), e)
        is java.net.ConnectException -> SshException(S.get(R.string.ssh_connection_refused, host.hostname, host.port), e)
        is java.net.SocketTimeoutException -> SshException(S.get(R.string.ssh_connect_timeout), e)
        else -> {
            val chain = generateSequence(e as Throwable) { it.cause }.toList()
            val hostKey = chain.any { it.message?.contains("HOST_KEY_NOT_VERIFIABLE") == true || it.message?.contains("Could not verify") == true }
            if (hostKey) SshException(S.get(R.string.ssh_host_key_rejected), e, fatal = true)
            else SshException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    /** Buka shell interaktif dengan PTY. Bila [command] diisi, perintah itu dijalankan di PTY. */
    fun openShell(cols: Int, rows: Int, command: String?): Pair<Session, Session.Shell?> {
        val c = client ?: throw SshException(S.get(R.string.ssh_not_connected))
        val session = c.startSession()
        session.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
        try {
            session.setEnvVar("LANG", "en_US.UTF-8")
        } catch (_: Exception) {
            // Banyak server menolak SetEnv; abaikan.
        }
        return if (command.isNullOrBlank()) {
            session to session.startShell()
        } else {
            session.exec(command)
            session to null
        }
    }

    /** Jalankan perintah non-interaktif dan kumpulkan outputnya. */
    fun exec(command: String, timeoutSec: Long = 20, stdin: String? = null): ExecResult {
        val c = client ?: throw SshException(S.get(R.string.ssh_not_connected))
        val session = c.startSession()
        try {
            val cmd = session.exec(command)
            if (stdin != null) {
                cmd.outputStream.write(stdin.toByteArray(Charsets.UTF_8))
                cmd.outputStream.flush()
                cmd.outputStream.close()
            }
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val errThread = Thread { try { cmd.errorStream.copyTo(err) } catch (_: Exception) {} }
            val outThread = Thread { try { cmd.inputStream.copyTo(out) } catch (_: Exception) {} }
            errThread.isDaemon = true
            outThread.isDaemon = true
            errThread.start()
            outThread.start()
            outThread.join(timeoutSec * 1000)
            if (outThread.isAlive) {
                try { session.close() } catch (_: Exception) {}
                throw SshException(S.get(R.string.ssh_command_timeout, timeoutSec.toInt()))
            }
            errThread.join(2000)
            try { cmd.join(2, TimeUnit.SECONDS) } catch (_: Exception) {}
            return ExecResult(
                synchronized(out) { out.toString(Charsets.UTF_8.name()) },
                synchronized(err) { err.toString(Charsets.UTF_8.name()) },
                cmd.exitStatus,
            )
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }

    /** null = belum diketahui; true = sudo meminta password; false = sudo NOPASSWD. */
    @Volatile
    private var sudoNeedsPassword: Boolean? = null

    class SudoPasswordRequired : Exception("Password sudo diperlukan")

    /**
     * Jalankan [script] (dibaca `sh -s`) sebagai root lewat sudo. Password tidak pernah ditaruh di
     * argumen perintah: dengan `sudo -S -p ''` baris pertama stdin adalah password, sisanya skrip.
     * Melempar [SudoPasswordRequired] bila sudo meminta password tetapi [password] null.
     */
    fun execSudo(script: String, password: String?, timeoutSec: Long = 20): ExecResult {
        if (sudoNeedsPassword != true) {
            val r = exec("sudo -n sh -s", timeoutSec, stdin = script)
            when {
                Sudo.needsPassword(r.stderr) -> sudoNeedsPassword = true
                Sudo.notInstalled(r.stderr) -> throw SshException(S.get(R.string.ssh_sudo_not_installed))
                Sudo.notAllowed(r.stderr) -> throw SshException(S.get(R.string.ssh_sudo_not_allowed, host.username))
                else -> { sudoNeedsPassword = false; return r }
            }
        }
        val pw = password ?: throw SudoPasswordRequired()
        val r = exec("sudo -S -p '' sh -s", timeoutSec, stdin = pw + "\n" + script)
        if (Sudo.wrongPassword(r.stderr)) throw SudoWrongPasswordException(S.get(R.string.ssh_sudo_wrong_password))
        if (Sudo.notAllowed(r.stderr)) throw SshException(S.get(R.string.ssh_sudo_not_allowed, host.username))
        return r
    }

    fun sftp(): SFTPClient {
        synchronized(sftpLock) {
            sftpClient?.let { return it }
            val c = client ?: throw SshException(S.get(R.string.ssh_not_connected))
            return c.newSFTPClient().also { sftpClient = it }
        }
    }

    fun resetSftp() {
        synchronized(sftpLock) {
            try { sftpClient?.close() } catch (_: Exception) {}
            sftpClient = null
        }
    }

    /**
     * Apakah koneksi masih benar-benar hidup? Mengirim keepalive@openssh.com dan menunggu balasan.
     * Balasan "gagal" dari server tetap berarti koneksi hidup; hanya tanpa balasan (timeout) yang mati.
     */
    fun probe(timeoutSec: Long = 8): Boolean {
        val c = client ?: return false
        if (!c.isConnected) return false
        return try {
            c.connection.sendGlobalRequest("keepalive@openssh.com", true, ByteArray(0)).retrieve(timeoutSec, TimeUnit.SECONDS)
            true
        } catch (e: Exception) {
            val chain = generateSequence(e as Throwable) { it.cause }.toList()
            val timedOut = chain.any { it is java.util.concurrent.TimeoutException || it.message?.contains("timeout", ignoreCase = true) == true }
            !timedOut && c.isConnected
        }
    }

    fun close() {
        resetSftp()
        val c = client
        client = null
        try { c?.disconnect() } catch (_: Exception) {}
    }

    private inner class Verifier : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val hostPort = "${host.hostname}:${host.port}"
            val type = KeyType.fromKey(key).toString()
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            val fp = OpenSshKeyFormat.fingerprintSha256(blob)
            val known = vault.knownHost(hostPort, type)
            if (known != null && known.fingerprint == fp) return true
            val others = vault.knownHostsFor(hostPort)
            val accepted = runBlocking {
                Prompts.askHostKey(
                    hostPort = hostPort,
                    keyType = type,
                    fingerprint = fp,
                    changed = known != null,
                    oldFingerprint = known?.fingerprint ?: others.firstOrNull()?.fingerprint,
                )
            }
            if (accepted) vault.saveKnownHost(KnownHost(hostPort, type, fp))
            return accepted
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
}
