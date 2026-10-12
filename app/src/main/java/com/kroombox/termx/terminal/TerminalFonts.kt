package com.kroombox.termx.terminal

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import com.kroombox.termx.R
import com.kroombox.termx.data.AppSettings
import com.kroombox.termx.data.CustomFont
import com.kroombox.termx.data.newId
import java.io.File
import kotlin.math.abs

/** Pilihan font terminal (bawaan atau impor). */
data class FontOption(
    val id: String,
    val name: String,
    val regularRes: Int? = null,
    val boldRes: Int? = null,
    val custom: CustomFont? = null,
    val ligatures: Boolean = false,
)

data class LoadedFont(val id: String, val regular: Typeface, val bold: Typeface)

/** Memuat font terminal dan mengelola font yang diimpor pengguna (.ttf / .otf). */
object TerminalFonts {
    const val DEFAULT_ID = "jetbrains_mono"
    const val SYSTEM_ID = "system"

    val builtIn = listOf(
        FontOption("jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono_regular, R.font.jetbrains_mono_bold, ligatures = true),
        FontOption("fira_code", "Fira Code", R.font.fira_code_regular, R.font.fira_code_bold, ligatures = true),
        FontOption("cascadia_code", "Cascadia Code", R.font.cascadia_code_regular, R.font.cascadia_code_bold, ligatures = true),
        FontOption("hack", "Hack", R.font.hack_regular, R.font.hack_bold),
        FontOption(SYSTEM_ID, "Monospace sistem"),
    )

    fun options(settings: AppSettings): List<FontOption> =
        builtIn + settings.customFonts.map { FontOption(it.id, it.name, custom = it, ligatures = true) }

    fun option(settings: AppSettings, id: String = settings.fontId): FontOption =
        options(settings).firstOrNull { it.id == id } ?: builtIn.first()

    private val cache = HashMap<String, LoadedFont>()

    private val systemFont = LoadedFont(SYSTEM_ID, Typeface.MONOSPACE, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))

    fun fontDir(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    /** Muat font (di-cache). Bila gagal, kembali ke monospace sistem. */
    @Synchronized
    fun load(context: Context, settings: AppSettings, id: String = settings.fontId): LoadedFont {
        cache[id]?.let { return it }
        val opt = option(settings, id)
        val loaded = try {
            val custom = opt.custom
            when {
                custom != null -> {
                    val dir = fontDir(context)
                    val regular = Typeface.createFromFile(File(dir, custom.regularFile))
                    val bold = custom.boldFile?.let { Typeface.createFromFile(File(dir, it)) }
                        ?: Typeface.create(regular, Typeface.BOLD)
                    LoadedFont(opt.id, regular, bold)
                }
                opt.regularRes != null -> {
                    val regular = ResourcesCompat.getFont(context, opt.regularRes) ?: Typeface.MONOSPACE
                    val bold = opt.boldRes?.let { ResourcesCompat.getFont(context, it) }
                        ?: Typeface.create(regular, Typeface.BOLD)
                    LoadedFont(opt.id, regular, bold)
                }
                else -> systemFont
            }
        } catch (_: Exception) {
            systemFont
        }
        cache[id] = loaded
        return loaded
    }

    data class ImportResult(val font: CustomFont, val warning: String?)

    /**
     * Salin file font dari [uri] ke penyimpanan aplikasi lalu validasi. Melempar exception bila file
     * bukan font yang valid. Memberi peringatan bila font tidak monospace.
     */
    fun import(context: Context, uri: Uri, displayName: String?): ImportResult {
        val rawName = displayName?.substringBeforeLast('.')?.ifBlank { null } ?: "Font impor"
        val ext = displayName?.substringAfterLast('.', "ttf")?.lowercase()?.takeIf { it == "ttf" || it == "otf" } ?: "ttf"
        val id = newId()
        val fileName = "$id.$ext"
        val target = File(fontDir(context), fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalArgumentException("File tidak bisa dibaca")

        val tf = try {
            Typeface.createFromFile(target)
        } catch (e: Exception) {
            target.delete()
            throw IllegalArgumentException("Bukan file font yang valid (.ttf / .otf)")
        }
        if (tf == Typeface.DEFAULT || target.length() < 1024) {
            target.delete()
            throw IllegalArgumentException("Bukan file font yang valid (.ttf / .otf)")
        }
        val paint = Paint().apply { typeface = tf; textSize = 40f }
        val wi = paint.measureText("i")
        val wm = paint.measureText("M")
        val warning = if (wm <= 0f || abs(wi - wm) / wm > 0.02f) {
            "Font ini tidak monospace — kolom terminal bisa tidak rata."
        } else null
        val name = rawName.replace('_', ' ').replace('-', ' ').trim()
        return ImportResult(CustomFont(id = id, name = name, regularFile = fileName), warning)
    }

    fun delete(context: Context, font: CustomFont) {
        File(fontDir(context), font.regularFile).delete()
        font.boldFile?.let { File(fontDir(context), it).delete() }
        synchronized(this) { cache.remove(font.id) }
    }
}
