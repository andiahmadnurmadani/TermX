package com.kroombox.termx.terminal

import java.util.ArrayDeque

interface TerminalListener {
    /** Kirim balasan (mis. respons DSR/DA) ke host. */
    fun onResponse(bytes: ByteArray) {}
    fun onScreenChanged() {}
    fun onTitleChanged(title: String) {}
    fun onBell() {}
    fun onClipboardSet(text: String) {}
}

enum class MouseMode { NONE, X10, NORMAL, BUTTON_EVENT, ANY_EVENT }

/**
 * Emulator terminal VT100/xterm yang cukup lengkap untuk shell, tmux, vim, htop, dan aplikasi TUI
 * seperti Claude Code. Thread-safe: semua akses dari luar harus memakai [lock].
 */
class TerminalEmulator(
    columns: Int,
    rows: Int,
    var scrollbackMax: Int = 5000,
    var listener: TerminalListener? = null,
) {
    val lock = Any()

    var cols = columns.coerceAtLeast(2); private set
    var rows = rows.coerceAtLeast(2); private set

    private var mainScreen = Array(this.rows) { TermLine(cols) }
    private var altScreen = Array(this.rows) { TermLine(cols) }
    private var screen = mainScreen
    val scrollback = ArrayDeque<TermLine>()

    var isAltScreen = false; private set

    // Kursor
    var cursorX = 0; private set
    var cursorY = 0; private set
    private var wrapPending = false
    var cursorVisible = true; private set
    /** 0 = blok, 1 = garis bawah, 2 = garis vertikal. */
    var cursorShape = 0; private set
    var cursorBlink = true; private set

    private var style: Long = Style.DEFAULT

    // Region scroll
    private var top = 0
    private var bottom = this.rows - 1

    // Mode
    var appCursorKeys = false; private set
    var appKeypad = false; private set
    var bracketedPaste = false; private set
    var reverseVideo = false; private set
    var mouseMode = MouseMode.NONE; private set
    var mouseSgr = false; private set
    var focusEvents = false; private set
    private var autoWrap = true
    private var originMode = false
    private var insertMode = false
    private var newLineMode = false

    // Charset: G0/G1, true = DEC special graphics
    private var g0Graphics = false
    private var g1Graphics = false
    private var useG1 = false

    private var tabStops = BooleanArray(cols) { it % 8 == 0 }

    var title: String = ""; private set

    /** Bertambah setiap kali baris terdorong ke scrollback; dipakai view untuk menjaga posisi gulir. */
    var scrollCounter = 0L; private set

    private class SavedCursor(
        val x: Int, val y: Int, val style: Long, val g0: Boolean, val g1: Boolean,
        val useG1: Boolean, val origin: Boolean, val wrapPending: Boolean, val autoWrap: Boolean,
    )

    private var savedMain: SavedCursor? = null
    private var savedAlt: SavedCursor? = null

    // ---------------------------------------------------------------- Parser
    private enum class State { GROUND, ESC, ESC_INTER, CSI, OSC, OSC_ESC, STR, STR_ESC }

    private var state = State.GROUND
    private var escInter = 0
    private val csiParams = ArrayList<IntArray>()
    private var csiCur = ArrayList<Int>()
    private var csiNum = -1
    private var csiPrivate = 0
    private var csiInter = 0
    private val oscBuf = StringBuilder()

    // UTF-8
    private var utf8Need = 0
    private var utf8Cp = 0
    private var utf8Min = 0

    private var lastPrinted = ' '.code

    // ---------------------------------------------------------------- Akses baca
    val scrollbackSize: Int get() = scrollback.size
    val totalLines: Int get() = scrollback.size + rows

    fun screenLine(y: Int): TermLine = screen[y]

    /** Baris absolut: 0 = baris scrollback paling tua; scrollbackSize = baris layar teratas. */
    fun lineAt(abs: Int): TermLine? {
        val sb = scrollback.size
        return when {
            abs < 0 -> null
            abs < sb -> scrollback.elementAtFast(abs)
            abs < sb + rows -> screen[abs - sb]
            else -> null
        }
    }

    private var sbCache: Array<TermLine>? = null
    private var sbCacheSize = -1
    private var sbCacheCounter = -1L

    private fun ArrayDeque<TermLine>.elementAtFast(i: Int): TermLine {
        if (sbCache == null || sbCacheSize != size || sbCacheCounter != scrollCounter) {
            sbCache = this.toTypedArray()
            sbCacheSize = size
            sbCacheCounter = scrollCounter
        }
        return sbCache!![i]
    }

    fun screenText(): String = synchronized(lock) {
        buildString {
            for (y in 0 until rows) {
                append(screen[y].text().trimEnd())
                if (y < rows - 1) append('\n')
            }
        }
    }

    /** Ambil teks dari rentang absolut (baris, kolom) inklusif-eksklusif. */
    fun textRange(r1: Int, c1: Int, r2: Int, c2: Int): String = synchronized(lock) {
        val sb = StringBuilder()
        for (r in r1..r2) {
            val line = lineAt(r) ?: continue
            val from = if (r == r1) c1 else 0
            val to = if (r == r2) c2 else line.size
            sb.append(line.text(from, to).trimEnd())
            if (r != r2 && !line.wrapped) sb.append('\n')
        }
        sb.toString()
    }

    // ---------------------------------------------------------------- Input
    fun append(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        synchronized(lock) {
            for (i in offset until offset + length) feedByte(data[i].toInt() and 0xFF)
        }
        listener?.onScreenChanged()
    }

    fun append(text: String) = append(text.toByteArray(Charsets.UTF_8))

    private fun feedByte(b: Int) {
        // Dekode UTF-8 hanya di state yang menerima teks.
        if (utf8Need > 0) {
            if (b and 0xC0 == 0x80) {
                utf8Cp = (utf8Cp shl 6) or (b and 0x3F)
                utf8Need--
                if (utf8Need == 0) {
                    val cp = if (utf8Cp < utf8Min || utf8Cp > 0x10FFFF || utf8Cp in 0xD800..0xDFFF) 0xFFFD else utf8Cp
                    processCodePoint(cp)
                }
                return
            } else {
                utf8Need = 0
                processCodePoint(0xFFFD)
                // lanjutkan proses byte ini sebagai awal baru
            }
        }
        when {
            b < 0x80 -> processCodePoint(b)
            b and 0xE0 == 0xC0 -> { utf8Need = 1; utf8Cp = b and 0x1F; utf8Min = 0x80 }
            b and 0xF0 == 0xE0 -> { utf8Need = 2; utf8Cp = b and 0x0F; utf8Min = 0x800 }
            b and 0xF8 == 0xF0 -> { utf8Need = 3; utf8Cp = b and 0x07; utf8Min = 0x10000 }
            else -> processCodePoint(0xFFFD)
        }
    }

    private fun processCodePoint(cp: Int) {
        // Kontrol C0 diproses di hampir semua state (kecuali string OSC/DCS).
        when (state) {
            State.OSC -> {
                when (cp) {
                    0x07 -> { finishOsc(); state = State.GROUND }
                    0x1B -> state = State.OSC_ESC
                    else -> if (oscBuf.length < 100_000) oscBuf.appendCodePoint(cp)
                }
                return
            }
            State.OSC_ESC -> {
                if (cp == '\\'.code) { finishOsc(); state = State.GROUND }
                else { finishOsc(); state = State.ESC; processCodePoint(cp) }
                return
            }
            State.STR -> {
                when (cp) {
                    0x07 -> state = State.GROUND
                    0x1B -> state = State.STR_ESC
                }
                return
            }
            State.STR_ESC -> {
                state = if (cp == '\\'.code) State.GROUND else State.STR
                return
            }
            else -> {}
        }

        if (cp < 0x20 || cp == 0x7F) {
            if (cp == 0x1B) {
                state = State.ESC
                escInter = 0
                return
            }
            if (cp == 0x18 || cp == 0x1A) { state = State.GROUND; return } // CAN/SUB
            executeControl(cp)
            return
        }

        when (state) {
            State.GROUND -> printCodePoint(cp)
            State.ESC -> handleEsc(cp)
            State.ESC_INTER -> handleEscInter(cp)
            State.CSI -> handleCsi(cp)
            else -> state = State.GROUND
        }
    }

    private fun executeControl(cp: Int) {
        when (cp) {
            0x07 -> listener?.onBell()
            0x08 -> { wrapPending = false; if (cursorX > 0) cursorX-- }
            0x09 -> tabForward(1)
            0x0A, 0x0B, 0x0C -> { lineFeed(); if (newLineMode) cursorX = 0 }
            0x0D -> { cursorX = 0; wrapPending = false }
            0x0E -> useG1 = true
            0x0F -> useG1 = false
        }
    }

    // ---------------------------------------------------------------- Cetak
    private fun mapGraphics(cp: Int): Int {
        val graphics = if (useG1) g1Graphics else g0Graphics
        if (!graphics || cp < 0x5F || cp > 0x7E) return cp
        return DEC_GRAPHICS[cp - 0x5F]
    }

    private fun printCodePoint(raw: Int) {
        val cp = mapGraphics(raw)
        val w = WcWidth.of(cp)
        if (w == 0) return // karakter penggabung diabaikan
        if (wrapPending && autoWrap) {
            screen[cursorY].wrapped = true
            cursorX = 0
            lineFeed()
        }
        wrapPending = false
        if (w == 2 && cursorX == cols - 1) {
            if (autoWrap) {
                screen[cursorY].clearRange(cursorX, cols, Style.eraseStyle(style))
                screen[cursorY].wrapped = true
                cursorX = 0
                lineFeed()
            } else return
        }
        val line = screen[cursorY]
        if (insertMode) insertCells(line, cursorX, w)
        // Jika menimpa separuh karakter lebar, bersihkan pasangannya.
        fixWideOverwrite(line, cursorX)
        line.chars[cursorX] = cp
        line.styles[cursorX] = if (w == 2) Style.withAttrs(style, Style.attrs(style) or Style.WIDE) else style
        if (w == 2) {
            fixWideOverwrite(line, cursorX + 1)
            line.chars[cursorX + 1] = ' '.code
            line.styles[cursorX + 1] = Style.withAttrs(style, Style.attrs(style) or Style.WIDE_CONT)
        }
        lastPrinted = cp
        cursorX += w
        if (cursorX >= cols) {
            cursorX = cols - 1
            wrapPending = true
        }
    }

    private fun fixWideOverwrite(line: TermLine, x: Int) {
        if (x < 0 || x >= cols) return
        val a = Style.attrs(line.styles[x])
        if (a and Style.WIDE_CONT != 0 && x > 0) {
            line.chars[x - 1] = ' '.code
            line.styles[x - 1] = Style.withAttrs(line.styles[x - 1], Style.attrs(line.styles[x - 1]) and Style.WIDE.inv())
        } else if (a and Style.WIDE != 0 && x + 1 < cols) {
            line.chars[x + 1] = ' '.code
            line.styles[x + 1] = Style.withAttrs(line.styles[x + 1], Style.attrs(line.styles[x + 1]) and Style.WIDE_CONT.inv())
        }
    }

    private fun insertCells(line: TermLine, x: Int, n: Int) {
        val count = n.coerceAtMost(cols - x)
        if (count <= 0) return
        System.arraycopy(line.chars, x, line.chars, x + count, cols - x - count)
        System.arraycopy(line.styles, x, line.styles, x + count, cols - x - count)
        line.clearRange(x, x + count, Style.eraseStyle(style))
    }

    // ---------------------------------------------------------------- Gerak & scroll
    private fun lineFeed() {
        wrapPending = false
        if (cursorY == bottom) scrollUp(1)
        else if (cursorY < rows - 1) cursorY++
    }

    private fun reverseIndex() {
        wrapPending = false
        if (cursorY == top) scrollDown(1)
        else if (cursorY > 0) cursorY--
    }

    private fun newBlankLine(): TermLine = TermLine(cols).also { it.clear(Style.eraseStyle(style)) }

    private fun scrollUp(n: Int, allowScrollback: Boolean = true) {
        val count = n.coerceIn(1, bottom - top + 1)
        repeat(count) {
            val removed = screen[top]
            if (allowScrollback && !isAltScreen && top == 0 && bottom == rows - 1 && scrollbackMax > 0) {
                scrollback.addLast(removed)
                scrollCounter++
                while (scrollback.size > scrollbackMax) scrollback.removeFirst()
                System.arraycopy(screen, top + 1, screen, top, bottom - top)
                screen[bottom] = newBlankLine()
            } else {
                System.arraycopy(screen, top + 1, screen, top, bottom - top)
                removed.clear(Style.eraseStyle(style))
                screen[bottom] = removed
            }
        }
    }

    private fun scrollDown(n: Int) {
        val count = n.coerceIn(1, bottom - top + 1)
        repeat(count) {
            val removed = screen[bottom]
            System.arraycopy(screen, top, screen, top + 1, bottom - top)
            removed.clear(Style.eraseStyle(style))
            screen[top] = removed
        }
    }

    private fun setCursor(x: Int, y: Int) {
        wrapPending = false
        cursorX = x.coerceIn(0, cols - 1)
        val minY = if (originMode) top else 0
        val maxY = if (originMode) bottom else rows - 1
        cursorY = y.coerceIn(minY, maxY)
    }

    private fun tabForward(n: Int) {
        var left = n
        while (left > 0 && cursorX < cols - 1) {
            cursorX++
            if (tabStops[cursorX]) left--
        }
        wrapPending = false
    }

    private fun tabBackward(n: Int) {
        var left = n
        while (left > 0 && cursorX > 0) {
            cursorX--
            if (tabStops[cursorX]) left--
        }
        wrapPending = false
    }

    // ---------------------------------------------------------------- ESC
    private fun handleEsc(cp: Int) {
        state = State.GROUND
        when (cp.toChar()) {
            '[' -> { state = State.CSI; csiParams.clear(); csiCur = ArrayList(); csiNum = -1; csiPrivate = 0; csiInter = 0 }
            ']' -> { state = State.OSC; oscBuf.setLength(0) }
            'P', 'X', '^', '_' -> state = State.STR
            '(', ')', '*', '+', '#', '%', ' ' -> { escInter = cp; state = State.ESC_INTER }
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'D' -> lineFeed()
            'E' -> { cursorX = 0; lineFeed() }
            'H' -> tabStops[cursorX] = true
            'M' -> reverseIndex()
            'c' -> fullReset()
            '=' -> appKeypad = true
            '>' -> appKeypad = false
            '\\' -> {}
            else -> {}
        }
    }

    private fun handleEscInter(cp: Int) {
        state = State.GROUND
        when (escInter.toChar()) {
            '(' -> g0Graphics = cp == '0'.code
            ')' -> g1Graphics = cp == '0'.code
            '#' -> if (cp == '8'.code) {
                for (y in 0 until rows) for (x in 0 until cols) {
                    screen[y].chars[x] = 'E'.code
                    screen[y].styles[x] = Style.DEFAULT
                }
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- CSI
    private fun handleCsi(cp: Int) {
        val c = cp.toChar()
        when {
            c in '0'..'9' -> {
                csiNum = if (csiNum < 0) (c - '0') else (csiNum * 10 + (c - '0')).coerceAtMost(1_000_000)
            }
            c == ';' -> {
                csiCur.add(csiNum); csiNum = -1
                csiParams.add(csiCur.toIntArray()); csiCur = ArrayList()
            }
            c == ':' -> { csiCur.add(csiNum); csiNum = -1 }
            c in '<'..'?' -> {
                if (csiParams.isEmpty() && csiCur.isEmpty() && csiNum < 0) csiPrivate = cp
            }
            c in ' '..'/' -> csiInter = cp
            c in '@'..'~' -> {
                csiCur.add(csiNum)
                csiParams.add(csiCur.toIntArray())
                state = State.GROUND
                dispatchCsi(cp)
            }
            else -> state = State.GROUND
        }
    }

    private fun p(i: Int, def: Int): Int {
        if (i >= csiParams.size) return def
        val v = csiParams[i][0]
        return if (v < 0) def else v
    }

    /** Parameter dengan nilai minimum 1 (untuk hitungan gerak). */
    private fun p1(i: Int) = p(i, 1).coerceAtLeast(1)

    private fun dispatchCsi(cmd: Int) {
        val c = cmd.toChar()
        if (csiPrivate == '?'.code) { dispatchPrivate(c); return }
        if (csiPrivate == '>'.code) {
            if (c == 'c') listener?.onResponse("\u001b[>0;10;1c".toByteArray())
            return
        }
        if (csiPrivate != 0) return
        if (csiInter == ' '.code) {
            if (c == 'q') {
                when (p(0, 1)) {
                    0, 1 -> { cursorShape = 0; cursorBlink = true }
                    2 -> { cursorShape = 0; cursorBlink = false }
                    3 -> { cursorShape = 1; cursorBlink = true }
                    4 -> { cursorShape = 1; cursorBlink = false }
                    5 -> { cursorShape = 2; cursorBlink = true }
                    6 -> { cursorShape = 2; cursorBlink = false }
                }
            }
            return
        }
        if (csiInter == '!'.code) { if (c == 'p') softReset(); return }
        if (csiInter != 0) return

        when (c) {
            '@' -> { wrapPending = false; insertCells(screen[cursorY], cursorX, p1(0)) }
            'A' -> setCursorKeepOrigin(cursorX, (cursorY - p1(0)).coerceAtLeast(if (cursorY >= top) top else 0))
            'B' -> setCursorKeepOrigin(cursorX, (cursorY + p1(0)).coerceAtMost(if (cursorY <= bottom) bottom else rows - 1))
            'C', 'a' -> { cursorX = (cursorX + p1(0)).coerceAtMost(cols - 1); wrapPending = false }
            'D' -> { cursorX = (cursorX - p1(0)).coerceAtLeast(0); wrapPending = false }
            'E' -> setCursorKeepOrigin(0, (cursorY + p1(0)).coerceAtMost(bottom))
            'F' -> setCursorKeepOrigin(0, (cursorY - p1(0)).coerceAtLeast(top))
            'G', '`' -> { cursorX = (p1(0) - 1).coerceIn(0, cols - 1); wrapPending = false }
            'H', 'f' -> {
                val y = p1(0) - 1 + (if (originMode) top else 0)
                setCursor(p1(1) - 1, y)
            }
            'I' -> tabForward(p1(0))
            'J' -> eraseInDisplay(p(0, 0))
            'K' -> eraseInLine(p(0, 0))
            'L' -> if (cursorY in top..bottom) {
                val saveTop = top
                top = cursorY
                scrollDown(p1(0))
                top = saveTop
                cursorX = 0; wrapPending = false
            }
            'M' -> if (cursorY in top..bottom) {
                val saveTop = top
                top = cursorY
                // Hapus baris tanpa memasukkan ke scrollback.
                scrollUp(p1(0), allowScrollback = false)
                top = saveTop
                cursorX = 0; wrapPending = false
            }
            'P' -> {
                wrapPending = false
                val line = screen[cursorY]
                val n = p1(0).coerceAtMost(cols - cursorX)
                System.arraycopy(line.chars, cursorX + n, line.chars, cursorX, cols - cursorX - n)
                System.arraycopy(line.styles, cursorX + n, line.styles, cursorX, cols - cursorX - n)
                line.clearRange(cols - n, cols, Style.eraseStyle(style))
            }
            'S' -> scrollUp(p1(0))
            'T' -> scrollDown(p1(0))
            'X' -> { wrapPending = false; screen[cursorY].clearRange(cursorX, cursorX + p1(0), Style.eraseStyle(style)) }
            'Z' -> tabBackward(p1(0))
            'b' -> repeat(p1(0).coerceAtMost(cols * rows)) { printCodePoint(lastPrinted) }
            'c' -> if (p(0, 0) == 0) listener?.onResponse("\u001b[?62;22c".toByteArray())
            'd' -> setCursor(cursorX, p1(0) - 1 + (if (originMode) top else 0))
            'e' -> setCursor(cursorX, cursorY + p1(0))
            'g' -> when (p(0, 0)) {
                0 -> tabStops[cursorX] = false
                3 -> tabStops.fill(false)
            }
            'h' -> setAnsiMode(true)
            'l' -> setAnsiMode(false)
            'm' -> selectGraphicRendition()
            'n' -> when (p(0, 0)) {
                5 -> listener?.onResponse("\u001b[0n".toByteArray())
                6 -> {
                    val y = cursorY + 1 - (if (originMode) top else 0)
                    listener?.onResponse("\u001b[$y;${cursorX + 1}R".toByteArray())
                }
            }
            'r' -> {
                val t = p1(0) - 1
                val b = (if (p(1, 0) == 0) rows else p(1, rows)) - 1
                if (t < b && b < rows) {
                    top = t; bottom = b
                    setCursor(0, if (originMode) top else 0)
                }
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            't' -> when (p(0, 0)) {
                18 -> listener?.onResponse("\u001b[8;$rows;${cols}t".toByteArray())
            }
        }
    }

    private fun setCursorKeepOrigin(x: Int, y: Int) {
        wrapPending = false
        cursorX = x.coerceIn(0, cols - 1)
        cursorY = y.coerceIn(0, rows - 1)
    }

    private fun setAnsiMode(on: Boolean) {
        for (group in csiParams) when (group[0]) {
            4 -> insertMode = on
            20 -> newLineMode = on
        }
    }

    private fun dispatchPrivate(c: Char) {
        when (c) {
            'h', 'l' -> {
                val on = c == 'h'
                for (group in csiParams) setDecMode(group[0], on)
            }
            'n' -> if (p(0, 0) == 6) {
                listener?.onResponse("\u001b[?${cursorY + 1};${cursorX + 1}R".toByteArray())
            }
            'J' -> eraseInDisplay(p(0, 0))
            'K' -> eraseInLine(p(0, 0))
        }
    }

    private fun setDecMode(mode: Int, on: Boolean) {
        when (mode) {
            1 -> appCursorKeys = on
            5 -> reverseVideo = on
            6 -> { originMode = on; setCursor(0, if (on) top else 0) }
            7 -> autoWrap = on
            9 -> mouseMode = if (on) MouseMode.X10 else MouseMode.NONE
            12 -> cursorBlink = on
            25 -> cursorVisible = on
            47, 1047 -> switchScreen(on, clear = mode == 1047 && !on)
            1048 -> if (on) saveCursor() else restoreCursor()
            1049 -> {
                if (on) {
                    saveCursor()
                    switchScreen(true, clear = false)
                    clearScreen(altScreen)
                } else {
                    switchScreen(false, clear = false)
                    restoreCursor()
                }
            }
            1000 -> mouseMode = if (on) MouseMode.NORMAL else MouseMode.NONE
            1002 -> mouseMode = if (on) MouseMode.BUTTON_EVENT else MouseMode.NONE
            1003 -> mouseMode = if (on) MouseMode.ANY_EVENT else MouseMode.NONE
            1004 -> focusEvents = on
            1006 -> mouseSgr = on
            2004 -> bracketedPaste = on
        }
    }

    private fun clearScreen(s: Array<TermLine>) {
        val es = Style.eraseStyle(style)
        for (line in s) line.clear(es)
    }

    private fun switchScreen(alt: Boolean, clear: Boolean) {
        if (alt == isAltScreen) return
        if (alt) {
            screen = altScreen
            isAltScreen = true
        } else {
            if (clear) clearScreen(altScreen)
            screen = mainScreen
            isAltScreen = false
        }
        wrapPending = false
        cursorY = cursorY.coerceIn(0, rows - 1)
    }

    private fun eraseInDisplay(mode: Int) {
        wrapPending = false
        val es = Style.eraseStyle(style)
        when (mode) {
            0 -> {
                screen[cursorY].clearRange(cursorX, cols, es)
                for (y in cursorY + 1 until rows) screen[y].clear(es)
            }
            1 -> {
                for (y in 0 until cursorY) screen[y].clear(es)
                screen[cursorY].clearRange(0, cursorX + 1, es)
            }
            2 -> for (y in 0 until rows) screen[y].clear(es)
            3 -> { scrollback.clear(); scrollCounter++ }
        }
    }

    private fun eraseInLine(mode: Int) {
        wrapPending = false
        val es = Style.eraseStyle(style)
        val line = screen[cursorY]
        when (mode) {
            0 -> { line.clearRange(cursorX, cols, es); line.wrapped = false }
            1 -> line.clearRange(0, cursorX + 1, es)
            2 -> line.clear(es)
        }
    }

    private fun selectGraphicRendition() {
        var i = 0
        var fg = Style.fg(style)
        var bg = Style.bg(style)
        var a = Style.attrs(style)
        while (i < csiParams.size) {
            val group = csiParams[i]
            val code = if (group[0] < 0) 0 else group[0]
            when (code) {
                0 -> { fg = 0; bg = 0; a = 0 }
                1 -> a = a or Style.BOLD
                2 -> a = a or Style.DIM
                3 -> a = a or Style.ITALIC
                4 -> a = if (group.size > 1 && group[1] == 0) a and Style.UNDERLINE.inv() else a or Style.UNDERLINE
                5, 6 -> a = a or Style.BLINK
                7 -> a = a or Style.INVERSE
                8 -> a = a or Style.INVISIBLE
                9 -> a = a or Style.STRIKE
                21 -> a = a or Style.UNDERLINE
                22 -> a = a and (Style.BOLD or Style.DIM).inv()
                23 -> a = a and Style.ITALIC.inv()
                24 -> a = a and Style.UNDERLINE.inv()
                25 -> a = a and Style.BLINK.inv()
                27 -> a = a and Style.INVERSE.inv()
                28 -> a = a and Style.INVISIBLE.inv()
                29 -> a = a and Style.STRIKE.inv()
                in 30..37 -> fg = Style.indexed(code - 30)
                39 -> fg = 0
                in 40..47 -> bg = Style.indexed(code - 40)
                49 -> bg = 0
                in 90..97 -> fg = Style.indexed(code - 90 + 8)
                in 100..107 -> bg = Style.indexed(code - 100 + 8)
                38, 48, 58 -> {
                    var color = -1
                    if (group.size > 1) {
                        // Bentuk titik dua: 38:5:n atau 38:2:[cs:]r:g:b
                        when (group[1]) {
                            5 -> if (group.size > 2) color = Style.indexed(group[2].coerceAtLeast(0))
                            2 -> if (group.size >= 5) {
                                val n = group.size
                                color = Style.rgb(group[n - 3].coerceAtLeast(0), group[n - 2].coerceAtLeast(0), group[n - 1].coerceAtLeast(0))
                            }
                        }
                    } else if (i + 1 < csiParams.size) {
                        when (csiParams[i + 1][0]) {
                            5 -> if (i + 2 < csiParams.size) {
                                color = Style.indexed(csiParams[i + 2][0].coerceAtLeast(0))
                                i += 2
                            } else i = csiParams.size
                            2 -> if (i + 4 < csiParams.size) {
                                color = Style.rgb(
                                    csiParams[i + 2][0].coerceAtLeast(0),
                                    csiParams[i + 3][0].coerceAtLeast(0),
                                    csiParams[i + 4][0].coerceAtLeast(0),
                                )
                                i += 4
                            } else i = csiParams.size
                            else -> i++
                        }
                    }
                    if (color >= 0) {
                        if (code == 38) fg = color else if (code == 48) bg = color
                    }
                }
            }
            i++
        }
        style = Style.pack(fg, bg, a)
    }

    // ---------------------------------------------------------------- OSC
    private fun finishOsc() {
        val s = oscBuf.toString()
        val sep = s.indexOf(';')
        val code = (if (sep >= 0) s.substring(0, sep) else s).toIntOrNull() ?: return
        val arg = if (sep >= 0) s.substring(sep + 1) else ""
        when (code) {
            0, 2 -> { title = arg; listener?.onTitleChanged(arg) }
            10, 11 -> if (arg == "?") {
                val rgb = if (code == 10) "e6e6/edede/f3f3" else "0e0e/1111/1616"
                listener?.onResponse("\u001b]$code;rgb:$rgb\u001b\\".toByteArray())
            }
            52 -> {
                val p = arg.indexOf(';')
                val data = if (p >= 0) arg.substring(p + 1) else arg
                if (data != "?") {
                    try {
                        val text = String(java.util.Base64.getDecoder().decode(data), Charsets.UTF_8)
                        listener?.onClipboardSet(text)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Simpan / reset
    private fun saveCursor() {
        val s = SavedCursor(cursorX, cursorY, style, g0Graphics, g1Graphics, useG1, originMode, wrapPending, autoWrap)
        if (isAltScreen) savedAlt = s else savedMain = s
    }

    private fun restoreCursor() {
        val s = (if (isAltScreen) savedAlt else savedMain)
        if (s == null) {
            cursorX = 0; cursorY = 0; style = Style.DEFAULT; wrapPending = false
            return
        }
        cursorX = s.x.coerceIn(0, cols - 1)
        cursorY = s.y.coerceIn(0, rows - 1)
        style = s.style
        g0Graphics = s.g0; g1Graphics = s.g1; useG1 = s.useG1
        originMode = s.origin
        wrapPending = s.wrapPending
        autoWrap = s.autoWrap
    }

    private fun softReset() {
        cursorVisible = true
        originMode = false
        autoWrap = true
        insertMode = false
        appCursorKeys = false
        appKeypad = false
        top = 0; bottom = rows - 1
        style = Style.DEFAULT
        g0Graphics = false; g1Graphics = false; useG1 = false
        savedMain = null; savedAlt = null
    }

    fun fullReset() {
        synchronized(lock) {
            softReset()
            if (isAltScreen) switchScreen(false, clear = true)
            clearScreen(mainScreen)
            clearScreen(altScreen)
            scrollback.clear()
            scrollCounter++
            cursorX = 0; cursorY = 0; wrapPending = false
            bracketedPaste = false
            mouseMode = MouseMode.NONE
            mouseSgr = false
            reverseVideo = false
            cursorShape = 0
            tabStops = BooleanArray(cols) { it % 8 == 0 }
            state = State.GROUND
            utf8Need = 0
        }
    }

    // ---------------------------------------------------------------- Rekaman riwayat
    /**
     * Rekam isi terminal sebagai teks ANSI (warna & atribut ikut), untuk disimpan ke disk dan dimuat
     * lagi dengan [append] saat sesi dibuka kembali — sehingga riwayat tidak hilang walau tab ditutup
     * atau proses aplikasi dimatikan.
     *
     * Di layar utama: scrollback + layar. Di layar alternatif (tmux, vim, htop): hanya layar yang
     * tampil, karena riwayat lengkapnya disimpan program itu sendiri (tmux: di server).
     */
    fun snapshot(maxLines: Int = 3000): String = synchronized(lock) {
        val lines = ArrayList<TermLine>()
        if (!isAltScreen) lines.addAll(scrollback)
        lines.addAll(screen.asList())
        var last = lines.size - 1
        while (last >= 0 && lastContent(lines[last]) == 0 && !lines[last].wrapped) last--
        if (last < 0) return@synchronized ""
        val first = (last + 1 - maxLines).coerceAtLeast(0)
        val sb = StringBuilder()
        var cur = Style.DEFAULT
        for (i in first..last) {
            val line = lines[i]
            val end = if (line.wrapped) line.size else lastContent(line)
            for (x in 0 until end) {
                val st = line.styles[x]
                val a = Style.attrs(st)
                if (a and Style.WIDE_CONT != 0) continue
                val vis = Style.pack(Style.fg(st), Style.bg(st), a and (Style.WIDE or Style.WIDE_CONT).inv())
                if (vis != cur) { sb.append(sgr(vis)); cur = vis }
                sb.appendCodePoint(line.chars[x])
            }
            if (!line.wrapped || i == last) {
                if (cur != Style.DEFAULT) { sb.append("\u001b[0m"); cur = Style.DEFAULT }
                if (i < last) sb.append("\r\n")
            }
        }
        sb.toString()
    }

    private fun lastContent(line: TermLine): Int {
        for (x in line.size - 1 downTo 0) {
            val st = line.styles[x]
            if (line.chars[x] != ' '.code || Style.bg(st) != Style.COLOR_DEFAULT || Style.attrs(st) and Style.INVERSE != 0) return x + 1
        }
        return 0
    }

    private fun sgr(s: Long): String {
        val p = ArrayList<String>(8)
        p += "0"
        val a = Style.attrs(s)
        if (a and Style.BOLD != 0) p += "1"
        if (a and Style.DIM != 0) p += "2"
        if (a and Style.ITALIC != 0) p += "3"
        if (a and Style.UNDERLINE != 0) p += "4"
        if (a and Style.BLINK != 0) p += "5"
        if (a and Style.INVERSE != 0) p += "7"
        if (a and Style.INVISIBLE != 0) p += "8"
        if (a and Style.STRIKE != 0) p += "9"
        sgrColor(Style.fg(s), true, p)
        sgrColor(Style.bg(s), false, p)
        return "\u001b[" + p.joinToString(";") + "m"
    }

    private fun sgrColor(c: Int, fg: Boolean, p: MutableList<String>) {
        if (c == Style.COLOR_DEFAULT) return
        if (c and Style.TRUECOLOR_FLAG != 0) {
            p += if (fg) "38" else "48"
            p += "2"
            p += "${(c shr 16) and 0xFF}"
            p += "${(c shr 8) and 0xFF}"
            p += "${c and 0xFF}"
            return
        }
        val idx = c - 1
        when {
            idx < 8 -> p += "${(if (fg) 30 else 40) + idx}"
            idx < 16 -> p += "${(if (fg) 90 else 100) + idx - 8}"
            else -> { p += if (fg) "38" else "48"; p += "5"; p += "$idx" }
        }
    }

    // ---------------------------------------------------------------- Ukuran
    fun resize(newCols: Int, newRows: Int) {
        val nc = newCols.coerceAtLeast(2)
        val nr = newRows.coerceAtLeast(2)
        synchronized(lock) {
            if (nc == cols && nr == rows) return
            // Layar utama: dorong baris atas ke scrollback bila tinggi berkurang.
            mainScreen = resizeScreen(mainScreen, nc, nr, keepScrollback = true, isMain = true)
            altScreen = resizeScreen(altScreen, nc, nr, keepScrollback = false, isMain = false)
            screen = if (isAltScreen) altScreen else mainScreen
            for (line in scrollback) line.resize(nc)
            sbCache = null
            cols = nc
            rows = nr
            top = 0
            bottom = nr - 1
            tabStops = BooleanArray(nc) { it % 8 == 0 }
            cursorX = cursorX.coerceIn(0, nc - 1)
            cursorY = cursorY.coerceIn(0, nr - 1)
            wrapPending = false
        }
        listener?.onScreenChanged()
    }

    private fun resizeScreen(old: Array<TermLine>, nc: Int, nr: Int, keepScrollback: Boolean, isMain: Boolean): Array<TermLine> {
        val lines = old.toMutableList()
        lines.forEach { it.resize(nc) }
        val activeHere = isMain != isAltScreen
        if (nr < lines.size) {
            var excess = lines.size - nr
            // Buang baris kosong di bawah kursor dulu.
            while (excess > 0 && lines.size - 1 > (if (activeHere) cursorY else -1) && lines.last().text().isBlank()) {
                lines.removeAt(lines.size - 1); excess--
            }
            while (excess > 0) {
                val removed = lines.removeAt(0)
                if (keepScrollback && scrollbackMax > 0) {
                    scrollback.addLast(removed)
                    scrollCounter++
                }
                if (activeHere) cursorY--
                excess--
            }
            while (scrollback.size > scrollbackMax) scrollback.removeFirst()
        } else if (nr > lines.size) {
            var need = nr - lines.size
            while (need > 0 && keepScrollback && scrollback.isNotEmpty() && activeHere) {
                lines.add(0, scrollback.removeLast().also { it.resize(nc) })
                scrollCounter++
                cursorY++
                need--
            }
            while (lines.size < nr) lines.add(TermLine(nc))
        }
        if (activeHere) cursorY = cursorY.coerceIn(0, nr - 1)
        return lines.toTypedArray()
    }

    companion object {
        /** Peta DEC Special Graphics untuk 0x5F..0x7E. */
        private val DEC_GRAPHICS = intArrayOf(
            0x00A0, 0x25C6, 0x2592, 0x2409, 0x240C, 0x240D, 0x240A, 0x00B0, 0x00B1, 0x2424,
            0x240B, 0x2518, 0x2510, 0x250C, 0x2514, 0x253C, 0x23BA, 0x23BB, 0x2500, 0x23BC,
            0x23BD, 0x251C, 0x2524, 0x2534, 0x252C, 0x2502, 0x2264, 0x2265, 0x03C0, 0x2260,
            0x00A3, 0x00B7,
        )
    }
}
