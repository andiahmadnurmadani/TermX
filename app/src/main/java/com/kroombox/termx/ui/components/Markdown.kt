package com.kroombox.termx.ui.components

import androidx.compose.foundation.background
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
    data class ListItem(val marker: String, val indent: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val listRe = Regex("""^(\s*)([-*+]|\d{1,3}[.)])\s+(.*)$""")
    private val headRe = Regex("""^(#{1,6})\s+(.*)$""")
    private val ruleRe = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val tableSep = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

    fun parse(src: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = src.replace("\r\n", "\n").split('\n')
        var i = 0
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
                trimmed.startsWith("|") && i + 1 < lines.size && tableSep.matches(lines[i + 1]) -> {
                    flushPara()
                    val rows = ArrayList<List<String>>()
                    rows.add(cells(trimmed))
                    i += 2
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) { rows.add(cells(lines[i].trim())); i++ }
                    out.add(MdBlock.Table(rows))
                    continue
                }
                listRe.matches(line) -> {
                    flushPara()
                    val m = listRe.find(line)!!
                    val marker = m.groupValues[2].let { if (it[0].isDigit()) it.trimEnd(')').trimEnd('.') + "." else "•" }
                    var text = m.groupValues[3]
                    // Baris lanjutan (indentasi) milik item yang sama.
                    while (i + 1 < lines.size && lines[i + 1].startsWith("  ") && !listRe.matches(lines[i + 1]) && lines[i + 1].isNotBlank()) {
                        text += " " + lines[i + 1].trim(); i++
                    }
                    out.add(MdBlock.ListItem(marker, m.groupValues[1].length / 2, text))
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

    private fun cells(row: String): List<String> = row.trim().trim('|').split('|').map { it.trim() }

    private val inlineRe = Regex("""(`+)(.+?)\1|\*\*(.+?)\*\*|__(.+?)__|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?!\w)|~~(.+?)~~|\[([^\]]+)]\(([^)\s]+)\)|(https?://[^\s)>\]]+)""")

    fun inline(text: String, codeBg: Color, link: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in inlineRe.findAll(text)) {
            if (m.range.first > last) append(text.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontFamily = Mono, background = codeBg, fontSize = 13.5.sp)) { append(" ${g[2]} ") }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(g[3], codeBg, link)) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(g[4], codeBg, link)) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[5]) }
                g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
                g[7].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[7]) }
                g[8].isNotEmpty() -> withLink(LinkAnnotation.Url(g[9], TextLinkStyles(SpanStyle(color = link)))) { append(g[8]) }
                g[10].isNotEmpty() -> withLink(LinkAnnotation.Url(g[10], TextLinkStyles(SpanStyle(color = link)))) { append(g[10]) }
            }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}

/** Tampilkan Markdown. [blocks] sebaiknya di-remember oleh pemanggil agar parse tidak diulang. */
@Composable
fun MarkdownText(blocks: List<MdBlock>, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    val codeBg = Palette.SurfaceHighest
    val link = Palette.Blue
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, color = color)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) when (b) {
            is MdBlock.Para -> SelectionContainer { Text(MarkdownParser.inline(b.text, codeBg, link), style = body) }
            is MdBlock.Heading -> Text(
                MarkdownParser.inline(b.text, codeBg, link),
                style = body.copy(
                    fontSize = when (b.level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp },
                    fontWeight = FontWeight.SemiBold, lineHeight = 26.sp,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
            is MdBlock.ListItem -> Row(Modifier.padding(start = (b.indent * 14).dp)) {
                Text(b.marker, style = body.copy(color = Palette.TextDim), modifier = Modifier.width(if (b.marker == "•") 16.dp else 24.dp))
                SelectionContainer { Text(MarkdownParser.inline(b.text, codeBg, link), style = body) }
            }
            is MdBlock.Quote -> Row {
                Box(Modifier.width(3.dp).padding(vertical = 2.dp).background(Palette.Outline, RoundedCornerShape(2.dp)).size(3.dp, 20.dp))
                Text(MarkdownParser.inline(b.text, codeBg, link), style = body.copy(color = Palette.TextDim), modifier = Modifier.padding(start = 10.dp))
            }
            is MdBlock.Code -> CodeBlock(b.lang, b.code)
            is MdBlock.Table -> Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface).horizontalScroll(rememberScrollState()).padding(10.dp),
            ) {
                val widths = (0 until (b.rows.maxOfOrNull { it.size } ?: 0)).map { c -> b.rows.maxOf { it.getOrNull(c)?.length ?: 0 }.coerceAtMost(40) }
                Text(
                    b.rows.mapIndexed { r, row ->
                        row.mapIndexed { c, cell -> cell.take(40).padEnd(widths.getOrElse(c) { 0 }) }.joinToString("  ") +
                            if (r == 0) "\n" + widths.joinToString("  ") { "─".repeat(it) } else ""
                    }.joinToString("\n"),
                    fontFamily = Mono, fontSize = 12.sp, color = Palette.Text, lineHeight = 17.sp, softWrap = false,
                )
            }
            MdBlock.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).size(1.dp).background(Palette.Outline))
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
