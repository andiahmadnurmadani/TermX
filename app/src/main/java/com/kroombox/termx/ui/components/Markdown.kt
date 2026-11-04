package com.kroombox.termx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kroombox.termx.ui.theme.Mono
import com.kroombox.termx.ui.theme.Palette
import kotlinx.coroutines.delay

/** Blok Markdown hasil parse. Parser sengaja kecil: cukup untuk jawaban agent AI. */
sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String, val open: Boolean) : MdBlock
    data class ListItem(val marker: String, val indent: Int, val text: String, val checked: Boolean? = null) : MdBlock
    data class Quote(val text: String) : MdBlock
    /** [align] per kolom: 'l', 'c', atau 'r'. */
    data class Table(val rows: List<List<String>>, val align: List<Char> = emptyList()) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val listRe = Regex("""^(\s*)([-*+]|\d{1,3}[.)])\s+(.*)$""")
    private val headRe = Regex("""^(#{1,6})\s+(.*)$""")
    private val ruleRe = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val tableSep = Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$""")
    private val taskRe = Regex("""^\[([ xX])]\s+(.*)$""")

    fun parse(src: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = src.replace("\r\n", "\n").split('\n')
        var i = 0
        // Front matter YAML di awal file (.md) ditampilkan sebagai blok kode.
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." }
            if (end != null && end > 1) {
                out.add(MdBlock.Code("yaml", lines.subList(1, end).joinToString("\n"), open = false))
                i = end + 1
            }
        }
        val para = StringBuilder()
        fun flushPara() {
            if (para.isNotBlank()) out.add(MdBlock.Para(para.toString().trim()))
            para.setLength(0)
        }
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    flushPara()
                    val fence = trimmed.take(3)
                    val lang = trimmed.drop(3).trim()
                    val code = StringBuilder()
                    i++
                    var closed = false
                    while (i < lines.size) {
                        if (lines[i].trimStart().startsWith(fence)) { closed = true; break }
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i])
                        i++
                    }
                    out.add(MdBlock.Code(lang, code.toString(), open = !closed))
                }
                line.isBlank() -> flushPara()
                headRe.matches(trimmed) -> {
                    flushPara()
                    val m = headRe.find(trimmed)!!
                    out.add(MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trimEnd('#', ' ')))
                }
                ruleRe.matches(line) && para.isEmpty() -> { flushPara(); out.add(MdBlock.Rule) }
                trimmed.contains('|') && i + 1 < lines.size && lines[i + 1].contains('-') && tableSep.matches(lines[i + 1]) &&
                    (trimmed.startsWith("|") || lines[i + 1].contains('|')) -> {
                    flushPara()
                    val rows = ArrayList<List<String>>()
                    rows.add(cells(trimmed))
                    val align = cells(lines[i + 1].trim()).map { c ->
                        val l = c.startsWith(":"); val r = c.endsWith(":")
                        if (l && r) 'c' else if (r) 'r' else 'l'
                    }
                    i += 2
                    while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) { rows.add(cells(lines[i].trim())); i++ }
                    val n = rows.maxOf { it.size }
                    out.add(MdBlock.Table(rows.map { r -> r + List(n - r.size) { "" } }, align))
                    continue
                }
                listRe.matches(line) -> {
                    flushPara()
                    val m = listRe.find(line)!!
                    val marker = m.groupValues[2].let { if (it[0].isDigit()) it.trimEnd(')').trimEnd('.') + "." else "•" }
                    var text = m.groupValues[3]
                    val task = if (marker == "•") taskRe.find(text) else null
                    if (task != null) text = task.groupValues[2]
                    // Baris lanjutan (indentasi) milik item yang sama.
                    while (i + 1 < lines.size && lines[i + 1].startsWith("  ") && !listRe.matches(lines[i + 1]) && lines[i + 1].isNotBlank()) {
                        text += " " + lines[i + 1].trim(); i++
                    }
                    out.add(MdBlock.ListItem(marker, m.groupValues[1].replace("\t", "    ").length / 2, text,
                        task?.let { it.groupValues[1] != " " }))
                }
                trimmed.startsWith(">") -> {
                    flushPara()
                    val q = StringBuilder(trimmed.removePrefix(">").trim())
                    while (i + 1 < lines.size && lines[i + 1].trimStart().startsWith(">")) {
                        q.append('\n').append(lines[i + 1].trimStart().removePrefix(">").trim()); i++
                    }
                    out.add(MdBlock.Quote(q.toString()))
                }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line.trim()) }
            }
            i++
        }
        flushPara()
        return out
    }

    private fun cells(row: String): List<String> {
        var r = row.trim()
        if (r.startsWith("|")) r = r.drop(1)
        if (r.endsWith("|") && !r.endsWith("\\|")) r = r.dropLast(1)
        return r.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }
    }

    private val inlineRe = Regex(
        """\\([\\`*_{}\[\]()#+\-.!|~>])|(`+)(.+?)\2|!\[([^\]]*)]\(([^)\s]+)\)|\*\*(.+?)\*\*|__(.+?)__|""" +
            """(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?!\w)|~~(.+?)~~|""" +
            """\[([^\]]+)]\(([^)\s]+)(?:\s+"[^"]*")?\)|(https?://[^\s)>\]]+)|<br\s*/?>""",
        RegexOption.IGNORE_CASE,
    )

    fun inline(text: String, codeBg: Color, link: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in inlineRe.findAll(text)) {
            if (m.range.first > last) append(text.substring(last, m.range.first))
            val g = m.groups
            when {
                g[1] != null -> append(g[1]!!.value)
                g[3] != null -> withStyle(SpanStyle(fontFamily = Mono, background = codeBg, fontSize = 13.5.sp)) { append("\u2009${g[3]!!.value.trim()}\u2009") }
                g[5] != null -> withLink(LinkAnnotation.Url(g[5]!!.value, TextLinkStyles(SpanStyle(color = link)))) {
                    append("\uD83D\uDDBC ${g[4]?.value?.ifBlank { null } ?: g[5]!!.value.substringAfterLast('/')}")
                }
                g[6] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(g[6]!!.value, codeBg, link)) }
                g[7] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(g[7]!!.value, codeBg, link)) }
                g[8] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(g[8]!!.value, codeBg, link)) }
                g[9] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(g[9]!!.value, codeBg, link)) }
                g[10] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inline(g[10]!!.value, codeBg, link)) }
                g[11] != null -> withLink(LinkAnnotation.Url(g[12]!!.value, TextLinkStyles(SpanStyle(color = link)))) { append(inline(g[11]!!.value, codeBg, link)) }
                g[13] != null -> withLink(LinkAnnotation.Url(g[13]!!.value, TextLinkStyles(SpanStyle(color = link)))) { append(g[13]!!.value) }
                else -> append("\n") // <br>
            }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}

