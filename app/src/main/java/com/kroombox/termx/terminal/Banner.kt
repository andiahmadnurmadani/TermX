package com.kroombox.termx.terminal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Banner ASCII yang ditulis ke terminal saat sebuah tab pertama kali tersambung: logo TermX dan
 * ringkasan koneksi (user@host, OS, mode tmux/biasa, waktu). Hanya tampilan lokal — tidak dikirim
 * ke server.
 */
object Banner {
    private const val ESC = "\u001b"
    private const val CYAN = "$ESC[38;2;34;211;238m"
    private const val GREEN = "$ESC[38;2;61;220;151m"
    private const val DIM = "$ESC[2m"
    private const val BOLD = "$ESC[1m"
    private const val RESET = "$ESC[0m"

    // Font figlet "small": "Term" + "X".
    private val TERM = listOf(
        " _____              ",
        "|_   _|__ _ _ _ __  ",
        "  | |/ -_) '_| '  \\ ",
        "  |_|\\___|_| |_|_|_|",
    )
    private val X = listOf(
        "__  __",
        "\\ \\/ /",
        " >  < ",
        "/_/\\_\\",
    )

    /** Lebar logo dalam kolom. */
    val LOGO_WIDTH = TERM[0].length + 2 + X[0].length

    fun render(
        userHost: String,
        osName: String?,
        mode: String,
        cols: Int,
        now: Date = Date(),
        locale: Locale = Locale.ENGLISH,
    ): String {
        val sb = StringBuilder()
        sb.append("\r\n")
        if (cols >= LOGO_WIDTH + 2) {
            for (i in TERM.indices) {
                sb.append(" ").append(CYAN).append(BOLD).append(TERM[i]).append(RESET)
                sb.append("  ").append(GREEN).append(BOLD).append(X[i]).append(RESET).append("\r\n")
            }
        } else {
            sb.append(" ").append(CYAN).append(BOLD).append("Term").append(GREEN).append("X").append(RESET).append("\r\n")
        }
        sb.append("\r\n")
        fun line(dot: String, text: String) {
            val max = (cols - 4).coerceAtLeast(8)
            val t = if (text.length > max) text.take(max - 1) + "…" else text
            sb.append(" ").append(dot).append("●").append(RESET).append(" ").append(t).append("\r\n")
        }
        line(GREEN, "$BOLD$userHost$RESET")
        if (!osName.isNullOrBlank()) line(CYAN, osName)
        line(CYAN, mode)
        val time = SimpleDateFormat("EEE, d MMM yyyy HH:mm", locale).format(now)
        line(DIM, "$DIM$time$RESET")
        sb.append(DIM).append("─".repeat((cols - 2).coerceIn(10, LOGO_WIDTH + 2))).append(RESET).append("\r\n")
        return sb.toString()
    }
}
