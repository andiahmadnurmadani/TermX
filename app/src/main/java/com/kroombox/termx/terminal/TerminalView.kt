package com.kroombox.termx.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Penghubung antara view dan sesi (SSH). */
interface TerminalClient {
    fun sendBytes(bytes: ByteArray)
    fun onTerminalResize(cols: Int, rows: Int)
    fun onSelectionChanged(active: Boolean) {}
    fun onFontSizeChanged(sp: Float) {}
}

/**
 * View terminal: menggambar [TerminalEmulator] dengan Canvas dan menerima input keyboard
 * (soft keyboard & hardware), gestur gulir, pinch-zoom, dan seleksi teks.
 */
@SuppressLint("ViewConstructor")
class TerminalView(context: Context) : View(context) {

    var emulator: TerminalEmulator? = null
        set(value) {
            field = value
            scrollOffset = 0
            lastScrollCounter = value?.scrollCounter ?: 0
            clearSelection()
            if (width > 0 && height > 0) updateSize()
            invalidate()
        }
    var client: TerminalClient? = null
    var theme: TerminalTheme = TerminalTheme.Midnight
        set(value) { field = value; invalidate() }

    /** Modifier "sticky" dari bar tombol tambahan (Ctrl/Alt). */
    var stickyModsProvider: (() -> Int)? = null
    var onStickyModsConsumed: (() -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint()
    private var regular: Typeface = Typeface.MONOSPACE
    private var bold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private var fontKey: String? = null
    private var ligatures = false

    var fontSizeSp: Float = 13f
        set(value) {
            val v = value.coerceIn(6f, 32f)
            if (v == field) return
            field = v
            applyFont()
            updateSize()
            invalidate()
        }

    private var cellW = 1f
    private var cellH = 1f
    private var baseline = 0f

    /** Jumlah baris yang digulir ke riwayat (0 = posisi live). */
    private var scrollOffset = 0
    private var lastScrollCounter = 0L
    private var scrollRemainder = 0f

    // Seleksi (koordinat baris absolut)
    private var selecting = false
    private var selStartRow = 0
    private var selStartCol = 0
    private var selEndRow = 0
    private var selEndCol = 0
    val hasSelection: Boolean get() = selecting

    private val sb = StringBuilder()
    private val rect = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        applyFont()
    }

    /**
     * Ganti font terminal. [key] dipakai untuk menghindari pengukuran ulang bila font tidak berubah.
     * Ligatur dimatikan secara default agar setiap karakter tetap terlihat apa adanya.
     */
    fun setFont(key: String, regularTf: Typeface, boldTf: Typeface, enableLigatures: Boolean) {
        if (key == fontKey && enableLigatures == ligatures) return
        fontKey = key
        ligatures = enableLigatures
        regular = regularTf
        bold = boldTf
        textPaint.typeface = regularTf
        textPaint.fontFeatureSettings = if (enableLigatures) null else "'liga' 0, 'calt' 0, 'dlig' 0"
        applyFont()
        updateSize()
        invalidate()
    }

