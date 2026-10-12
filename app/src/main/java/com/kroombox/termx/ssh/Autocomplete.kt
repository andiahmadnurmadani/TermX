package com.kroombox.termx.ssh

/**
 * Melacak baris perintah yang sedang diketik pengguna, dari byte yang dikirim ke server.
 *
 * Hanya perkiraan: TermX tidak bisa melihat isi baris di shell. Begitu ada tombol yang mengubah baris
 * tanpa bisa dilacak (panah, Tab untuk completion shell, Ctrl-R, …) baris dianggap tidak diketahui
 * (null) sampai Enter berikutnya, sehingga saran tidak pernah melengkapi baris yang salah.
 * Peristiwa mouse (gulir tmux) dan fokus diabaikan karena tidak mengubah baris.
 */
class InputTracker {
    var line: String? = ""
        private set

    private enum class St { NORMAL, ESC, CSI, SS3 }
    private var st = St.NORMAL
    private val csi = StringBuilder()

    /** Proses input; kembalikan baris yang diselesaikan dengan Enter (bisa lebih dari satu). */
    fun feed(text: String): List<String> {
        val done = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when (st) {
                St.ESC -> {
                    st = when (cp) {
                        '['.code -> { csi.setLength(0); St.CSI }
                        'O'.code -> St.SS3
                        else -> { line = null; St.NORMAL } // Alt+huruf, dll.
                    }
                    continue
                }
                St.SS3 -> { line = null; st = St.NORMAL; continue } // panah/F1-F4 mode aplikasi
                St.CSI -> {
                    if (cp in 0x40..0x7E) {
                        val params = csi.toString()
                        val mouse = (cp == 'M'.code || cp == 'm'.code) && (params.startsWith("<") || params.isEmpty())
                        val focus = (cp == 'I'.code || cp == 'O'.code) && params.isEmpty()
                        val paste = cp == '~'.code && (params == "200" || params == "201")
                        if (!mouse && !focus && !paste) line = null
                        st = St.NORMAL
                    } else {
                        csi.appendCodePoint(cp)
                        // Mode mouse lama (ESC [ M b x y) membawa 3 byte mentah; cukup dianggap selesai.
                        if (csi.length > 32) { line = null; st = St.NORMAL }
                    }
                    continue
                }
                St.NORMAL -> {}
            }
            when (cp) {
                0x1B -> st = St.ESC
                '\r'.code, '\n'.code -> {
                    line?.takeIf { it.isNotBlank() }?.let { done += it }
                    line = ""
                }
                0x7F, 0x08 -> line = line?.let { l -> if (l.isEmpty()) l else l.substring(0, l.offsetByCodePoints(l.length, -1)) }
                0x03, 0x04, 0x15 -> line = "" // Ctrl-C, Ctrl-D, Ctrl-U
                0x17 -> line = line?.trimEnd()?.let { l -> l.substring(0, l.lastIndexOf(' ') + 1) } // Ctrl-W
                else -> if (cp < 0x20) line = null else line = line?.let { it + String(Character.toChars(cp)) }
            }
        }
        return done
    }

    fun reset() {
        line = ""
        st = St.NORMAL
    }
}

/** Sumber saran: riwayat perintah host, snippet, dan perintah umum. */
object Autocomplete {
    val COMMON = listOf(
        "ls -la", "cd ..", "cd ~", "pwd", "clear", "exit", "history", "htop", "top", "df -h", "du -sh *", "free -h",
        "uptime", "whoami", "uname -a", "ip a", "ss -tulpn", "ping -c 4 ", "curl -I ", "tail -f ", "less ", "cat ",
        "nano ", "vim ", "grep -rn ", "find . -name ", "chmod +x ", "chown ", "mkdir -p ", "rm -rf ", "cp -r ", "mv ",
        "tar -xzf ", "tar -czf ", "ps aux | grep ", "kill -9 ", "systemctl status ", "systemctl restart ",
        "systemctl stop ", "systemctl start ", "journalctl -u ", "journalctl -f", "sudo ", "sudo -i", "apt update",
        "apt upgrade", "apt install ", "dnf install ", "git status", "git pull", "git log --oneline -10", "git diff",
        "git add .", "git commit -m \"", "git push", "git checkout ", "docker ps", "docker ps -a", "docker logs -f ",
        "docker compose up -d", "docker compose down", "docker compose logs -f", "docker exec -it ", "docker images",
        "docker restart ", "tmux ls", "tmux attach -t ", "tmux new -s ", "npm install", "npm run ", "pm2 list",
        "pm2 logs ", "pm2 restart ", "python3 ", "pip install ", "node ", "claude",
    )

    /**
     * Saran untuk [prefix]: yang paling baru dipakai di host ini dulu, lalu snippet, lalu perintah umum.
     * Hanya yang benar-benar diawali [prefix] dan lebih panjang darinya.
     */
    fun suggest(prefix: String, history: List<String>, snippets: List<String>, max: Int = 8): List<String> {
        if (prefix.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        fun take(src: List<String>) {
            for (c in src) {
                if (out.size >= max) return
                if (c.length > prefix.length && c.startsWith(prefix) && '\n' !in c) out += c
            }
        }
        take(history)
        take(snippets)
        take(COMMON)
        return out.toList()
    }
}
