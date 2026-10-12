package com.kroombox.termx.ssh

/**
 * Utilitas sudo yang tidak bergantung pada Android (bisa diuji unit).
 *
 * Banyak server membutuhkan sudo untuk perintah tertentu (docker, journalctl, apt, …). TermX
 * menangani dua jalur:
 *  - Terminal interaktif: perintah dibungkus `sudo …`, lalu password diketik otomatis hanya setelah
 *    prompt password sudo benar-benar terlihat di layar.
 *  - Perintah latar (monitor): dijalankan lewat `sudo -n` (NOPASSWD) atau `sudo -S` dengan password
 *    dikirim lewat stdin, tidak pernah lewat argumen perintah.
 */
object Sudo {
    private val SHELL_META = Regex("[;&|<>`$(){}\\n*?!~#\\\\]")

    /** Kutip satu argumen untuk sh. */
    fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Bungkus perintah dengan sudo. Perintah sederhana (tanpa metakarakter shell) cukup diawali
     * `sudo `; perintah majemuk (pipe, &&, redirect, variabel) dijalankan utuh di `sudo sh -c '…'`
     * supaya seluruh bagiannya ikut berjalan sebagai root, bukan hanya perintah pertama.
     */
    fun wrap(command: String): String {
        val cmd = command.trim()
        if (cmd.isEmpty()) return cmd
        if (cmd.startsWith("sudo ")) return cmd
        return if (SHELL_META.containsMatchIn(cmd)) "sudo sh -c ${shQuote(cmd)}" else "sudo $cmd"
    }

    private val ANSI = Regex("\u001B(\\[[0-9;?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(\u0007|\u001B\\\\)|[@-Z\\\\-_])")

    /** Buang escape sequence supaya teks prompt bisa dicocokkan. */
    fun stripAnsi(s: String): String = ANSI.replace(s, "")

    private val PROMPTS = listOf(
        Regex("\\[sudo\\][^\\n]*:\\s*$", RegexOption.IGNORE_CASE),
        Regex("password for [^\\n]*:\\s*$", RegexOption.IGNORE_CASE),
        Regex("kata sandi[^\\n]*:\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*password:\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
    )

    /** Apakah ekor output terminal sedang menampilkan prompt password sudo? */
    fun isPasswordPrompt(tail: String): Boolean {
        val text = stripAnsi(tail).replace("\r", "").trimEnd(' ').let { t ->
            // Ambil baris terakhir yang tidak kosong; prompt sudo selalu di baris kursor.
            t.lines().lastOrNull { it.isNotBlank() } ?: ""
        }
        return PROMPTS.any { it.containsMatchIn(text) }
    }

    /**
     * Prompt milik sudo untuk [user] (mis. `[sudo] password for andi:` / `[sudo] kata sandi untuk andi:`).
     * Hanya prompt jenis ini yang diisi otomatis; prompt password lain cukup ditawarkan tombolnya.
     */
    fun isSudoPromptFor(tail: String, user: String): Boolean {
        val last = stripAnsi(tail).replace("\r", "").trimEnd(' ').lines().lastOrNull { it.isNotBlank() } ?: return false
        if (!last.contains("[sudo]", ignoreCase = true)) return false
        return user.isBlank() || Regex("\\b" + Regex.escape(user) + "\\s*:\\s*$").containsMatchIn(last)
    }

    private val INVOKES_SUDO = Regex("(^|[;&|(]\\s*|\\bexec\\s+)sudo(\\s|$)")

    /** Baris perintah yang menjalankan sudo (mis. `sudo apt update`, `cd x && sudo make install`). */
    fun invokesSudo(line: String): Boolean = INVOKES_SUDO.containsMatchIn(line.trim())

    private val INVOKES_SU = Regex("(^|[;&|(]\\s*|\\bexec\\s+)su(\\s|$)")

    /** Baris perintah yang menjalankan `su` (mis. `su`, `su -`, `su - root`). */
    fun invokesSu(line: String): Boolean = INVOKES_SU.containsMatchIn(line.trim())

    /** Hasil stderr sudo yang berarti password diperlukan (mode -n). */
    fun needsPassword(stderr: String): Boolean {
        val e = stderr.lowercase()
        return "a password is required" in e || "password is required" in e || "a terminal is required" in e ||
            "kata sandi diperlukan" in e
    }

    fun wrongPassword(stderr: String): Boolean {
        val e = stderr.lowercase()
        return "incorrect password" in e || "sorry, try again" in e || "maaf, coba lagi" in e ||
            "authentication failure" in e
    }

    fun notInstalled(stderr: String): Boolean {
        val e = stderr.lowercase()
        return "sudo: not found" in e || "sudo: command not found" in e || ("sudo" in e && "not found" in e && "command" in e)
    }

    fun notAllowed(stderr: String): Boolean {
        val e = stderr.lowercase()
        return "is not in the sudoers file" in e || "not allowed to execute" in e || "may not run sudo" in e
    }
}
