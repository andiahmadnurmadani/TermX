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
        else python(listOf(script, "--b64", RemoteCmd.payload(RemoteCmd.shSplit(args), stdin)))

    /**
     * `python <args>` di Windows. Argumen tanpa kutip ganda di dalamnya. Python di path berspasi
     * (mis. C:\Users\Nama Saya\…) dijalankan lewat PowerShell agar sama di PowerShell maupun cmd.
     */
    fun python(args: List<String>): String {
        val spaced = py.contains(' ') && !py.startsWith("py ")
        if (!spaced) return (listOf(py) + args.map { if (it.contains(' ') || it.contains(';')) "\"$it\"" else it }).joinToString(" ")
        val ps = "& " + (listOf(py) + args).joinToString(" ") { "'" + it.replace("'", "''") + "'" } + "; exit \$LASTEXITCODE"
        return RemoteCmd.powershell(ps)
    }
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

    /** Jalankan skrip PowerShell dari shell Windows mana pun (cmd atau PowerShell). */
    fun powershell(script: String): String =
        "powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand " +
            Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))

    /** Python yang sudah terpasang walau belum ada di PATH sesi SSH ini (bukan alias Microsoft Store). */
    val WIN_FIND_PYTHON = """
        |${'$'}c = @(Get-Command python, python3 -CommandType Application -ErrorAction SilentlyContinue |
        |  Where-Object { ${'$'}_.Source -notlike '*\WindowsApps\*' } | ForEach-Object { ${'$'}_.Source })
        |${'$'}c += @(Get-ChildItem "${'$'}env:LOCALAPPDATA\Programs\Python\Python3*\python.exe", "${'$'}env:ProgramFiles\Python3*\python.exe" -ErrorAction SilentlyContinue |
        |  Sort-Object FullName -Descending | ForEach-Object { ${'$'}_.FullName })
        |foreach (${'$'}p in ${'$'}c) { if (${'$'}p) { 'TXPY ' + ${'$'}p; break } }
    """.trimMargin()

    /** Pasang Python 3 untuk pengguna ini saja (tanpa admin): winget, atau installer resmi python.org. */
    val WIN_INSTALL_PYTHON = """
        |${'$'}ProgressPreference = 'SilentlyContinue'
        |if (Get-Command winget -ErrorAction SilentlyContinue) {
        |  winget install -e --id Python.Python.3.12 --scope user --silent --accept-package-agreements --accept-source-agreements --disable-interactivity | Out-Null
        |  if (${'$'}LASTEXITCODE -eq 0) { 'TXOK'; exit 0 }
        |}
        |${'$'}arch = if (${'$'}env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'amd64' }
        |${'$'}u = "https://www.python.org/ftp/python/3.12.10/python-3.12.10-${'$'}arch.exe"
        |${'$'}f = Join-Path ${'$'}env:TEMP 'termx-python-setup.exe'
        |[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        |Invoke-WebRequest -Uri ${'$'}u -OutFile ${'$'}f -UseBasicParsing
        |${'$'}p = Start-Process -FilePath ${'$'}f -ArgumentList '/quiet', 'InstallAllUsers=0', 'PrependPath=1', 'Include_launcher=1', 'Include_test=0' -Wait -PassThru
        |Remove-Item ${'$'}f -ErrorAction SilentlyContinue
        |if (${'$'}p.ExitCode -eq 0) { 'TXOK' } else { 'installer exit code ' + ${'$'}p.ExitCode }
    """.trimMargin()

    /** Linux (dijalankan dengan sudo): python3 lewat pengelola paket yang tersedia. */
    const val LINUX_INSTALL_PYTHON = """
set -e
if command -v apt-get >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive; apt-get update -qq; apt-get install -y -qq python3
elif command -v dnf >/dev/null 2>&1; then dnf install -y -q python3
elif command -v yum >/dev/null 2>&1; then yum install -y -q python3
elif command -v zypper >/dev/null 2>&1; then zypper --non-interactive install python3
elif command -v apk >/dev/null 2>&1; then apk add --no-cache python3
elif command -v pacman >/dev/null 2>&1; then pacman -Sy --noconfirm python
else echo NOPM; exit 3
fi
command -v python3 >/dev/null 2>&1 && echo TXOK
"""

    fun parseProbe(stdout: String): Pair<String, String>? {
        val l = stdout.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("TXPLAT ") } ?: return null
        val p = l.split(" ", limit = 3)
        return if (p.size == 3) p[1] to p[2] else null
    }
}
