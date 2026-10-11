package com.kroombox.konsol.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    private val responses = mutableListOf<String>()
    private val listener = object : TerminalListener {
        override fun onResponse(bytes: ByteArray) { responses.add(String(bytes)) }
    }

    private fun emu(cols: Int, rows: Int) = TerminalEmulator(cols, rows, listener = listener)

    @Test fun basicTextAndCursor() {
        val t = emu(10, 4)
        t.append("hello\r\nworld")
        assertEquals("hello", t.screenLine(0).text().trimEnd())
        assertEquals("world", t.screenLine(1).text().trimEnd())
        assertEquals(5, t.cursorX); assertEquals(1, t.cursorY)
    }

    @Test fun autowrapMarksWrappedLine() {
        val t = emu(10, 4)
        t.append("0123456789AB")
        assertEquals("0123456789", t.screenLine(0).text())
        assertEquals("AB", t.screenLine(1).text().trimEnd())
        assertTrue(t.screenLine(0).wrapped)
    }

    @Test fun pendingWrapThenCrLfDoesNotAddBlankLine() {
        val t = emu(4, 3)
        t.append("abcd\r\nef")
        assertEquals("abcd\nef\n", t.screenText())
    }

    @Test fun sgrColors() {
        val t = emu(10, 2)
        t.append("\u001b[1;31mR\u001b[38;2;10;20;30mT\u001b[48:5:200mU\u001b[0m")
        val r = t.screenLine(0).styles[0]
        assertEquals(Style.indexed(1), Style.fg(r))
        assertTrue(Style.attrs(r) and Style.BOLD != 0)
        assertEquals(Style.rgb(10, 20, 30), Style.fg(t.screenLine(0).styles[1]))
        assertEquals(Style.indexed(200), Style.bg(t.screenLine(0).styles[2]))
    }

    @Test fun scrollbackAndAltScreen() {
        val t = emu(5, 3)
        t.append("a\r\nb\r\nc\r\nd\r\ne")
        assertEquals(2, t.scrollbackSize)
        assertEquals("a", t.lineAt(0)!!.text().trimEnd())
        assertEquals("c\nd\ne", t.screenText())
        t.append("\u001b[?1049h\u001b[HX")
        assertTrue(t.isAltScreen)
        assertEquals("X", t.screenLine(0).text().trimEnd())
        t.append("\u001b[?1049l")
        assertFalse(t.isAltScreen)
        assertEquals("c\nd\ne", t.screenText())
        assertEquals(1, t.cursorX); assertEquals(2, t.cursorY)
    }

    @Test fun cursorPositionReport() {
        val t = emu(10, 4)
        t.append("\u001b[2;3H\u001b[6n")
        assertEquals("\u001b[2;3R", responses.last())
    }

    @Test fun scrollRegionKeepsStatusLine() {
        val t = emu(6, 4)
        t.append("\u001b[4;1Hstatus\u001b[1;3r\u001b[1;1H1\r\n2\r\n3\r\n4")
        assertEquals("2\n3\n4\nstatus", t.screenText())
        assertEquals(0, t.scrollbackSize)
    }

    @Test fun insertDeleteLinesAndChars() {
        var t = emu(4, 4)
        t.append("a\r\nb\r\nc\r\nd\u001b[2;1H\u001b[L")
        assertEquals("a\n\nb\nc", t.screenText())
        t.append("\u001b[M")
        assertEquals("a\nb\nc\n", t.screenText())
        t = emu(8, 2)
        t.append("abcdef\u001b[1;2H\u001b[2P")
        assertEquals("adef", t.screenLine(0).text().trimEnd())
        t.append("\u001b[2@")
        assertEquals("a  def", t.screenLine(0).text().trimEnd())
        t.append("\u001b[1;1H\u001b[3X")
        assertEquals("   def", t.screenLine(0).text().trimEnd())
    }

    @Test fun decGraphicsAndUtf8Wide() {
        var t = emu(8, 2)
        t.append("\u001b(0lqk\u001b(Bx")
        assertEquals("┌─┐x", t.screenLine(0).text().trimEnd())
        t = emu(8, 2)
        val bytes = "a漢b".toByteArray()
        t.append(bytes, 0, 2); t.append(bytes, 2, bytes.size - 2)
        assertEquals("a漢b", t.screenLine(0).text().trimEnd())
        assertEquals(4, t.cursorX)
    }

    @Test fun oscTitle() {
        var title = ""
        val t = TerminalEmulator(8, 2, listener = object : TerminalListener {
            override fun onTitleChanged(title2: String) { title = title2 }
        })
        t.append("\u001b]0;my title\u0007")
        assertEquals("my title", title)
        t.append("\u001b]2;t2\u001b\\")
        assertEquals("t2", title)
    }

    @Test fun resizeMovesLinesToScrollback() {
        val t = emu(5, 4)
        t.append("1\r\n2\r\n3\r\n4")
        t.resize(5, 2)
        assertEquals("3\n4", t.screenText())
        assertEquals(2, t.scrollbackSize)
        t.resize(5, 4)
        assertEquals("1\n2\n3\n4", t.screenText())
        assertEquals(0, t.scrollbackSize)
    }

    @Test fun privateModes() {
        val t = emu(5, 4)
        t.append("\u001b[?1h\u001b[?2004h\u001b[?25l\u001b[?1000h\u001b[?1006h")
        assertTrue(t.appCursorKeys); assertTrue(t.bracketedPaste); assertFalse(t.cursorVisible)
        assertEquals(MouseMode.NORMAL, t.mouseMode); assertTrue(t.mouseSgr)
    }

    @Test fun keyEncoding() {
        assertEquals("\u001b[A", KeyEncoder.encode(TermKey.UP, 0, false))
        assertEquals("\u001bOA", KeyEncoder.encode(TermKey.UP, 0, true))
        assertEquals("\u001b[1;5C", KeyEncoder.encode(TermKey.RIGHT, Mods.CTRL, false))
        assertEquals("\u0003", KeyEncoder.encodeChar('c'.code, Mods.CTRL))
        assertEquals("\u001bx", KeyEncoder.encodeChar('x'.code, Mods.ALT))
        assertEquals("\u001b[<64;3;2M", KeyEncoder.mouseEvent(64, 2, 1, true, true))
    }
}
