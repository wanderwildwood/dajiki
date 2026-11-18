package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun aSheetGoneFromTheFolderComesBackUnderItsOwnName() {
        // Today's page deleted from outside while open: the words keep the day's name.
        assertEquals("2026-11-14.txt", keepName("2026-11-14.txt", gone = true, taken = listOf("notes.txt")))
        // Taken again since (a sync app put one back): beside it, as for a sheet that changed.
        assertEquals("2026-11-14 (this phone).txt", keepName("2026-11-14.txt", gone = true, taken = listOf("2026-11-14.txt")))
        // Changed elsewhere, still there: always beside it.
        assertEquals("notes (this phone).md", keepName("notes.md", gone = false, taken = emptyList()))
    }

    // t/16264: with "New sheets saved as" on plain text, sheets made through Cabinet's folder
    // picker came out with no extension and no text type, and the list passed over them.

    @Test
    fun aDateNamedSheetWithNoExtensionAndNoTextTypeIsListed() {
        assertTrue(isWriting("2026-11-08 1432", "application/octet-stream"))
        assertTrue(isWriting("2026-11-08", "application/octet-stream"))
        assertTrue(isWriting("2026-11-08 1432 (1)", null))
    }

    @Test
    fun textAndMarkdownSheetsAreStillListed() {
        assertTrue(isWriting("2026-11-08 1432.txt", "text/plain"))
        assertTrue(isWriting("2026-11-08.md", "application/octet-stream"))
        assertTrue(isWriting("Letter to Ada.TXT", null))
        assertTrue(isWriting("README", "text/plain"))
    }

    @Test
    fun foldersAndOtherFilesAreNotListed() {
        assertFalse(isWriting("2026-11-08", "vnd.android.document/directory"))
        assertFalse(isWriting("2026-11-08 holiday.jpg", "image/jpeg"))
        assertFalse(isWriting("backup", "application/octet-stream"))
    }

    @Test
    fun aPlainSheetTheProviderLeftBareGetsItsExtensionBack() {
        assertEquals("2026-11-08 1432.txt", withExtension("2026-11-08 1432", "txt"))
        assertEquals("2026-11-08 1432 (1).txt", withExtension("2026-11-08 1432 (1)", "txt"))
    }

    @Test
    fun aSheetThatKeptItsExtensionIsLeftAlone() {
        assertNull(withExtension("2026-11-08 1432.txt", "txt"))
        assertNull(withExtension("2026-11-08 1432 (1).txt", "txt"))
        assertNull(withExtension("2026-11-08.TXT", "txt"))
    }

    @Test
    fun aDaysPageIsTheDiaryWhereverItIs() {
        assertTrue(isDiaryPage("2026-11-08.txt", inDiaryFolder = false))
        assertTrue(isDiaryPage("2026-11-08.md", inDiaryFolder = false))
        assertTrue(isDiaryPage("2026-11-08", inDiaryFolder = false))
        // The copy kept when a sync clashed is the same day's page.
        assertTrue(isDiaryPage(besideName("2026-11-08.txt"), inDiaryFolder = false))
        assertTrue(isDiaryPage(besideName(besideName("2026-11-08.txt")), inDiaryFolder = false))
        assertTrue(isDiaryPage("Notes from the walk.txt", inDiaryFolder = true))
    }

    @Test
    fun aCopyOfADaysPageIsLockedWithIt() {
        // A second clash while the first copy is still there: the provider numbers it.
        assertTrue(isDiaryPage("2026-11-08 (this phone) (1).txt", inDiaryFolder = false))
        // Today made twice: the provider's own "(1)".
        assertTrue(isDiaryPage("2026-11-08 (1).txt", inDiaryFolder = false))
        // Sync apps' copies of the same page.
        assertTrue(isDiaryPage("2026-11-08 (conflicted copy 2026-11-09 101500).txt", inDiaryFolder = false))
        assertTrue(isDiaryPage("2026-11-08.sync-conflict-20261109-101500-ABCDEFG.txt", inDiaryFolder = false))
        assertTrue(isDiaryPage("2026-11-08.txt.sync-conflict-20261109-101500-ABCDEFG", inDiaryFolder = false))
        // Still not: a sheet started at a minute, and its own numbered copy.
        assertFalse(isDiaryPage("2026-11-08 1432 (1).txt", inDiaryFolder = false))
        assertFalse(isDiaryPage("2026-11-08 notes.txt", inDiaryFolder = false))
    }

    @Test
    fun aSheetNotInUtf8IsRefusedRatherThanSpoilt() {
        assertEquals("café", decodeSheet("café".toByteArray(Charsets.UTF_8)))
        val latin1 = "café".toByteArray(Charsets.ISO_8859_1)
        assertTrue(runCatching { decodeSheet(latin1) }.isFailure)
        assertTrue(runCatching { decodeSheet(ByteArray(MAX_SHEET_BYTES + 1)) }.isFailure)
    }

    @Test
    fun aRenameKeepsTheExtensionUnlessOneWasTyped() {
        assertEquals("Ch. 2 draft.txt", renamed("Draft.txt", "Ch. 2 draft"))
        assertEquals("v1.2 notes.md", renamed("Notes.md", "v1.2 notes"))
        assertEquals("Letter.md", renamed("Letter.txt", "Letter.md"))
        assertEquals("Letter.TXT", renamed("Letter.txt", "Letter.TXT"))
        assertEquals("Plain", renamed("2026-11-08", "Plain"))
    }

    @Test
    fun anOrdinarySheetIsNotTheDiary() {
        assertFalse(isDiaryPage("2026-11-08 1432.txt", inDiaryFolder = false))
        assertFalse(isDiaryPage("Letter to Ada.txt", inDiaryFolder = false))
    }

    @Test
    fun theSheetsTabLeavesTheDiaryFolderOutOnlyWhenAsked() {
        // (name, in the Diary folder)
        val all = listOf("2026-11-08.txt" to true, "Letter to Ada.txt" to false)
        assertEquals(all, sheetsTab(all, diaryFolder = false) { it.second })
        assertEquals(listOf("Letter to Ada.txt"), sheetsTab(all, diaryFolder = true) { it.second }.map { it.first })
    }

    @Test
    fun aWriteThatGoesThroughIsNotTriedAgain() {
        var tries = 0
        assertNull(writeTwice { tries++ })
        assertEquals(1, tries)
    }

    @Test
    fun aWriteThatFailsOnceIsTriedOnceMore() {
        var tries = 0
        assertNull(writeTwice { if (++tries == 1) error("passing") })
        assertEquals(2, tries)
    }

    @Test
    fun aWriteThatFailsTwiceStopsThereAndKeepsBothReasons() {
        var tries = 0
        val failed = writeTwice { error("try ${++tries}") }
        assertEquals(2, tries)
        assertEquals("try 2", failed?.message)
        assertEquals("try 1", failed?.suppressed?.single()?.message)
    }
}
