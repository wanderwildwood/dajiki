package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Test

/** The name a sheet ends in, and that a forked copy keeps it. */
class FormatTest {

    @Test
    fun `plain text is txt and markdown is md`() {
        assertEquals("txt", Format.PLAIN.extension)
        assertEquals("md", Format.MARKDOWN.extension)
    }

    @Test
    fun `the setting cycles from plain to markdown and back`() {
        assertEquals(Format.MARKDOWN, next(Format.PLAIN))
        assertEquals(Format.PLAIN, next(Format.MARKDOWN))
    }

    @Test
    fun `a markdown sheet forks to a markdown copy`() {
        assertEquals("2026-10-08 0712 (this phone).md", besideName("2026-10-08 0712.md"))
    }
}