/** Tampilkan Markdown. [blocks] sebaiknya di-remember oleh pemanggil agar parse tidak diulang. */
@Composable
fun MarkdownText(blocks: List<MdBlock>, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) MarkdownBlock(b, color)
    }
}

/** Satu blok Markdown (dipakai juga oleh pratinjau file .md dalam LazyColumn). */
@Composable
fun MarkdownBlock(b: MdBlock, color: Color = Palette.Text) {
    val codeBg = Palette.SurfaceHighest
    val link = Palette.Blue
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, color = color)
    when (b) {
        is MdBlock.Para -> Text(MarkdownParser.inline(b.text, codeBg, link), style = body)
        is MdBlock.Heading -> Column(Modifier.padding(top = if (b.level <= 2) 8.dp else 4.dp)) {
            Text(
                MarkdownParser.inline(b.text, codeBg, link),
                style = body.copy(
                    fontSize = when (b.level) { 1 -> 22.sp; 2 -> 19.sp; 3 -> 17.sp; else -> 15.sp },
                    fontWeight = if (b.level <= 3) FontWeight.Bold else FontWeight.SemiBold,
                    lineHeight = when (b.level) { 1 -> 29.sp; 2 -> 26.sp; else -> 23.sp },
                    color = if (b.level >= 5) Palette.TextDim else color,
                ),
            )
            if (b.level <= 2) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Palette.Outline))
        }
        is MdBlock.ListItem -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
            when (b.checked) {
                null -> Text(
                    if (b.marker == "•") listOf("•", "◦", "▪")[b.indent % 3] else b.marker,
                    style = body.copy(color = Palette.TextDim),
                    modifier = Modifier.width(if (b.marker == "•") 18.dp else (10 + b.marker.length * 8).dp),
                )
                else -> Icon(
                    if (b.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                    tint = if (b.checked) Palette.Accent else Palette.TextDim,
                    modifier = Modifier.padding(top = 2.dp, end = 6.dp).size(18.dp),
                )
            }
            Text(
                MarkdownParser.inline(b.text, codeBg, link),
                style = if (b.checked == true) body.copy(color = Palette.TextDim, textDecoration = TextDecoration.LineThrough) else body,
            )
        }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Palette.Accent.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
            MarkdownText(
                remember(b.text) { MarkdownParser.parse(b.text) },
                Modifier.padding(start = 12.dp),
                color = Palette.TextDim,
            )
        }
        is MdBlock.Code -> CodeBlock(b.lang, b.code)
        is MdBlock.Table -> MdTable(b, body, codeBg, link)
        MdBlock.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Palette.Outline))
    }
}

