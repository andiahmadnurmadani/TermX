package com.kroombox.termx.ssh

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Riwayat perintah per host untuk autocomplete. Disimpan lokal (satu perintah per baris, terbaru di
 * atas, maksimal [MAX]). Perintah yang tidak tampil di layar saat diketik (mis. password) tidak pernah
 * masuk — lihat TerminalSession.
 */
class CommandHistory(context: Context) {
    private val dir = File(context.filesDir, "cmdhistory").apply { mkdirs() }
    private val cache = HashMap<String, MutableList<String>>()

    private fun file(hostId: String) = AtomicFile(File(dir, hostId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt"))

    @Synchronized
    fun list(hostId: String): List<String> = cache.getOrPut(hostId) {
        try {
            val f = file(hostId)
            if (f.baseFile.exists()) String(f.readFully(), Charsets.UTF_8).lines().filter { it.isNotBlank() }.toMutableList()
            else mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }.toList()

    @Synchronized
    fun add(hostId: String, command: String) {
        val c = command.trim()
        if (c.isEmpty() || c.length > 500 || command.startsWith(" ")) return
        val l = cache.getOrPut(hostId) { list(hostId).toMutableList() }
        l.remove(c)
        l.add(0, c)
        while (l.size > MAX) l.removeAt(l.size - 1)
        write(hostId, l)
    }

    @Synchronized
    fun remove(hostId: String, command: String) {
        val l = cache.getOrPut(hostId) { list(hostId).toMutableList() }
        if (l.remove(command)) write(hostId, l)
    }

    @Synchronized
    fun clearAll() {
        cache.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    fun deleteHost(hostId: String) {
        synchronized(this) { cache.remove(hostId) }
        try { file(hostId).delete() } catch (_: Exception) {}
    }

    private fun write(hostId: String, l: List<String>) {
        val f = file(hostId)
        val out = try { f.startWrite() } catch (_: Exception) { return }
        try {
            out.write(l.joinToString("\n").toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (_: Exception) {
            f.failWrite(out)
        }
    }

    companion object {
        const val MAX = 300
    }
}
