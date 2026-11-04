package com.kroombox.termx.ui

import com.kroombox.termx.ui.components.MarkdownParser
import com.kroombox.termx.ui.components.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test fun headingsListsAndCode() {
        val b = MarkdownParser.parse("# Title\n\nSome text\n\n- one\n- two\n\n```kotlin\nval x = 1\n```")
        assertEquals(MdBlock.Heading(1, "Title"), b[0])
        assertTrue(b[1] is MdBlock.Para)
        assertEquals(2, b.count { it is MdBlock.ListItem })
        val code = b.last() as MdBlock.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1", code.code)
        assertEquals(false, code.open)
    }

    @Test fun unterminatedFenceWhileStreaming() {
        val code = MarkdownParser.parse("```sh\nls -la").last() as MdBlock.Code
        assertEquals(true, code.open)
        assertEquals("ls -la", code.code)
    }

    @Test fun tableAndRule() {
        val b = MarkdownParser.parse("| a | b |\n|---|---|\n| 1 | 2 |\n\n---")
        val t = b.first() as MdBlock.Table
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), t.rows)
        assertEquals(MdBlock.Rule, b.last())
    }

    @Test fun tasksFrontMatterAlignedTable() {
        val b = MarkdownParser.parse("---\ntitle: x\n---\n# H\n\n- [x] done\n- [ ] todo\n\nName | Qty\n:--- | ---:\nA | 1")
        assertEquals("yaml", (b[0] as MdBlock.Code).lang)
        assertEquals(MdBlock.Heading(1, "H"), b[1])
        assertEquals(true, (b[2] as MdBlock.ListItem).checked)
        assertEquals("done", (b[2] as MdBlock.ListItem).text)
        assertEquals(false, (b[3] as MdBlock.ListItem).checked)
        val t = b[4] as MdBlock.Table
        assertEquals(listOf('l', 'r'), t.align)
        assertEquals(listOf("A", "1"), t.rows[1])
    }

    @Test fun inlineEscapesImagesAndBreaks() {
        val c = androidx.compose.ui.graphics.Color.Black
        assertEquals("a *b* c", MarkdownParser.inline("a \\*b\\* c", c, c).text)
        assertEquals("x\ny", MarkdownParser.inline("x<br>y", c, c).text)
        assertTrue(MarkdownParser.inline("![logo](https://e.com/a.png)", c, c).text.endsWith("logo"))
        assertEquals("bold link", MarkdownParser.inline("**bold** [link](https://e.com)", c, c).text)
    }
}
