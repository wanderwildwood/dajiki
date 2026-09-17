package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Test

class WordsTest {

    @Test
    fun `nothing is no words`() {
        assertEquals(0, countWords(""))
        assertEquals(0, countWords("   \n\t  "))
    }

    @Test
    fun `words are runs of what is not whitespace`() {
        assertEquals(1, countWords("one"))
        assertEquals(3, countWords("one two three"))
    }

    @Test
    fun `whitespace at either end does not add a word`() {
        assertEquals(2, countWords("  one two  "))
        assertEquals(2, countWords("\none two\n"))
    }

    @Test
    fun `runs of whitespace between words count once`() {
        assertEquals(2, countWords("one     two"))
        assertEquals(3, countWords("one\n\n\ntwo\tthree"))
    }

    @Test
    fun `a hyphenated word and a contraction are each one word`() {
        assertEquals(1, countWords("twenty-one"))
        assertEquals(1, countWords("don't"))
        assertEquals(3, countWords("don't count twenty-one"))
    }

    @Test
    fun `an em dash between two words is not a word of its own`() {
        // Set close, which is how it is typed: "one—two" is two words, not three.
        assertEquals(1, countWords("one—two"))
        // Set open, it is a token of its own, and this counts it. That is a known and
        // accepted disagreement with some editors rather than an oversight: nothing here
        // tries to tell punctuation from words.
        assertEquals(3, countWords("one — two"))
    }

    @Test
    fun `a paragraph counts as a writer would count it`() {
        val paragraph = "It was a bright cold day in April, and the clocks were striking " +
            "thirteen."
        assertEquals(14, countWords(paragraph))
    }
}

class ChangedBetweenTest {

    @Test
    fun `the same stamp is no change`() {
        val stamp = Stamp(modified = 1_700_000_000_000, size = 412)
        assertEquals(false, changedBetween(stamp, stamp))
    }

    @Test
    fun `a different length is a change even with nothing dated`() {
        assertEquals(
            true,
            changedBetween(Stamp(modified = 0, size = 412), Stamp(modified = 0, size = 500)),
        )
    }

    @Test
    fun `a different date is a change`() {
        assertEquals(
            true,
            changedBetween(
                Stamp(modified = 1_700_000_000_000, size = 412),
                Stamp(modified = 1_700_000_005_000, size = 412),
            ),
        )
    }

    @Test
    fun `the same length and no usable dates cannot be answered`() {
        // The one case the stamps cannot settle. It must say so rather than say "unchanged",
        // because "unchanged" is the answer that overwrites somebody's afternoon.
        assertEquals(
            null,
            changedBetween(Stamp(modified = 0, size = 412), Stamp(modified = 0, size = 412)),
        )
    }

    @Test
    fun `a missing length falls back to the dates`() {
        assertEquals(
            false,
            changedBetween(
                Stamp(modified = 1_700_000_000_000, size = -1),
                Stamp(modified = 1_700_000_000_000, size = -1),
            ),
        )
    }
}

class BesideNameTest {

    @Test
    fun `the extension is kept`() {
        assertEquals("notes (this phone).txt", besideName("notes.txt"))
        assertEquals("draft (this phone).md", besideName("draft.md"))
    }

    @Test
    fun `a name with no extension just gets the marker`() {
        assertEquals("notes (this phone)", besideName("notes"))
    }

    @Test
    fun `only the last dot counts`() {
        assertEquals("2026-09-17 0840 (this phone).txt", besideName("2026-09-17 0840.txt"))
        assertEquals("a.b.c (this phone).txt", besideName("a.b.c.txt"))
    }

    @Test
    fun `a leading dot is part of the name, not an extension`() {
        assertEquals(".profile (this phone)", besideName(".profile"))
    }
}