@Composable
private fun MdTable(b: MdBlock.Table, body: TextStyle, codeBg: Color, link: Color) {
    val cols = b.rows.maxOfOrNull { it.size } ?: 0
    // Lebar kolom dari panjang isi (perkiraan), dibatasi agar tabel lebar tetap terbaca dan bisa digeser.
    val widths = remember(b) {
        (0 until cols).map { c -> (b.rows.maxOf { it.getOrNull(c)?.length ?: 0 } * 7.6f + 24f).coerceIn(56f, 260f).dp }
    }
    val cell = body.copy(fontSize = 13.5.sp, lineHeight = 19.sp)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Palette.Outline, RoundedCornerShape(10.dp))) {
            b.rows.forEachIndexed { r, row ->
                Row(
                    Modifier.height(IntrinsicSize.Min).background(
                        when { r == 0 -> Palette.SurfaceHigh; r % 2 == 0 -> Palette.Surface; else -> Color.Transparent },
                    ),
                ) {
                    for (c in 0 until cols) {
                        val align = when (b.align.getOrNull(c)) { 'c' -> TextAlign.Center; 'r' -> TextAlign.End; else -> TextAlign.Start }
                        Text(
                            MarkdownParser.inline(row.getOrNull(c).orEmpty(), codeBg, link),
                            style = if (r == 0) cell.copy(fontWeight = FontWeight.SemiBold) else cell,
                            textAlign = align,
                            modifier = Modifier.width(widths[c]).fillMaxHeight().padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                        if (c < cols - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Outline.copy(alpha = 0.6f)))
                    }
                }
                if (r < b.rows.size - 1) Box(Modifier.height(1.dp).width(widths.fold(0.dp) { a, w -> a + w } + (cols - 1).coerceAtLeast(0).dp)
                    .background(Palette.Outline.copy(alpha = if (r == 0) 1f else 0.5f)))
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifEmpty { "code" }, style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }, modifier = Modifier.size(36.dp)) {
                Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, "Salin", Modifier.size(16.dp),
                    tint = if (copied) Palette.Accent else Palette.TextDim)
            }
        }
        SelectionContainer {
            Text(
                code, fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 18.sp, color = Palette.Text, softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}
