package com.kroombox.termx.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Cara menjalankan agentd di sebuah host. Linux/macOS memakai sh seperti biasa; Windows (PowerShell
 * atau cmd.exe sebagai shell SSH) tidak mengenal `&&`, `~`, `2>/dev/null`, maupun kutip tunggal sh,
 * jadi argumen dikirim sebagai JSON base64url yang aman di shell mana pun.
 */
internal data class RemoteHost(val windows: Boolean, val py: String, val script: String) {
    /** Baris perintah untuk `agentd.py <args>`; [args] memakai kutip sh seperti biasa (lihat [shq]). */
    fun line(args: String, stdin: String? = null): String =
        if (!windows) "${AgentManager.PY} ${AgentManager.REMOTE} $args"
        else "$py \"$script\" --b64 ${RemoteCmd.payload(RemoteCmd.shSplit(args), stdin)}"
}

internal object RemoteCmd {
    /** Pecah argumen bergaya sh (kata polos, '…', dan \' di luar kutip) menjadi daftar argumen. */
    fun shSplit(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inWord = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '\'' -> {
                    inWord = true
                    val end = s.indexOf('\'', i + 1).let { if (it < 0) s.length else it }
                    cur.append(s, i + 1, end)
                    i = end
                }
                ch == '\\' && i + 1 < s.length -> { inWord = true; cur.append(s[i + 1]); i++ }
                ch == ' ' || ch == '\t' || ch == '\n' -> {
                    if (inWord) { out.add(cur.toString()); cur.setLength(0); inWord = false }
                }
                else -> { inWord = true; cur.append(ch) }
            }
            i++
        }
        if (inWord) out.add(cur.toString())
        return out
    }

    /** JSON (daftar argumen, atau {"a": argumen, "i": stdin}) sebagai base64url tanpa padding. */
    fun payload(args: List<String>, stdin: String? = null): String {
        val arr = JsonArray(args.map { JsonPrimitive(it) })
        val json = if (stdin == null) arr.toString() else buildJsonObject { put("a", arr); put("i", stdin) }.toString()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    /** Penanda platform dari `python -c PROBE`: "TXPLAT <sys.platform> <home>". */
    const val PROBE = "import sys,os;print('TXPLAT',sys.platform,os.path.expanduser('~'))"

    /** Pindahkan berkas sementara hasil SFTP ke tempatnya (Windows). */
    const val WIN_REPLACE = "import os;d=os.path.join(os.path.expanduser('~'),'.termx');" +
        "os.replace(os.path.join(d,'agentd.py.tmp'),os.path.join(d,'agentd.py'));print('OK')"

    /** Cadangan bila SFTP tidak tersedia: tulis skrip dari stdin. */
    const val WIN_STDIN_INSTALL = "import sys,os;d=os.path.join(os.path.expanduser('~'),'.termx');" +
        "os.makedirs(d,exist_ok=True);t=os.path.join(d,'agentd.py.tmp'+str(os.getpid()));" +
        "f=open(t,'wb');f.write(sys.stdin.buffer.read());f.close();os.replace(t,os.path.join(d,'agentd.py'));print('OK')"

    fun parseProbe(stdout: String): Pair<String, String>? {
        val l = stdout.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("TXPLAT ") } ?: return null
        val p = l.split(" ", limit = 3)
        return if (p.size == 3) p[1] to p[2] else null
    }
}
