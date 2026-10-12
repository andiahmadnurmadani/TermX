package com.kroombox.termx.ssh

/** Utilitas tmux: membangun perintah attach dan mengelola sesi di server. */
object Tmux {
    data class RemoteSession(
        val name: String,
        val windows: Int,
        val attachedClients: Int,
        val createdEpoch: Long,
        val activityEpoch: Long,
    )

    private const val PATH_FIX = "PATH=\"\$PATH:/usr/local/bin:/opt/homebrew/bin:/snap/bin:\$HOME/.local/bin\"; export PATH;"

    /** Nama sesi aman (tanpa spasi/kutip). */
    fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9_.-]"), "-").trim('-').ifBlank { "termx" }.take(48)

    /**
     * Perintah untuk masuk ke sesi tmux (dibuat bila belum ada). [startup] hanya dijalankan saat sesi
     * baru dibuat, sehingga attach ulang tidak mengetik ulang perintah ke program yang sedang berjalan.
     * Bila tmux tidak terpasang, jatuh kembali ke shell login biasa. Dibungkus `sh -c` agar aman untuk
     * shell apa pun (bash/zsh/fish).
     */
    fun attachCommand(sessionName: String, mouse: Boolean, startup: String = ""): String {
        val n = sanitize(sessionName)
        val mouseOpt = if (mouse) " \\; set-option -q mouse on" else ""
        val sendStartup = if (startup.isNotBlank()) {
            val b64 = java.util.Base64.getEncoder().encodeToString(startup.trim().toByteArray(Charsets.UTF_8))
            " && tmux send-keys -t $n \"\$(echo $b64 | base64 -d 2>/dev/null || echo $b64 | base64 -D)\" Enter"
        } else ""
        val script = "$PATH_FIX if command -v tmux >/dev/null 2>&1; then " +
            "tmux has-session -t $n 2>/dev/null || { tmux start-server \\; set-option -gq history-limit 50000; tmux new-session -d -s $n$sendStartup; }; " +
            "exec tmux attach-session -t $n$mouseOpt; " +
            "else echo \"[tmux tidak ditemukan di server - memakai shell biasa (sesi tidak persisten)]\"; " +
            "exec \"\${SHELL:-/bin/sh}\" -l; fi"
        return "sh -c '$script'"
    }

    const val LIST_SCRIPT = "$PATH_FIX command -v tmux >/dev/null 2>&1 || { echo NO_TMUX; exit 0; }; " +
        "tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_attached}|#{session_created}|#{session_activity}' 2>/dev/null; exit 0"

    fun parseList(output: String): List<RemoteSession>? {
        if (output.contains("NO_TMUX")) return null
        return output.lineSequence().mapNotNull { line ->
            val p = line.trim().split('|')
            if (p.size < 5) return@mapNotNull null
            RemoteSession(
                name = p[0],
                windows = p[1].toIntOrNull() ?: 0,
                attachedClients = p[2].toIntOrNull() ?: 0,
                createdEpoch = p[3].toLongOrNull() ?: 0,
                activityEpoch = p[4].toLongOrNull() ?: 0,
            )
        }.toList()
    }

    fun killCommand(sessionName: String) = "$PATH_FIX tmux kill-session -t '${sanitize(sessionName)}'"

    fun installHint(): String =
        "Pasang tmux di server agar sesi tetap hidup: sudo apt install tmux  |  sudo dnf install tmux  |  brew install tmux"
}
