package com.kroombox.konsol.terminal

/**
 * Model data terminal murni Kotlin (tanpa dependensi Android) supaya bisa diuji di JVM.
 *
 * Setiap sel menyimpan code point (Int) dan gaya (Long) yang dikemas:
 *  - bit 0..25  : warna foreground
 *  - bit 26..51 : warna background
 *  - bit 52..63 : atribut
 * Pengodean warna (26 bit): 0 = default, 1..256 = indeks palet (idx + 1), (1 shl 25) or rgb = truecolor.
 */
object Style {
    const val BOLD = 1
    const val DIM = 1 shl 1
    const val ITALIC = 1 shl 2
    const val UNDERLINE = 1 shl 3
    const val BLINK = 1 shl 4
    const val INVERSE = 1 shl 5
    const val INVISIBLE = 1 shl 6
    const val STRIKE = 1 shl 7
    const val WIDE = 1 shl 8
    const val WIDE_CONT = 1 shl 9

    const val COLOR_DEFAULT = 0
    const val TRUECOLOR_FLAG = 1 shl 25
    private const val COLOR_MASK = (1L shl 26) - 1

    val DEFAULT: Long = 0L

    fun indexed(idx: Int): Int = (idx and 0xFF) + 1
    fun rgb(r: Int, g: Int, b: Int): Int =
        TRUECOLOR_FLAG or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun fg(s: Long): Int = (s and COLOR_MASK).toInt()
    fun bg(s: Long): Int = ((s ushr 26) and COLOR_MASK).toInt()
    fun attrs(s: Long): Int = (s ushr 52).toInt() and 0xFFF

    fun pack(fg: Int, bg: Int, attrs: Int): Long =
        (fg.toLong() and COLOR_MASK) or
            ((bg.toLong() and COLOR_MASK) shl 26) or
            ((attrs.toLong() and 0xFFF) shl 52)

    fun withFg(s: Long, fg: Int) = pack(fg, bg(s), attrs(s))
    fun withBg(s: Long, bg: Int) = pack(fg(s), bg, attrs(s))
    fun withAttrs(s: Long, a: Int) = pack(fg(s), bg(s), a)

    /** Gaya untuk area yang dihapus: hanya mempertahankan warna background (Background Color Erase). */
    fun eraseStyle(s: Long): Long = pack(COLOR_DEFAULT, bg(s), 0)
}

class TermLine(cols: Int) {
    var chars = IntArray(cols) { ' '.code }
    var styles = LongArray(cols)
    var wrapped = false

    val size: Int get() = chars.size

    fun clear(style: Long) {
        chars.fill(' '.code)
        styles.fill(style)
        wrapped = false
    }

    fun clearRange(from: Int, to: Int, style: Long) {
        val a = from.coerceIn(0, size)
        val b = to.coerceIn(0, size)
        for (i in a until b) {
            chars[i] = ' '.code
            styles[i] = style
        }
    }

    fun resize(cols: Int) {
        if (cols == size) return
        val nc = IntArray(cols) { ' '.code }
        val ns = LongArray(cols)
        val n = minOf(cols, size)
        System.arraycopy(chars, 0, nc, 0, n)
        System.arraycopy(styles, 0, ns, 0, n)
        chars = nc
        styles = ns
    }

    fun copyFrom(other: TermLine) {
        if (other.size != size) {
            chars = other.chars.copyOf()
            styles = other.styles.copyOf()
        } else {
            System.arraycopy(other.chars, 0, chars, 0, size)
            System.arraycopy(other.styles, 0, styles, 0, size)
        }
        wrapped = other.wrapped
    }

    fun text(from: Int = 0, to: Int = size): String {
        val sb = StringBuilder()
        for (i in from.coerceAtLeast(0) until to.coerceAtMost(size)) {
            if (Style.attrs(styles[i]) and Style.WIDE_CONT != 0) continue
            sb.appendCodePoint(chars[i])
        }
        return sb.toString()
    }
}

