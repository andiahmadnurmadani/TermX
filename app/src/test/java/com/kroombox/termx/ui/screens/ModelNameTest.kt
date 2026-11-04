package com.kroombox.termx.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelNameTest {
    @Test fun prettifiesClaudeModelIds() {
        assertEquals("Opus 5.5", prettyModelId("claude-opus-5-5"))
        assertEquals("Fable 5.1", prettyModelId("claude-fable-5-1"))
        assertEquals("Sonnet 5", prettyModelId("claude-sonnet-5"))
        assertEquals("Haiku 4.5", prettyModelId("claude-haiku-4-5-20251001"))
        assertEquals("Opus 5.5 (1M)", prettyModelId("claude-opus-5-5[1m]"))
        assertEquals("Sonnet", prettyModelId("sonnet"))
        assertEquals("gpt-5", prettyModelId("gpt-5"))
    }
}
