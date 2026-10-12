package com.kroombox.termx.data

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
    val persistentSession: Boolean = false,
    val tmuxSessionName: String = "termx",
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
    /**
     * Ikon host: "auto" (logo OS yang terdeteksi, atau inisial), "initials", "os:<id>" (logo OS),
     * "mi:<key>" (ikon bawaan), atau "emoji:<teks>". Lihat [com.kroombox.termx.ui.components.IconSpec].
     */
    val icon: String = "auto",
    /** OS yang terdeteksi otomatis saat konek (id katalog, mis. "ubuntu"). */
    val osId: String? = null,
    /** Nama OS lengkap dari server, mis. "Ubuntu 24.04.1 LTS". */
    val osName: String? = null,
    /** Password sudo terenkripsi (SecretBox). Null = pakai password login bila diizinkan, atau tanya. */
    val sudoPasswordEnc: String? = null,
    /** Pakai password login untuk sudo bila password sudo terpisah tidak diisi. */
    val sudoUseLoginPassword: Boolean = true,
    /** Monitor menjalankan perintahnya dengan sudo (mis. agar `docker ps` terbaca). */
    val monitorSudo: Boolean = false,
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
    /** Ikon snippet, format sama dengan [Host.icon] (tanpa "auto"). */
    val icon: String = "mi:bolt",
    /**
     * Jalankan dengan sudo. Perintah dibungkus `sudo …`, dan saat prompt password sudo muncul di
     * terminal, password sudo host diketik otomatis.
     */
    val useSudo: Boolean = false,
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
    /** Font terminal: id bawaan (jetbrains_mono, fira_code, cascadia_code, hack, system) atau id font impor. */
    val fontId: String = "jetbrains_mono",
    /** Ligatur (mis. -> != => menjadi simbol) untuk font yang mendukung. */
    val ligatures: Boolean = false,
    val customFonts: List<CustomFont> = emptyList(),
    val scrollbackLines: Int = 5000,
    val keepScreenOn: Boolean = true,
    val vibrateOnBell: Boolean = true,
    val defaultPersistent: Boolean = false,
    /** Wake-lock + Wi-Fi lock selama ada sesi, agar koneksi tidak dibekukan saat layar mati. */
    val keepAliveInBackground: Boolean = true,
    /** Dialog izin "tanpa batasan baterai" sudah pernah ditampilkan. */
    val batteryPromptShown: Boolean = false,
    /** Layout bar tombol tambahan, dipisah spasi. */
    val extraKeys: String = DEFAULT_EXTRA_KEYS,
) {
    companion object {
        const val DEFAULT_EXTRA_KEYS = "ESC TAB CTRL ALT / - | ~ UP DOWN LEFT RIGHT HOME END PGUP PGDN"
    }
}

/** Font yang diimpor pengguna; file disimpan di filesDir/fonts. */
@Serializable
data class CustomFont(
    val id: String = newId(),
    val name: String,
    val regularFile: String,
    val boldFile: String? = null,
)

@Serializable
data class VaultData(
    /** 2: ketuk host = terminal biasa (tmux jadi pilihan). */
    val version: Int = 2,
    val hosts: List<Host> = emptyList(),
    val groups: List<HostGroup> = emptyList(),
    val keys: List<SshKey> = emptyList(),
    val snippets: List<Snippet> = defaultSnippets(),
    val knownHosts: List<KnownHost> = emptyList(),
    val forwards: List<ForwardRule> = emptyList(),
    val settings: AppSettings = AppSettings(),
    /**
     * Tab terminal yang masih aktif. Disimpan agar bila Android mematikan proses aplikasi, sesi
     * dibuka lagi otomatis (dan attach ke tmux yang sama) sampai pengguna sendiri memutuskannya.
     */
    val openSessions: List<SavedSession> = emptyList(),
    /** Tab yang baru ditutup (terbaru dulu), untuk dibuka lagi dari tab Sesi beserta riwayatnya. */
    val recentSessions: List<SavedSession> = emptyList(),
)

@Serializable
data class SavedSession(
    val hostId: String,
    /** Nama sesi tmux; null = shell biasa. */
    val tmuxName: String? = null,
    val closedAt: Long = 0,
)

fun defaultSnippets(): List<Snippet> = listOf(
    Snippet(name = "Info sistem", command = "uname -a && uptime && free -h && df -h /", description = "Ringkasan host", icon = "mi:info"),
    Snippet(
        name = "Container Docker",
        command = "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'",
        description = "Daftar container berjalan (dengan sudo)",
        icon = "os:docker",
        useSudo = true,
    ),
    Snippet(name = "Log Docker", command = "docker logs --tail 100 -f ", description = "Ketik nama container lalu Enter", autoRun = false, icon = "mi:history", useSudo = true),
    Snippet(name = "Sesi tmux", command = "tmux ls", description = "Lihat sesi tmux di server", icon = "mi:layers"),
    Snippet(name = "Proses teratas", command = "ps aux --sort=-%cpu | head -15", description = "15 proses dengan CPU tertinggi", icon = "mi:speed"),
    Snippet(name = "Log sistem", command = "journalctl -n 100 --no-pager", description = "100 baris log terakhir", icon = "mi:list", useSudo = true),
    Snippet(name = "Update paket", command = "apt update && apt list --upgradable", description = "Debian/Ubuntu", icon = "mi:update", useSudo = true),
    Snippet(name = "Claude Code", command = "claude", description = "Jalankan Claude Code (di dalam tmux tetap hidup)", icon = "mi:code"),
)

/** Palet aksen untuk host/grup. */
val AccentColors = listOf(
    0xFF3DDC97, 0xFF5AA9FF, 0xFFC792EA, 0xFFF7C948, 0xFFFF6B6B, 0xFF4DD0E1, 0xFFFF9F43, 0xFF9CA3AF,
)