/** Lebar tampilan karakter (0, 1, atau 2 kolom), versi ringkas dari wcwidth. */
object WcWidth {
    private val combining = intArrayOf(
        0x0300, 0x036F, 0x0483, 0x0489, 0x0591, 0x05BD, 0x05BF, 0x05BF, 0x05C1, 0x05C2,
        0x05C4, 0x05C5, 0x05C7, 0x05C7, 0x0610, 0x061A, 0x064B, 0x065F, 0x0670, 0x0670,
        0x06D6, 0x06DC, 0x06DF, 0x06E4, 0x06E7, 0x06E8, 0x06EA, 0x06ED, 0x0900, 0x0902,
        0x093A, 0x093A, 0x093C, 0x093C, 0x0941, 0x0948, 0x094D, 0x094D, 0x0E31, 0x0E31,
        0x0E34, 0x0E3A, 0x0E47, 0x0E4E, 0x1AB0, 0x1AFF, 0x1DC0, 0x1DFF, 0x200B, 0x200F,
        0x202A, 0x202E, 0x2060, 0x2064, 0x20D0, 0x20FF, 0xFE00, 0xFE0F, 0xFE20, 0xFE2F,
        0xFEFF, 0xFEFF, 0x1F3FB, 0x1F3FF, 0xE0000, 0xE0FFF,
    )
    private val wide = intArrayOf(
        0x1100, 0x115F, 0x231A, 0x231B, 0x2329, 0x232A, 0x23E9, 0x23EC, 0x23F0, 0x23F0,
        0x23F3, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615, 0x2648, 0x2653, 0x267F, 0x267F,
        0x2693, 0x2693, 0x26A1, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5,
        0x26CE, 0x26CE, 0x26D4, 0x26D4, 0x26EA, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26F5,
        0x26FA, 0x26FA, 0x26FD, 0x26FD, 0x2705, 0x2705, 0x270A, 0x270B, 0x2728, 0x2728,
        0x274C, 0x274C, 0x274E, 0x274E, 0x2753, 0x2755, 0x2757, 0x2757, 0x2795, 0x2797,
        0x27B0, 0x27B0, 0x27BF, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50, 0x2B50, 0x2B55, 0x2B55,
        0x2E80, 0x303E, 0x3041, 0x33FF, 0x3400, 0x4DBF, 0x4E00, 0x9FFF, 0xA000, 0xA4CF,
        0xA960, 0xA97F, 0xAC00, 0xD7A3, 0xF900, 0xFAFF, 0xFE10, 0xFE19, 0xFE30, 0xFE6F,
        0xFF00, 0xFF60, 0xFFE0, 0xFFE6, 0x1F004, 0x1F004, 0x1F0CF, 0x1F0CF, 0x1F18E, 0x1F18E,
        0x1F191, 0x1F19A, 0x1F200, 0x1F251, 0x1F300, 0x1F320, 0x1F32D, 0x1F335, 0x1F337, 0x1F37C,
        0x1F37E, 0x1F393, 0x1F3A0, 0x1F3CA, 0x1F3CF, 0x1F3D3, 0x1F3E0, 0x1F3F0, 0x1F3F4, 0x1F3F4,
        0x1F3F8, 0x1F43E, 0x1F440, 0x1F440, 0x1F442, 0x1F4FC, 0x1F4FF, 0x1F53D, 0x1F54B, 0x1F54E,
        0x1F550, 0x1F567, 0x1F57A, 0x1F57A, 0x1F595, 0x1F596, 0x1F5A4, 0x1F5A4, 0x1F5FB, 0x1F64F,
        0x1F680, 0x1F6C5, 0x1F6CC, 0x1F6CC, 0x1F6D0, 0x1F6D2, 0x1F6D5, 0x1F6D7, 0x1F6EB, 0x1F6EC,
        0x1F6F4, 0x1F6FC, 0x1F7E0, 0x1F7EB, 0x1F90C, 0x1F93A, 0x1F93C, 0x1F945, 0x1F947, 0x1F9FF,
        0x1FA70, 0x1FAFF, 0x20000, 0x2FFFD, 0x30000, 0x3FFFD,
    )

    private fun inTable(cp: Int, t: IntArray): Boolean {
        var lo = 0
        var hi = t.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val a = t[mid * 2]
            val b = t[mid * 2 + 1]
            if (cp < a) hi = mid - 1 else if (cp > b) lo = mid + 1 else return true
        }
        return false
    }

    fun of(cp: Int): Int {
        if (cp < 0x300) return 1
        if (inTable(cp, combining)) return 0
        if (inTable(cp, wide)) return 2
        return 1
    }
}
