package com.kroombox.termx.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RemoteCmdTest {
    @Test fun splitsShQuotedArgs() {
        val args = "rename ${shq("20261104-1")} ${shq("it's a \"title\"  x")} ${shq("")}"
        assertEquals(listOf("rename", "20261104-1", "it's a \"title\"  x", ""), RemoteCmd.shSplit(args))
    }

    @Test fun splitsPlainAndMixed() {
        assertEquals(listOf("attach", "abc", "0", "40", "--no-stdin"), RemoteCmd.shSplit("attach 'abc' 0 40 --no-stdin"))
        assertEquals(listOf("mirror", "123", ""), RemoteCmd.shSplit("mirror 123 ''"))
        assertEquals(listOf("C:\\Users\\a b"), RemoteCmd.shSplit(shq("C:\\Users\\a b")))
    }

    @Test fun payloadIsShellSafeBase64Json() {
        val p = RemoteCmd.payload(listOf("post", "id", "{\"c\":\"send\",\"text\":\"halo & \$x 'y' %PATH%\"}"))
        assertTrue(p.matches(Regex("^[A-Za-z0-9_-]+$")))
        val json = String(Base64.getUrlDecoder().decode(p), Charsets.UTF_8)
        assertEquals("[\"post\",\"id\",\"{\\\"c\\\":\\\"send\\\",\\\"text\\\":\\\"halo & \$x 'y' %PATH%\\\"}\"]", json)
        val withIn = String(Base64.getUrlDecoder().decode(RemoteCmd.payload(listOf("hermes-setup", "probe"), "{\"k\":1}")))
        assertEquals("{\"a\":[\"hermes-setup\",\"probe\"],\"i\":\"{\\\"k\\\":1}\"}", withIn)
    }

    @Test fun parsesProbe() {
        assertEquals("win32" to "C:\\Users\\Andi Ahmad", RemoteCmd.parseProbe("\r\nTXPLAT win32 C:\\Users\\Andi Ahmad\r\n"))
        assertEquals("linux" to "/home/nmd", RemoteCmd.parseProbe("TXPLAT linux /home/nmd\n"))
        assertNull(RemoteCmd.parseProbe("python3 : The term 'python3' is not recognized"))
    }

    @Test fun windowsLineQuotesScriptAndEncodesArgs() {
        val h = RemoteHost(true, "py -3", "C:\\Users\\A B\\.termx\\agentd.py")
        val l = h.line("rename ${shq("x")} ${shq("y z")}")
        assertTrue(l.startsWith("py -3 \"C:\\Users\\A B\\.termx\\agentd.py\" --b64 "))
        assertFalse(l.contains("'"))
        assertEquals("python3 ~/.termx/agentd.py list", RemoteHost(false, "python3", "").line("list"))
    }
}
