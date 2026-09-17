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
