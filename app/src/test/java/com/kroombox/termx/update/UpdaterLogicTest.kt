package com.kroombox.termx.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterLogicTest {
    @Test
    fun versionComparison() {
        assertTrue(Versions.isNewer("0.9.0", "0.8.4"))
        assertTrue(Versions.isNewer("0.10.0", "0.9.9"))
        assertTrue(Versions.isNewer("1.0", "0.99.99"))
        assertTrue(Versions.isNewer("0.9.1", "0.9"))
        assertFalse(Versions.isNewer("0.9.0", "0.9.0"))
        assertFalse(Versions.isNewer("0.8.4", "0.9.0"))
        assertFalse(Versions.isNewer("0.9.0", "0.9.0-debug"))
    }

    @Test
    fun changelogSectionIsExtracted() {
        val md = """
            # Changelog

            ## [0.9.0] - 2026-11-03
            ### Added
            - In-app updates.

            ## [0.8.4] - 2026-11-03
            ### Fixed
            - Typing lag.
        """.trimIndent()
        assertEquals("### Added\n- In-app updates.", Changelog.section(md, "0.9.0"))
        assertEquals("### Fixed\n- Typing lag.", Changelog.section(md, "0.8.4"))
        assertEquals("", Changelog.section(md, "1.0.0"))
    }
}
