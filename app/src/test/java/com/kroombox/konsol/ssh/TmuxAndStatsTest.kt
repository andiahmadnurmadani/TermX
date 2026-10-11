package com.kroombox.konsol.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxAndStatsTest {
    @Test fun sanitizeSessionName() {
        assertEquals("my-sess", Tmux.sanitize("my sess!"))
        assertEquals("konsol", Tmux.sanitize("  "))
    }

    @Test fun attachCommandIsSingleQuotedAndSafe() {
        val cmd = Tmux.attachCommand("work", mouse = true, startup = "echo 'hi'")
        assertTrue(cmd.startsWith("sh -c '") && cmd.endsWith("'"))
        // Isi skrip tidak boleh mengandung kutip tunggal (startup dikodekan base64).
        assertEquals(2, cmd.count { it == '\'' })
        assertTrue(cmd.contains("new-session -d -s work"))
        assertTrue(cmd.contains("set-option -q mouse on"))
    }

    @Test fun parseTmuxList() {
        val l = Tmux.parseList("a|2|1|100|200\nbad\nb|1|0|1|2\n")!!
        assertEquals(listOf("a", "b"), l.map { it.name })
        assertEquals(2, l[0].windows)
        assertNull(Tmux.parseList("NO_TMUX\n"))
    }

    @Test fun parseStats() {
        val out = """
            hostname=srv
            os=Ubuntu 24.04 LTS
            load=0.50 0.40 0.30 1/100 123
            cpus=4
            cpu1=cpu  100 0 100 800 0 0 0 0 0 0
            cpu2=cpu  150 0 150 900 0 0 0 0 0 0
            MemTotal=1000
            MemAvailable=250
            disk=/|1000|500
            proc=1|12.5|3.0|java
            net=1000|2000
        """.trimIndent()
        val s = ServerStats.parse(out)
        assertEquals("srv", s.hostname)
        assertEquals(4, s.cpus)
        assertEquals(50.0, s.cpuPercent!!, 0.01)
        assertEquals(75.0, s.memUsedPercent, 0.01)
        assertEquals(50.0, s.disks[0].percent, 0.01)
        assertEquals("java", s.processes[0].command)
        assertEquals(2000L, s.netTxBytes)
    }
}
