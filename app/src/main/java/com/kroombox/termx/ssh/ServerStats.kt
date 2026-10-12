package com.kroombox.termx.ssh

/** Statistik server dari perintah read-only (/proc, df, ps). */
data class ServerStats(
    val hostname: String = "",
    val os: String = "",
    val kernel: String = "",
    val uptimeSec: Long = 0,
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val cpus: Int = 0,
    val cpuPercent: Double? = null,
    val memTotalKb: Long = 0,
    val memAvailKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapFreeKb: Long = 0,
    val disks: List<Disk> = emptyList(),
    val processes: List<Proc> = emptyList(),
    val dockerRunning: Int? = null,
    /** null = docker tidak terpasang; "ok"; "denied" (butuh sudo / grup docker); "error". */
    val dockerAccess: String? = null,
    val containers: List<Container> = emptyList(),
    /** Skrip dijalankan sebagai root (sudo). */
    val asRoot: Boolean = false,
    val netRxBytes: Long = 0,
    val netTxBytes: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
) {
    data class Disk(val mount: String, val totalBytes: Long, val usedBytes: Long) {
        val percent: Double get() = if (totalBytes > 0) usedBytes * 100.0 / totalBytes else 0.0
    }

    data class Proc(val pid: Int, val cpu: Double, val mem: Double, val command: String)

    data class Container(val name: String, val status: String, val image: String) {
        val running: Boolean get() = status.startsWith("Up", ignoreCase = true)
    }

    val memUsedPercent: Double
        get() = if (memTotalKb > 0) (memTotalKb - memAvailKb) * 100.0 / memTotalKb else 0.0
    val swapUsedPercent: Double
        get() = if (swapTotalKb > 0) (swapTotalKb - swapFreeKb) * 100.0 / swapTotalKb else 0.0

    companion object {
        /** Skrip POSIX sh, dikirim lewat stdin ke `sh -s` (aman untuk shell apa pun). */
        val SCRIPT = """
            export LC_ALL=C
            echo "hostname=${'$'}(hostname 2>/dev/null)"
            if [ -r /etc/os-release ]; then . /etc/os-release; echo "os=${'$'}PRETTY_NAME"; else echo "os=${'$'}(uname -s)"; fi
            echo "kernel=${'$'}(uname -r)"
            [ -r /proc/uptime ] && echo "uptime=${'$'}(cut -d' ' -f1 /proc/uptime)"
            [ -r /proc/loadavg ] && echo "load=${'$'}(cat /proc/loadavg)"
            echo "cpus=${'$'}(nproc 2>/dev/null || grep -c ^processor /proc/cpuinfo 2>/dev/null)"
            if [ -r /proc/stat ]; then
              echo "cpu1=${'$'}(head -1 /proc/stat)"; sleep 1; echo "cpu2=${'$'}(head -1 /proc/stat)"
            fi
            [ -r /proc/meminfo ] && grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo | tr -s ' ' | sed 's/: /=/; s/ kB//'
            df -P -k -x tmpfs -x devtmpfs -x overlay -x squashfs -x efivarfs 2>/dev/null | tail -n +2 | awk '{print "disk="${'$'}6"|"${'$'}2"|"${'$'}3}' | head -8
            ps -eo pid,pcpu,pmem,comm --sort=-pcpu 2>/dev/null | sed -n '2,7p' | awk '{print "proc="${'$'}1"|"${'$'}2"|"${'$'}3"|"${'$'}4}'
            echo "uid=${'$'}(id -u 2>/dev/null)"
            if command -v docker >/dev/null 2>&1; then
              if dout=${'$'}(docker ps -a --format '{{.Names}}|{{.Status}}|{{.Image}}' 2>&1); then
                echo "dockeraccess=ok"
                printf '%s\n' "${'$'}dout" | grep '|' | head -40 | sed 's/^/ctr=/'
              else
                case "${'$'}dout" in
                  *ermission*|*denied*|*connect*to*the*Docker*daemon*socket*) echo "dockeraccess=denied" ;;
                  *) echo "dockeraccess=error" ;;
                esac
              fi
            fi
            [ -r /proc/net/dev ] && awk 'NR>2{gsub(":"," "); if(${'$'}1!="lo"){rx+=${'$'}2; tx+=${'$'}10}} END{print "net="rx"|"tx}' /proc/net/dev
            exit 0
        """.trimIndent()

        fun parse(output: String): ServerStats {
            var s = ServerStats()
            val disks = mutableListOf<Disk>()
            val procs = mutableListOf<Proc>()
            val containers = mutableListOf<Container>()
            var cpu1: List<Long>? = null
            var cpu2: List<Long>? = null
            for (raw in output.lineSequence()) {
                val line = raw.trim()
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val k = line.substring(0, eq)
                val v = line.substring(eq + 1)
                when (k) {
                    "hostname" -> s = s.copy(hostname = v)
                    "os" -> s = s.copy(os = v.trim('"'))
                    "kernel" -> s = s.copy(kernel = v)
                    "uptime" -> s = s.copy(uptimeSec = v.toDoubleOrNull()?.toLong() ?: 0)
                    "load" -> {
                        val p = v.split(' ')
                        s = s.copy(
                            load1 = p.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                            load5 = p.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                            load15 = p.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
                        )
                    }
                    "cpus" -> s = s.copy(cpus = v.trim().toIntOrNull() ?: 0)
                    "cpu1" -> cpu1 = cpuFields(v)
                    "cpu2" -> cpu2 = cpuFields(v)
                    "MemTotal" -> s = s.copy(memTotalKb = v.trim().toLongOrNull() ?: 0)
                    "MemAvailable" -> s = s.copy(memAvailKb = v.trim().toLongOrNull() ?: 0)
                    "SwapTotal" -> s = s.copy(swapTotalKb = v.trim().toLongOrNull() ?: 0)
                    "SwapFree" -> s = s.copy(swapFreeKb = v.trim().toLongOrNull() ?: 0)
                    "disk" -> {
                        val p = v.split('|')
                        if (p.size >= 3) disks += Disk(p[0], (p[1].toLongOrNull() ?: 0) * 1024, (p[2].toLongOrNull() ?: 0) * 1024)
                    }
                    "proc" -> {
                        val p = v.split('|')
                        if (p.size >= 4) procs += Proc(p[0].toIntOrNull() ?: 0, p[1].toDoubleOrNull() ?: 0.0, p[2].toDoubleOrNull() ?: 0.0, p[3])
                    }
                    "docker" -> s = s.copy(dockerRunning = v.trim().toIntOrNull())
                    "dockeraccess" -> s = s.copy(dockerAccess = v.trim())
                    "uid" -> s = s.copy(asRoot = v.trim() == "0")
                    "ctr" -> {
                        val p = v.split('|')
                        if (p.size >= 3) containers += Container(p[0], p[1], p.drop(2).joinToString("|"))
                    }
                    "net" -> {
                        val p = v.split('|')
                        s = s.copy(netRxBytes = p.getOrNull(0)?.toDoubleOrNull()?.toLong() ?: 0, netTxBytes = p.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: 0)
                    }
                }
            }
            val a = cpu1
            val b = cpu2
            val cpuPct = if (a != null && b != null && a.size >= 4 && b.size >= 4) {
                val idleA = a[3] + (a.getOrNull(4) ?: 0)
                val idleB = b[3] + (b.getOrNull(4) ?: 0)
                val totA = a.sum()
                val totB = b.sum()
                val dt = (totB - totA).toDouble()
                if (dt > 0) ((dt - (idleB - idleA)) / dt * 100.0).coerceIn(0.0, 100.0) else null
            } else null
            val dockerRunning = if (s.dockerAccess == "ok") containers.count { it.running } else s.dockerRunning
            return s.copy(
                cpuPercent = cpuPct, disks = disks, processes = procs, containers = containers,
                dockerRunning = dockerRunning, timestamp = System.currentTimeMillis(),
            )
        }

        private fun cpuFields(v: String): List<Long> =
            v.trim().split(Regex("\\s+")).drop(1).take(8).mapNotNull { it.toLongOrNull() }
    }
}
