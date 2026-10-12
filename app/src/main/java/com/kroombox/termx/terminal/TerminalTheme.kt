package com.kroombox.termx.terminal

/** Skema warna terminal (ARGB). */
data class TerminalTheme(
    val id: String,
    val name: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    val selection: Int,
    /** 16 warna ANSI dasar. */
    val ansi: IntArray,
) {
    private val palette: IntArray by lazy { buildPalette() }

    private fun buildPalette(): IntArray {
        val p = IntArray(256)
        for (i in 0 until 16) p[i] = ansi[i]
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        var i = 16
        for (r in 0 until 6) for (g in 0 until 6) for (b in 0 until 6) {
            p[i++] = argb(steps[r], steps[g], steps[b])
        }
        for (j in 0 until 24) {
            val v = 8 + j * 10
            p[232 + j] = argb(v, v, v)
        }
        return p
    }

    /** Ubah kode warna [Style] menjadi ARGB. [isFg] menentukan nilai default. */
    fun resolve(code: Int, isFg: Boolean, bold: Boolean): Int {
        return when {
            code == Style.COLOR_DEFAULT -> if (isFg) foreground else background
            code and Style.TRUECOLOR_FLAG != 0 -> 0xFF000000.toInt() or (code and 0xFFFFFF)
            else -> {
                var idx = code - 1
                if (bold && isFg && idx in 0..7) idx += 8
                palette[idx.coerceIn(0, 255)]
            }
        }
    }

    override fun equals(other: Any?) = other is TerminalTheme && other.id == id
    override fun hashCode() = id.hashCode()

    companion object {
        fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        private fun c(hex: Long) = hex.toInt()

        val Midnight = TerminalTheme(
            id = "midnight", name = "Midnight",
            background = c(0xFF0E1116), foreground = c(0xFFE6EDF3), cursor = c(0xFF3DDC97), selection = c(0x553DDC97),
            ansi = intArrayOf(
                c(0xFF1B1F27), c(0xFFFF6B6B), c(0xFF3DDC97), c(0xFFF7C948), c(0xFF5AA9FF), c(0xFFC792EA), c(0xFF4DD0E1), c(0xFFC9D1D9),
                c(0xFF4B5563), c(0xFFFF8787), c(0xFF6EE7B7), c(0xFFFDE68A), c(0xFF93C5FD), c(0xFFDDB6F2), c(0xFF99F6E4), c(0xFFFFFFFF),
            ),
        )
        val Dracula = TerminalTheme(
            id = "dracula", name = "Dracula",
            background = c(0xFF282A36), foreground = c(0xFFF8F8F2), cursor = c(0xFFF8F8F2), selection = c(0x6644475A),
            ansi = intArrayOf(
                c(0xFF21222C), c(0xFFFF5555), c(0xFF50FA7B), c(0xFFF1FA8C), c(0xFFBD93F9), c(0xFFFF79C6), c(0xFF8BE9FD), c(0xFFF8F8F2),
                c(0xFF6272A4), c(0xFFFF6E6E), c(0xFF69FF94), c(0xFFFFFFA5), c(0xFFD6ACFF), c(0xFFFF92DF), c(0xFFA4FFFF), c(0xFFFFFFFF),
            ),
        )
        val Nord = TerminalTheme(
            id = "nord", name = "Nord",
            background = c(0xFF2E3440), foreground = c(0xFFD8DEE9), cursor = c(0xFF88C0D0), selection = c(0x664C566A),
            ansi = intArrayOf(
                c(0xFF3B4252), c(0xFFBF616A), c(0xFFA3BE8C), c(0xFFEBCB8B), c(0xFF81A1C1), c(0xFFB48EAD), c(0xFF88C0D0), c(0xFFE5E9F0),
                c(0xFF4C566A), c(0xFFBF616A), c(0xFFA3BE8C), c(0xFFEBCB8B), c(0xFF81A1C1), c(0xFFB48EAD), c(0xFF8FBCBB), c(0xFFECEFF4),
            ),
        )
        val SolarizedDark = TerminalTheme(
            id = "solarized", name = "Solarized Dark",
            background = c(0xFF002B36), foreground = c(0xFF93A1A1), cursor = c(0xFF93A1A1), selection = c(0x66073642),
            ansi = intArrayOf(
                c(0xFF073642), c(0xFFDC322F), c(0xFF859900), c(0xFFB58900), c(0xFF268BD2), c(0xFFD33682), c(0xFF2AA198), c(0xFFEEE8D5),
                c(0xFF586E75), c(0xFFCB4B16), c(0xFF93A1A1), c(0xFF839496), c(0xFF6C71C4), c(0xFFD33682), c(0xFF2AA198), c(0xFFFDF6E3),
            ),
        )
        val Light = TerminalTheme(
            id = "light", name = "Paper",
            background = c(0xFFFAFAF7), foreground = c(0xFF24292F), cursor = c(0xFF0F766E), selection = c(0x440F766E),
            ansi = intArrayOf(
                c(0xFF24292F), c(0xFFCF222E), c(0xFF116329), c(0xFF9A6700), c(0xFF0969DA), c(0xFF8250DF), c(0xFF1B7C83), c(0xFF6E7781),
                c(0xFF57606A), c(0xFFA40E26), c(0xFF1A7F37), c(0xFF633C01), c(0xFF218BFF), c(0xFFA475F9), c(0xFF3192AA), c(0xFF8C959F),
            ),
        )

        val all = listOf(Midnight, Dracula, Nord, SolarizedDark, Light)
        fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Midnight
    }
}