    private fun applyFont() {
        textPaint.typeface = regular
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSizeSp, resources.displayMetrics)
        cellW = textPaint.measureText("M")
        val fm = textPaint.fontMetrics
        cellH = ceil(fm.descent - fm.ascent + fm.leading)
        baseline = -fm.ascent
    }

    // ------------------------------------------------------------------ Ukuran
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSize()
    }

    private fun updateSize() {
        val emu = emulator ?: return
        if (width <= 0 || height <= 0) return
        val cols = max(2, floor((width - paddingLeft - paddingRight) / cellW).toInt())
        val rows = max(2, floor((height - paddingTop - paddingBottom) / cellH).toInt())
        if (cols != emu.cols || rows != emu.rows) {
            emu.resize(cols, rows)
        }
        client?.onTerminalResize(cols, rows)
    }

    // ------------------------------------------------------------------ Gambar
    override fun onDraw(canvas: Canvas) {
        val emu = emulator
        canvas.drawColor(if (emu?.reverseVideo == true) theme.foreground else theme.background)
        if (emu == null) return
        synchronized(emu.lock) {
            // Pertahankan posisi baca saat output baru masuk ketika sedang menggulir riwayat.
            val delta = emu.scrollCounter - lastScrollCounter
            if (scrollOffset > 0 && delta > 0) scrollOffset += delta.toInt()
            lastScrollCounter = emu.scrollCounter
            scrollOffset = scrollOffset.coerceIn(0, emu.scrollbackSize)

            val firstAbs = emu.scrollbackSize - scrollOffset
            val rv = emu.reverseVideo
            for (y in 0 until emu.rows) {
                val line = emu.lineAt(firstAbs + y) ?: continue
                drawLine(canvas, line, y, firstAbs + y, rv, emu.cols)
            }
            if (scrollOffset == 0 && emu.cursorVisible) drawCursor(canvas, emu)
        }
        if (scrollOffset > 0) drawScrollIndicator(canvas)
    }

    private fun drawLine(canvas: Canvas, line: TermLine, y: Int, absRow: Int, rv: Boolean, cols: Int) {
        val top = paddingTop + y * cellH
        val n = min(cols, line.size)
        // 1) Latar belakang per run
        var x = 0
        while (x < n) {
            val s = line.styles[x]
            val bgColor = cellBg(s, rv)
            var end = x + 1
            while (end < n && cellBg(line.styles[end], rv) == bgColor) end++
            if (bgColor != (if (rv) theme.foreground else theme.background)) {
                bgPaint.color = bgColor
                canvas.drawRect(paddingLeft + x * cellW, top, paddingLeft + end * cellW, top + cellH, bgPaint)
            }
            x = end
        }
        // Seleksi
        if (selecting) {
            val (r1, c1, r2, c2) = normalizedSelection()
            if (absRow in r1..r2) {
                val from = if (absRow == r1) c1 else 0
                val to = if (absRow == r2) c2 else cols
                if (to > from) {
                    bgPaint.color = theme.selection
                    canvas.drawRect(paddingLeft + from * cellW, top, paddingLeft + to * cellW, top + cellH, bgPaint)
                }
            }
        }
        // 2) Teks: run ASCII bergaya sama digambar sekaligus; selain itu per sel.
        x = 0
        val baseY = top + baseline
        while (x < n) {
            val s = line.styles[x]
            val a = Style.attrs(s)
            if (a and Style.WIDE_CONT != 0) { x++; continue }
            val cp = line.chars[x]
            if (cp < 0x80) {
                var end = x + 1
                while (end < n && line.styles[end] == s && line.chars[end] < 0x80) end++
                if (a and Style.INVISIBLE == 0) {
                    var blank = true
                    for (i in x until end) if (line.chars[i] != ' '.code) { blank = false; break }
                    if (!blank) {
                        sb.setLength(0)
                        for (i in x until end) sb.append(line.chars[i].toChar())
                        setupPaint(s, rv)
                        canvas.drawText(sb, 0, sb.length, paddingLeft + x * cellW, baseY, textPaint)
                    }
                    decorate(canvas, s, x, end, top, rv)
                }
                x = end
            } else {
                if (a and Style.INVISIBLE == 0) {
                    setupPaint(s, rv)
                    val w = if (a and Style.WIDE != 0) 2 else 1
                    sb.setLength(0)
                    sb.appendCodePoint(cp)
                    val str = sb.toString()
                    val measured = textPaint.measureText(str)
                    val slot = cellW * w
                    if (measured > slot * 1.05f) {
                        // Perkecil glyph yang terlalu lebar supaya tidak menimpa sel sebelah.
                        canvas.save()
                        val sx = slot / measured
                        canvas.scale(sx, 1f, paddingLeft + x * cellW, baseY)
                        canvas.drawText(str, paddingLeft + x * cellW, baseY, textPaint)
                        canvas.restore()
                    } else {
                        val off = (slot - measured) / 2f
                        canvas.drawText(str, paddingLeft + x * cellW + off, baseY, textPaint)
                    }
                    decorate(canvas, s, x, x + w, top, rv)
                }
                x++
            }
        }
    }

    private fun cellBg(s: Long, rv: Boolean): Int {
        val a = Style.attrs(s)
        val inverse = (a and Style.INVERSE != 0) != rv
        return if (inverse) {
            theme.resolve(Style.fg(s), true, a and Style.BOLD != 0)
        } else {
            theme.resolve(Style.bg(s), false, false)
        }
    }

    private fun cellFg(s: Long, rv: Boolean): Int {
        val a = Style.attrs(s)
        val inverse = (a and Style.INVERSE != 0) != rv
        var c = if (inverse) theme.resolve(Style.bg(s), false, false)
        else theme.resolve(Style.fg(s), true, a and Style.BOLD != 0)
        if (a and Style.DIM != 0) c = (c and 0x00FFFFFF) or (0x99 shl 24)
        return c
    }

    private fun setupPaint(s: Long, rv: Boolean) {
        val a = Style.attrs(s)
        textPaint.color = cellFg(s, rv)
        textPaint.typeface = if (a and Style.BOLD != 0) bold else regular
        textPaint.textSkewX = if (a and Style.ITALIC != 0) -0.2f else 0f
    }

    private fun decorate(canvas: Canvas, s: Long, from: Int, to: Int, top: Float, rv: Boolean) {
        val a = Style.attrs(s)
        if (a and (Style.UNDERLINE or Style.STRIKE) == 0) return
        bgPaint.color = cellFg(s, rv)
        val thick = max(1f, cellH / 14f)
        if (a and Style.UNDERLINE != 0) {
            val yy = top + cellH - thick * 1.5f
            canvas.drawRect(paddingLeft + from * cellW, yy, paddingLeft + to * cellW, yy + thick, bgPaint)
        }
        if (a and Style.STRIKE != 0) {
            val yy = top + cellH / 2f
            canvas.drawRect(paddingLeft + from * cellW, yy, paddingLeft + to * cellW, yy + thick, bgPaint)
        }
    }

    private fun drawCursor(canvas: Canvas, emu: TerminalEmulator) {
        val cx = paddingLeft + emu.cursorX * cellW
        val cy = paddingTop + emu.cursorY * cellH
        val line = emu.screenLine(emu.cursorY)
        val wide = Style.attrs(line.styles[emu.cursorX]) and Style.WIDE != 0
        val w = if (wide) cellW * 2 else cellW
        bgPaint.color = theme.cursor
        if (!hasFocus()) {
            bgPaint.style = Paint.Style.STROKE
            bgPaint.strokeWidth = max(1f, cellW / 8f)
            canvas.drawRect(cx, cy, cx + w, cy + cellH, bgPaint)
            bgPaint.style = Paint.Style.FILL
            return
        }
        when (emu.cursorShape) {
            1 -> canvas.drawRect(cx, cy + cellH - max(2f, cellH / 8f), cx + w, cy + cellH, bgPaint)
            2 -> canvas.drawRect(cx, cy, cx + max(2f, cellW / 6f), cy + cellH, bgPaint)
            else -> {
                canvas.drawRect(cx, cy, cx + w, cy + cellH, bgPaint)
                val cp = line.chars[emu.cursorX]
                if (cp != ' '.code) {
                    textPaint.color = theme.background
                    textPaint.typeface = if (Style.attrs(line.styles[emu.cursorX]) and Style.BOLD != 0) bold else regular
                    textPaint.textSkewX = 0f
                    sb.setLength(0)
                    sb.appendCodePoint(cp)
                    canvas.drawText(sb.toString(), cx, cy + baseline, textPaint)
                }
            }
        }
    }

    private fun drawScrollIndicator(canvas: Canvas) {
        val emu = emulator ?: return
        val total = emu.scrollbackSize + emu.rows
        if (total <= emu.rows) return
        val h = height.toFloat()
        val barH = max(h * emu.rows / total, 24f)
        val pos = (emu.scrollbackSize - scrollOffset).toFloat() / (total - emu.rows)
        val top = (h - barH) * pos
        bgPaint.color = (theme.foreground and 0x00FFFFFF) or (0x66 shl 24)
        rect.set(width - 6f * resources.displayMetrics.density, top, width - 2f * resources.displayMetrics.density, top + barH)
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
    }

    // ------------------------------------------------------------------ Seleksi
    private data class Sel(val r1: Int, val c1: Int, val r2: Int, val c2: Int)

    private fun normalizedSelection(): Sel {
        return if (selStartRow < selEndRow || (selStartRow == selEndRow && selStartCol <= selEndCol)) {
            Sel(selStartRow, selStartCol, selEndRow, selEndCol + 1)
        } else {
            Sel(selEndRow, selEndCol, selStartRow, selStartCol + 1)
        }
    }

    fun clearSelection() {
        if (selecting) {
            selecting = false
            client?.onSelectionChanged(false)
            invalidate()
        }
    }

    fun selectedText(): String? {
        val emu = emulator ?: return null
        if (!selecting) return null
        val s = normalizedSelection()
        return emu.textRange(s.r1, s.c1, s.r2, s.c2)
    }

    fun selectAllVisible() {
        val emu = emulator ?: return
        synchronized(emu.lock) {
            val first = emu.scrollbackSize - scrollOffset
            selStartRow = first; selStartCol = 0
            selEndRow = first + emu.rows - 1; selEndCol = emu.cols - 1
        }
        selecting = true
        client?.onSelectionChanged(true)
        invalidate()
    }

    private fun cellAt(ev: MotionEvent): Pair<Int, Int> {
        val emu = emulator ?: return 0 to 0
        val col = ((ev.x - paddingLeft) / cellW).toInt().coerceIn(0, emu.cols - 1)
        val row = ((ev.y - paddingTop) / cellH).toInt().coerceIn(0, emu.rows - 1)
        return row to col
    }

    // ------------------------------------------------------------------ Gestur
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (selecting) { clearSelection(); return true }
            val emu = emulator
            if (emu != null && emu.mouseMode != MouseMode.NONE && scrollOffset == 0) {
                val (row, col) = cellAt(e)
                sendString(KeyEncoder.mouseEvent(0, col, row, true, emu.mouseSgr), resetScroll = false)
                sendString(KeyEncoder.mouseEvent(0, col, row, false, emu.mouseSgr), resetScroll = false)
            }
            showKeyboard()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val emu = emulator ?: return
            val (row, col) = cellAt(e)
            // Pilih satu kata sebagai awal (di bawah lock karena thread pembaca bisa menulis bersamaan).
            synchronized(emu.lock) {
                val abs = emu.scrollbackSize - scrollOffset + row
                val line = emu.lineAt(abs) ?: return
                var a = col
                var b = col
                fun isWord(i: Int) = i in 0 until line.size && !Character.isWhitespace(line.chars[i])
                if (isWord(col)) {
                    while (isWord(a - 1)) a--
                    while (isWord(b + 1)) b++
                }
                selStartRow = abs; selStartCol = a
                selEndRow = abs; selEndCol = b
            }
            selecting = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            client?.onSelectionChanged(true)
            invalidate()
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val emu = emulator ?: return false
            if (scaling) return true
            if (selecting) {
                val (row, col) = cellAt(e2)
                selEndRow = synchronized(emu.lock) { emu.scrollbackSize } - scrollOffset + row
                selEndCol = col
                invalidate()
                return true
            }
            scrollRemainder += distanceY
            val lines = (scrollRemainder / cellH).toInt()
            if (lines == 0) return true
            scrollRemainder -= lines * cellH
            when {
                emu.mouseMode != MouseMode.NONE -> {
                    val (row, col) = cellAt(e2)
                    val button = if (lines < 0) 64 else 65
                    repeat(abs(lines)) { sendString(KeyEncoder.mouseEvent(button, col, row, true, emu.mouseSgr), resetScroll = false) }
                }
                emu.isAltScreen -> {
                    val key = if (lines < 0) TermKey.UP else TermKey.DOWN
                    repeat(abs(lines)) { sendString(KeyEncoder.encode(key, 0, emu.appCursorKeys), resetScroll = false) }
                }
                else -> {
                    scrollOffset = (scrollOffset - lines).coerceIn(0, emu.scrollbackSize)
                    invalidate()
                }
            }
            return true
        }
    })

    private var scaling = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaling = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val f = detector.scaleFactor
            if (abs(f - 1f) > 0.04f) {
                fontSizeSp *= f
                client?.onFontSizeChanged(fontSizeSp)
                return true
            }
            return false
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scaling = false }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            scrollRemainder = 0f
        }
        return true
    }

    fun scrollToBottom() {
        scrollOffset = 0
        invalidate()
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    // ------------------------------------------------------------------ Kirim data
    private fun sendString(s: String, resetScroll: Boolean = true) {
        if (s.isEmpty()) return
        if (resetScroll && scrollOffset != 0) { scrollOffset = 0; invalidate() }
        client?.sendBytes(s.toByteArray(Charsets.UTF_8))
    }

    /** Kirim teks dari keyboard/IME dengan menerapkan modifier sticky ke karakter pertama. */
    fun sendText(text: String) {
        if (text.isEmpty()) return
        val sticky = stickyModsProvider?.invoke() ?: 0
        val out = StringBuilder()
        var first = true
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val c = if (cp == '\n'.code) '\r'.code else cp
            if (first && sticky != 0) {
                out.append(KeyEncoder.encodeChar(c, sticky))
            } else {
                out.appendCodePoint(c)
            }
            first = false
        }
        if (sticky != 0) onStickyModsConsumed?.invoke()
        sendString(out.toString())
    }

    /** Kirim tombol khusus (dari bar tombol tambahan atau keyboard fisik). */
    fun sendKey(key: TermKey, extraMods: Int = 0) {
        val emu = emulator ?: return
        val sticky = stickyModsProvider?.invoke() ?: 0
        val mods = sticky or extraMods
        sendString(KeyEncoder.encode(key, mods, emu.appCursorKeys))
        if (sticky != 0) onStickyModsConsumed?.invoke()
    }

    fun paste(text: String) {
        val emu = emulator ?: return
        val clean = text.replace("\r\n", "\r").replace('\n', '\r')
        if (emu.bracketedPaste) sendString("\u001b[200~$clean\u001b[201~")
        else sendString(clean)
    }

    // ------------------------------------------------------------------ Keyboard
    override fun onCheckIsTextEditor(): Boolean = true

    /**
     * Jenis keyboard yang diminta ke Android:
     *  - "standard": TYPE_NULL (seperti Termux) — keyboard tampil normal, tiap tombol langsung terkirim.
     *  - "text": keyboard teks dengan saran/autocorrect; kata yang sedang disusun dikirim bertahap.
     *  - "nosuggest": mode lama (visible password, tanpa saran).
     */
    var keyboardMode: String = "standard"
        set(value) {
            if (field == value) return
            field = value
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(this)
        }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = when (keyboardMode) {
            "text" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            "nosuggest" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else -> InputType.TYPE_NULL
        }
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        return TermInputConnection()
    }

    /**
     * Penghubung keyboard. Tidak ada yang ditahan: teks yang sedang "disusun" keyboard (huruf
     * bergaris bawah) langsung dikirim ke server, dan bila keyboard mengubahnya (autocorrect, saran)
     * hanya selisihnya yang dikirim — backspace untuk bagian yang berubah lalu teks barunya. Dulu teks
     * baru terkirim saat kata selesai, itulah yang terasa sebagai jeda.
     */
    private inner class TermInputConnection : BaseInputConnection(this@TerminalView, true) {
        /** Teks susunan yang sudah terkirim ke server. */
        private var sentComposing = ""

        private fun applyDiff(old: String, new: String) {
            var p = 0
            while (p < old.length && p < new.length && old[p] == new[p]) p++
            if (p > 0 && Character.isHighSurrogate(old[p - 1])) p--
            val removed = old.codePointCount(p, old.length)
            repeat(removed) { sendKey(TermKey.BACKSPACE) }
            if (p < new.length) sendText(new.substring(p))
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val t = text?.toString() ?: ""
            applyDiff(sentComposing, t)
            sentComposing = t
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val t = text?.toString() ?: ""
            if (sentComposing.isNotEmpty()) applyDiff(sentComposing, t) else sendText(t)
            sentComposing = ""
            editable?.clear()
            return true
        }

        override fun finishComposingText(): Boolean {
            // Teksnya sudah terkirim saat disusun; cukup lupakan penandanya.
            sentComposing = ""
            editable?.clear()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (sentComposing.isNotEmpty()) {
                val keep = (sentComposing.length - beforeLength).coerceAtLeast(0)
                val next = sentComposing.substring(0, keep)
                applyDiff(sentComposing, next)
                sentComposing = next
                return true
            }
            repeat(beforeLength.coerceAtLeast(1).coerceAtMost(64)) { sendKey(TermKey.BACKSPACE) }
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = sentComposing.takeLast(n)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            when {
                event.action == KeyEvent.ACTION_DOWN -> handleKeyDown(event.keyCode, event)
                // Sebagian keyboard mengirim teks sebagai satu event "multiple".
                event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN ->
                    event.characters?.let { sendText(it) }
            }
            return true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        if (handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var mods = 0
        if (event.isShiftPressed) mods = mods or Mods.SHIFT
        if (event.isAltPressed) mods = mods or Mods.ALT
        if (event.isCtrlPressed) mods = mods or Mods.CTRL
        val special = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> TermKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> TermKey.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> TermKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> TermKey.RIGHT
            KeyEvent.KEYCODE_MOVE_HOME -> TermKey.HOME
            KeyEvent.KEYCODE_MOVE_END -> TermKey.END
            KeyEvent.KEYCODE_PAGE_UP -> TermKey.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> TermKey.PAGE_DOWN
            KeyEvent.KEYCODE_INSERT -> TermKey.INSERT
            KeyEvent.KEYCODE_FORWARD_DEL -> TermKey.DELETE
            KeyEvent.KEYCODE_ESCAPE -> TermKey.ESC
            KeyEvent.KEYCODE_TAB -> TermKey.TAB
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TermKey.ENTER
            KeyEvent.KEYCODE_DEL -> TermKey.BACKSPACE
            KeyEvent.KEYCODE_F1 -> TermKey.F1
            KeyEvent.KEYCODE_F2 -> TermKey.F2
            KeyEvent.KEYCODE_F3 -> TermKey.F3
            KeyEvent.KEYCODE_F4 -> TermKey.F4
            KeyEvent.KEYCODE_F5 -> TermKey.F5
            KeyEvent.KEYCODE_F6 -> TermKey.F6
            KeyEvent.KEYCODE_F7 -> TermKey.F7
            KeyEvent.KEYCODE_F8 -> TermKey.F8
            KeyEvent.KEYCODE_F9 -> TermKey.F9
            KeyEvent.KEYCODE_F10 -> TermKey.F10
            KeyEvent.KEYCODE_F11 -> TermKey.F11
            KeyEvent.KEYCODE_F12 -> TermKey.F12
            else -> null
        }
        if (special != null) {
            sendKey(special, mods)
            return true
        }
        val meta = event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv()
        val uc = event.getUnicodeChar(meta)
        if (uc == 0) return false
        if (uc and KeyCharacterMapCombining != 0) return false
        val sticky = stickyModsProvider?.invoke() ?: 0
        val allMods = (mods and Mods.SHIFT.inv()) or sticky
        sendString(KeyEncoder.encodeChar(uc, allMods))
        if (sticky != 0) onStickyModsConsumed?.invoke()
        return true
    }

    fun onBell() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    companion object {
        private const val KeyCharacterMapCombining = 0x80000000.toInt()
    }
}
