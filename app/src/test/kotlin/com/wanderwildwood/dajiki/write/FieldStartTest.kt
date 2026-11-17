package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldStartTest {

    @Test
    fun aSheetJustOpenedStartsAtTheEnd() {
        assertEquals("Dear Ada," to 9, fieldStart("Dear Ada,", null, "Dear Ada,"))
    }

    @Test
    fun aSheetOpenedFromASearchStartsAtTheWordFound() {
        assertEquals("one two three" to 4, fieldStart("one two three", 4, "one two three"))
    }

    @Test
    fun aPageMadeAgainKeepsWhatWasTypedSinceItOpened() {
        // Back from under the lock screen, or after the activity was rebuilt: the words typed
        // since the sheet opened are what the field starts with, not the sheet as it opened.
        assertEquals(
            "Dear Ada,\nThe rain held off." to 28,
            fieldStart("Dear Ada,", null, "Dear Ada,\nThe rain held off."),
        )
    }

    @Test
    fun aSearchCursorIsNotCarriedIntoTextThatHasChanged() {
        assertEquals("one" to 3, fieldStart("one two three", 8, "one"))
    }

    @Test
    fun everythingDeletedSinceOpeningStaysDeleted() {
        assertEquals("" to 0, fieldStart("Dear Ada,", null, ""))
    }
}
