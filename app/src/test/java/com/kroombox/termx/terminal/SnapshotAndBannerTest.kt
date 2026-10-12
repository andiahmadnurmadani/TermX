package com.kroombox.termx.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SnapshotAndBannerTest {
    @Test fun snapshotRoundTripKeepsTextAndColors() {
        val a = TerminalEmulator(40, 5, 100)
        a.append("plain line\r\n\u001b[31mred\u001b[0m and \u001b[1;38;2;1;2;3mrgb\u001b[0m\r\n")
        repeat(10) { a.append("line $it\r\n") }
        val snap = a.snapshot()
        val b = TerminalEmulator(40, 5, 100)
        b.append(snap)
        val textA = (0 until a.totalLines).joinToString("\n") { a.lineAt(it)!!.text().trimEnd() }.trimEnd()
        val textB = (0 until b.totalLines).joinToString("\n") { b.lineAt(it)!!.text().trimEnd() }.trimEnd()
        assertEquals(textA, textB)
        // Warna merah pada "red" ikut terbawa.
        val redLine = (0 until b.totalLines).map { b.lineAt(it)!! }.first { it.text().startsWith("red") }
        assertEquals(Style.indexed(1), Style.fg(redLine.styles[0]))
        val rgbIdx = redLine.text().indexOf("rgb")
        assertEquals(Style.rgb(1, 2, 3), Style.fg(redLine.styles[rgbIdx]))
        assertTrue(Style.attrs(redLine.styles[rgbIdx]) and Style.BOLD != 0)
    }

    @Test fun snapshotOfAltScreenHasOnlyVisibleScreen() {
        val e = TerminalEmulator(20, 3, 100)
        repeat(10) { e.append("old $it\r\n") }
        e.append("\u001b[?1049h\u001b[Htmux screen")
        val snap = e.snapshot()
        assertTrue(snap.contains("tmux screen"))
        assertTrue(!snap.contains("old 1"))
    }

    @Test fun bannerFitsNarrowAndWide() {
        val wide = Banner.render("root@srv", "Ubuntu 24.04 LTS", "terminal biasa", 80, Date(0))
        assertTrue(wide.contains("|_   _|"))
        assertTrue(wide.contains("root@srv"))
        val narrow = Banner.render("root@srv", null, "terminal biasa", 20, Date(0))
        assertTrue(!narrow.contains("|_   _|"))
        // Banner dapat ditulis ke emulator tanpa error.
        val e = TerminalEmulator(20, 10, 100)
        e.append(narrow)
        assertTrue(e.screenText().contains("Term"))
    }
}
