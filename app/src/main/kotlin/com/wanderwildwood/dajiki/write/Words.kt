package com.wanderwildwood.dajiki.write

/**
 * How many words are on the page.
 *
 * A word is a run of anything that is not whitespace, which is the count a writer working
 * to a length expects: an em dash between two words is not a third word, but "don't" and
 * "twenty-one" are each one. Nothing here tries to be cleverer than that — a count that
 * disagreed with the one the reader's editor or publisher uses would be worse than no
 * count at all.
 */
fun countWords(text: String): Int {
    var words = 0
    var inWord = false
    for (character in text) {
        if (character.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            words++
        }
    }
    return words
}
