package com.kroombox.termx.os

import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsCatalogTest {
    @Test fun ubuntu() {
        val d = OsCatalog.parse("uname=Linux\nid=ubuntu\nlike=debian\npretty=\"Ubuntu 24.04.1 LTS\"\n")!!
        assertEquals("ubuntu", d.id)
        assertEquals("Ubuntu 24.04.1 LTS", d.name)
    }

    @Test fun derivativeFallsBackToFamily() {
        assertEquals("ubuntu", OsCatalog.parse("uname=Linux\nid=neon\nlike=\"ubuntu debian\"\n")!!.id)
        assertEquals("redhat", OsCatalog.parse("uname=Linux\nid=ol\nlike=\"rhel centos\"\n")!!.id)
    }

    @Test fun raspberryPiOnDebianId() {
        assertEquals("raspberrypi", OsCatalog.parse("uname=Linux\nid=debian\nmodel=Raspberry Pi 4 Model B Rev 1.4\n")!!.id)
    }

    @Test fun specials() {
        assertEquals("macos", OsCatalog.parse("uname=Darwin\npretty=macOS 14.5\n")!!.id)
        assertEquals("freebsd", OsCatalog.parse("uname=FreeBSD\n")!!.id)
        assertEquals("proxmox", OsCatalog.parse("uname=Linux\nid=debian\npve=1\n")!!.id)
        assertEquals("linux", OsCatalog.parse("uname=Linux\nid=somethingnew\n")!!.id)
        assertEquals("windows", OsCatalog.parse("", "'sh' is not recognized as an internal or external command")!!.id)
        assertNull(OsCatalog.parse(""))
    }

    @Test fun everyDetectedIdHasAnIcon() {
        listOf("ubuntu", "debian", "archlinux", "fedora", "redhat", "alpinelinux", "macos", "windows", "linux", "raspberrypi", "proxmox")
            .forEach { assertTrue(it, OsCatalog.byId(it) != null) }
    }

    @Test fun allIconPathsParse() {
        OsCatalog.all.forEach { os ->
            val nodes = addPathNodes(os.path)
            assertTrue("${os.id} path kosong", nodes.size > 3)
        }
    }
}
