package com.kroombox.termx.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudoTest {
    @Test fun wrapSimpleAndCompound() {
        assertEquals("sudo docker ps", Sudo.wrap("docker ps"))
        assertEquals("sudo sh -c 'docker ps | head -5'", Sudo.wrap("docker ps | head -5"))
        assertEquals("sudo sh -c 'echo '\\''hi'\\'' && id'", Sudo.wrap("echo 'hi' && id"))
        assertEquals("sudo apt update", Sudo.wrap("sudo apt update"))
    }

    @Test fun detectsPrompts() {
        assertTrue(Sudo.isPasswordPrompt("$ sudo docker ps\r\n[sudo] password for andi: "))
        assertTrue(Sudo.isPasswordPrompt("\u001b[?2004l\r[sudo] kata sandi untuk andi: "))
        assertTrue(Sudo.isPasswordPrompt("sudo id\r\nPassword:"))
        assertTrue(Sudo.isPasswordPrompt("\u001b[1;32m[sudo] password for root:\u001b[0m "))
        assertFalse(Sudo.isPasswordPrompt("CONTAINER ID   IMAGE\r\nabc123   nginx\r\n$ "))
        assertFalse(Sudo.isPasswordPrompt("echo password for later\r\nok\r\n$ "))
    }

    @Test fun stderrClassification() {
        assertTrue(Sudo.needsPassword("sudo: a password is required\n"))
        assertTrue(Sudo.wrongPassword("Sorry, try again.\nsudo: 1 incorrect password attempt"))
        assertTrue(Sudo.notAllowed("andi is not in the sudoers file.  This incident will be reported."))
    }
}
