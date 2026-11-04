package com.kroombox.termx.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleTest {
    @Test fun detectsTailnetAddresses() {
        assertTrue(Tailscale.isTailnetAddress("100.64.0.1"))
        assertTrue(Tailscale.isTailnetAddress("100.101.102.103"))
        assertTrue(Tailscale.isTailnetAddress("100.127.255.254"))
        assertTrue(Tailscale.isTailnetAddress("box.tail1234.ts.net"))
        assertTrue(Tailscale.isTailnetAddress("fd7a:115c:a1e0::1"))
        assertFalse(Tailscale.isTailnetAddress("100.128.0.1"))
        assertFalse(Tailscale.isTailnetAddress("100.63.0.1"))
        assertFalse(Tailscale.isTailnetAddress("192.168.1.10"))
        assertFalse(Tailscale.isTailnetAddress("example.com"))
        assertFalse(Tailscale.isTailnetAddress("100.64.0.999"))
    }
}
