package com.kroombox.konsol.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class AuthType { PASSWORD, KEY }

@Serializable
data class Host(
    val id: String = newId(),
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    /** Password terenkripsi (SecretBox). Null = tanya saat konek. */
    val passwordEnc: String? = null,
    val keyId: String? = null,
    val groupId: String? = null,
    val tags: List<String> = emptyList(),
    val colorIndex: Int = 0,
    /** Sesi persisten: shell dijalankan di dalam tmux supaya tetap hidup di server. */
    val persistentSession: Boolean = true,
    val tmuxSessionName: String = "konsol",
    /** Aktifkan mouse tmux supaya geser layar menggulir riwayat tmux. */
    val tmuxMouse: Boolean = true,
    /** Host perantara (jump host / bastion). */
    val jumpHostId: String? = null,
    val startupCommand: String = "",
    val keepAliveSec: Int = 20,
    val autoReconnect: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long = 0,
) {
    val address: String get() = if (port == 22) "$username@$hostname" else "$username@$hostname:$port"
}

@Serializable
data class HostGroup(
    val id: String = newId(),
    val name: String,
    val colorIndex: Int = 0,
)

@Serializable
data class SshKey(
    val id: String = newId(),
    val name: String,
    /** "ed25519", "rsa", atau tipe dari kunci impor (mis. "ssh-ed25519"). */
    val type: String,
    val publicKey: String,
    val privateKeyEnc: String,
    val passphraseEnc: String? = null,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Snippet(
    val id: String = newId(),
    val name: String,
    val command: String,
    val description: String = "",
    /** Kirim Enter otomatis setelah perintah. */
    val autoRun: Boolean = true,
    val tags: List<String> = emptyList(),
)

@Serializable
data class KnownHost(
    /** "host:port" */
    val hostPort: String,
    val keyType: String,
    val fingerprint: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class ForwardType { LOCAL, REMOTE, DYNAMIC }

@Serializable
data class ForwardRule(
    val id: String = newId(),
    val hostId: String,
    val label: String = "",
    val type: ForwardType = ForwardType.LOCAL,
    val bindHost: String = "127.0.0.1",
    val bindPort: Int,
    val destHost: String = "127.0.0.1",
    val destPort: Int = 0,
    val autoStart: Boolean = false,
)

@Serializable
data class AppSettings(
    val themeId: String = "midnight",
    val fontSizeSp: Float = 13f,
    val scrollbackLines: Int = 5000,
    val keepScreenOn: Boolean = true,
    val vibrateOnBell: Boolean = true,
    val defaultPersistent: Boolean = true,
    /** Layout bar tombol tambahan, dipisah spasi. */
    val extraKeys: String = DEFAULT_EXTRA_KEYS,
) {
    companion object {
        const val DEFAULT_EXTRA_KEYS = "ESC TAB CTRL ALT / - | ~ UP DOWN LEFT RIGHT HOME END PGUP PGDN"
    }
}

@Serializable
data class VaultData(
    val version: Int = 1,
    val hosts: List<Host> = emptyList(),
    val groups: List<HostGroup> = emptyList(),
    val keys: List<SshKey> = emptyList(),
    val snippets: List<Snippet> = defaultSnippets(),
    val knownHosts: List<KnownHost> = emptyList(),
    val forwards: List<ForwardRule> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

fun defaultSnippets(): List<Snippet> = listOf(
    Snippet(name = "Info sistem", command = "uname -a && uptime && free -h && df -h /", description = "Ringkasan host"),
    Snippet(name = "Container Docker", command = "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'", description = "Daftar container berjalan"),
    Snippet(name = "Sesi tmux", command = "tmux ls", description = "Lihat sesi tmux di server"),
    Snippet(name = "Proses teratas", command = "ps aux --sort=-%cpu | head -15", description = "15 proses dengan CPU tertinggi"),
    Snippet(name = "Log sistem", command = "journalctl -n 100 --no-pager", description = "100 baris log terakhir", autoRun = true),
    Snippet(name = "Claude Code", command = "claude", description = "Jalankan Claude Code (di dalam tmux tetap hidup)"),
)

/** Palet aksen untuk host/grup. */
val AccentColors = listOf(
    0xFF3DDC97, 0xFF5AA9FF, 0xFFC792EA, 0xFFF7C948, 0xFFFF6B6B, 0xFF4DD0E1, 0xFFFF9F43, 0xFF9CA3AF,
)
