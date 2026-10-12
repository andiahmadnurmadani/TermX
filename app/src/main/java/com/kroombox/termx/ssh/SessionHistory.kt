package com.kroombox.termx.ssh

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Riwayat layar terminal per sesi, disimpan di penyimpanan internal aplikasi.
 *
 * Kuncinya host + nama sesi tmux (atau "plain" untuk shell biasa), jadi membuka host yang sama lagi —
 * setelah tab ditutup, aplikasi ditutup, atau proses dimatikan Android — menampilkan riwayat yang sama.
 * Untuk sesi tmux, riwayat lengkap tetap di server (gulir layar = gulir riwayat tmux); yang disimpan
 * di sini adalah layar terakhir, agar langsung tampil walau belum tersambung.
 */
class SessionHistory(context: Context) {
    private val dir = File(context.filesDir, "history").apply { mkdirs() }

    data class Saved(val cols: Int, val rows: Int, val text: String)

    fun key(hostId: String, tmuxName: String?): String =
        (hostId + "_" + (tmuxName ?: "~plain")).replace(Regex("[^A-Za-z0-9_.~-]"), "_")

    private fun file(key: String) = AtomicFile(File(dir, "$key.ans"))

    fun load(key: String): Saved? = try {
        val f = file(key)
        if (!f.baseFile.exists()) null else {
            val raw = String(f.readFully(), Charsets.UTF_8)
            val nl = raw.indexOf('\n')
            val head = if (nl > 0) raw.substring(0, nl).split(' ') else emptyList()
            if (head.size >= 3 && head[0] == HEADER) {
                Saved(head[1].toIntOrNull() ?: 80, head[2].toIntOrNull() ?: 24, raw.substring(nl + 1))
            } else null
        }
    } catch (_: Exception) {
        null
    }

    fun save(key: String, cols: Int, rows: Int, text: String) {
        if (text.isBlank()) return
        val f = file(key)
        val out = try { f.startWrite() } catch (_: Exception) { return }
        try {
            out.write("$HEADER $cols $rows\n".toByteArray(Charsets.UTF_8))
            out.write(text.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (_: Exception) {
            f.failWrite(out)
        }
    }

    fun delete(key: String) {
        try { file(key).delete() } catch (_: Exception) {}
    }

    /** Hapus riwayat host yang sudah dihapus. */
    fun deleteForHost(hostId: String) {
        dir.listFiles()?.filter { it.name.startsWith(hostId + "_") }?.forEach { it.delete() }
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val HEADER = "TERMXHIST1"
    }
}
