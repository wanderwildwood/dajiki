package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsTest {

    @Test
    fun aSheetThatWasSeenAndIsNowMissingHasVanished() {
        assertTrue(vanished(Stamp(modified = 5L, size = 10L), null))
    }

    @Test
    fun aSheetStillThereHasNotVanished() {
        assertFalse(vanished(Stamp(5L, 10L), Stamp(6L, 12L)))
    }

    @Test
    fun aSheetNeverSeenHasNothingToVanishFrom() {
        assertFalse(vanished(null, null))
    }

    @Test
    fun theSpareCopyKeepsTheExtension() {
        assertEquals("notes (this phone).md", besideName("notes.md"))
        assertEquals("notes (this phone)", besideName("notes"))
    }
}
