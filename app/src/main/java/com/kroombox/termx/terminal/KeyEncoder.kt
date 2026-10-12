package com.kroombox.termx.terminal

enum class TermKey {
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT, DELETE,
    ESC, TAB, ENTER, BACKSPACE,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}

object Mods {
    const val SHIFT = 1
    const val ALT = 2
    const val CTRL = 4
}

/** Menerjemahkan tombol menjadi urutan byte xterm. */
object KeyEncoder {
    private const val ESC = "\u001b"

    /** Nilai modifier xterm: 1 + shift(1) + alt(2) + ctrl(4). */
    private fun modParam(mods: Int): Int = 1 + mods

    fun encode(key: TermKey, mods: Int, appCursor: Boolean): String {
        val m = modParam(mods)
        fun cursor(c: Char): String = when {
            mods != 0 -> "$ESC[1;$m$c"
            appCursor -> "${ESC}O$c"
            else -> "$ESC[$c"
        }
        fun tilde(n: Int): String = if (mods != 0) "$ESC[$n;$m~" else "$ESC[$n~"
        fun ss3(c: Char): String = if (mods != 0) "$ESC[1;$m$c" else "${ESC}O$c"
        return when (key) {
            TermKey.UP -> cursor('A')
            TermKey.DOWN -> cursor('B')
            TermKey.RIGHT -> cursor('C')
            TermKey.LEFT -> cursor('D')
            TermKey.HOME -> cursor('H')
            TermKey.END -> cursor('F')
            TermKey.PAGE_UP -> tilde(5)
            TermKey.PAGE_DOWN -> tilde(6)
            TermKey.INSERT -> tilde(2)
            TermKey.DELETE -> tilde(3)
            TermKey.ESC -> if (mods and Mods.ALT != 0) "$ESC$ESC" else ESC
            TermKey.TAB -> if (mods and Mods.SHIFT != 0) "$ESC[Z" else if (mods and Mods.ALT != 0) "$ESC\t" else "\t"
            TermKey.ENTER -> if (mods and Mods.ALT != 0) "$ESC\r" else "\r"
            TermKey.BACKSPACE -> when {
                mods and Mods.CTRL != 0 -> "\u0008"
                mods and Mods.ALT != 0 -> "$ESC\u007f"
                else -> "\u007f"
            }
            TermKey.F1 -> ss3('P')
            TermKey.F2 -> ss3('Q')
            TermKey.F3 -> ss3('R')
            TermKey.F4 -> ss3('S')
            TermKey.F5 -> tilde(15)
            TermKey.F6 -> tilde(17)
            TermKey.F7 -> tilde(18)
            TermKey.F8 -> tilde(19)
            TermKey.F9 -> tilde(20)
            TermKey.F10 -> tilde(21)
            TermKey.F11 -> tilde(23)
            TermKey.F12 -> tilde(24)
        }
    }

    /** Menerapkan Ctrl/Alt pada karakter biasa. */
    fun encodeChar(codePoint: Int, mods: Int): String {
        var cp = codePoint
        if (mods and Mods.CTRL != 0) {
            cp = when {
                cp in 'a'.code..'z'.code -> cp - 'a'.code + 1
                cp in 'A'.code..'Z'.code -> cp - 'A'.code + 1
                cp == ' '.code || cp == '2'.code || cp == '@'.code -> 0
                cp == '['.code || cp == '3'.code -> 27
                cp == '\\'.code || cp == '4'.code -> 28
                cp == ']'.code || cp == '5'.code -> 29
                cp == '^'.code || cp == '6'.code -> 30
                cp == '_'.code || cp == '-'.code || cp == '7'.code || cp == '/'.code -> 31
                cp == '?'.code || cp == '8'.code -> 127
                else -> cp
            }
        }
        val s = String(Character.toChars(cp))
        return if (mods and Mods.ALT != 0) ESC + s else s
    }

    /** Laporan mouse untuk wheel / klik (format SGR bila aktif). */
    fun mouseEvent(button: Int, col: Int, row: Int, press: Boolean, sgr: Boolean): String {
        val x = col + 1
        val y = row + 1
        return if (sgr) {
            "$ESC[<$button;$x;$y${if (press) 'M' else 'm'}"
        } else {
            val b = if (press) button else 3
            val cb = (32 + b).coerceAtMost(255).toChar()
            val cx = (32 + x).coerceAtMost(255).toChar()
            val cy = (32 + y).coerceAtMost(255).toChar()
            "$ESC[M$cb$cx$cy"
        }
    }
}
