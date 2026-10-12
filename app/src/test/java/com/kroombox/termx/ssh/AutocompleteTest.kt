package com.kroombox.termx.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocompleteTest {
    @Test fun tracksTypingBackspaceAndEnter() {
        val t = InputTracker()
        t.feed("docker pz")
        t.feed("\u007f")
        t.feed("s")
        assertEquals("docker ps", t.line)
        assertEquals(listOf("docker ps"), t.feed("\r"))
        assertEquals("", t.line)
    }

    @Test fun arrowsAndTabMakeLineUnknownUntilEnter() {
        val t = InputTracker()
        t.feed("git ")
        t.feed("\u001b[A")
        assertNull(t.line)
        assertTrue(t.feed("\r").isEmpty())
        assertEquals("", t.line)
        t.feed("cd /et\t")
        assertNull(t.line)
    }

    @Test fun mouseAndPasteDoNotBreakTracking() {
        val t = InputTracker()
        t.feed("ls ")
        t.feed("\u001b[<64;10;5M")
        t.feed("\u001b[200~-la\u001b[201~")
        assertEquals("ls -la", t.line)
        t.feed("\u0003")
        assertEquals("", t.line)
    }

    @Test fun suggestionsPreferHistoryThenSnippetsThenCommon() {
        val s = Autocomplete.suggest("doc", listOf("docker compose logs -f api", "ls"), listOf("docker ps --format x"))
        assertEquals("docker compose logs -f api", s[0])
        assertEquals("docker ps --format x", s[1])
        assertTrue(s.contains("docker ps"))
        assertTrue(Autocomplete.suggest("", listOf("ls"), emptyList()).isEmpty())
        assertFalse(Autocomplete.suggest("ls -la", listOf("ls -la"), emptyList()).contains("ls -la"))
    }

    @Test fun sudoPromptForUser() {
        assertTrue(Sudo.isSudoPromptFor("$ sudo ls\r\n[sudo] password for andi: ", "andi"))
        assertTrue(Sudo.isSudoPromptFor("[sudo] kata sandi untuk andi:", "andi"))
        assertFalse(Sudo.isSudoPromptFor("[sudo] password for root: ", "andi"))
        assertFalse(Sudo.isSudoPromptFor("Password: ", "andi"))
    }

    @Test fun detectsUserInvokedSudo() {
        assertTrue(Sudo.invokesSudo("sudo apt update"))
        assertTrue(Sudo.invokesSudo("cd /srv && sudo make install"))
        assertTrue(Sudo.invokesSudo("sudo -i"))
        assertFalse(Sudo.invokesSudo("cat sudoers.txt"))
        assertFalse(Sudo.invokesSudo("echo pseudo"))
        assertFalse(Sudo.invokesSudo("grep sudo /var/log/auth.log"))
    }

    @Test fun detectsUserInvokedSu() {
        assertTrue(Sudo.invokesSu("su"))
        assertTrue(Sudo.invokesSu("su -"))
        assertTrue(Sudo.invokesSu("cd / && su - root"))
        assertFalse(Sudo.invokesSu("sudo ls"))
        assertFalse(Sudo.invokesSu("cat summary.txt"))
        assertFalse(Sudo.invokesSu("echo sum"))
    }

    @Test fun suPromptIsStrict() {
        assertTrue(Sudo.isSuPrompt("$ su -\r\nPassword: "))
        assertTrue(Sudo.isSuPrompt("su\r\nKata sandi:"))
        assertFalse(Sudo.isSuPrompt("[sudo] password for andi: "))
        assertFalse(Sudo.isSuPrompt("Enter password for vault: "))
        assertFalse(Sudo.isSuPrompt("cat notes.txt\r\nPassword: hunter2\r\n$ "))
    }
}
