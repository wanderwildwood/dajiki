package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class DaysTest {
    private val nov8 = LocalDate.of(2026, 11, 8)
    private val newYork = ZoneId.of("America/New_York")

    // ---- Which day a sheet belongs to

    @Test
    fun theDayIsReadFromTheStartOfTheName() {
        assertEquals(nov8, dayInName("2026-11-08.txt"))
        assertEquals(nov8, dayInName("2026-11-08 1432.txt"))
        assertEquals(nov8, dayInName("2026-11-08 1432 (1).md"))
        assertEquals(nov8, dayInName("2026_11_08 walk.txt"))
        assertEquals(nov8, dayInName("2026.11.08"))
    }

    @Test
    fun aNameThatIsNotADayHasNone() {
        assertNull(dayInName("Letter to Ada.txt"))
        assertNull(dayInName("2026-02-31.txt")) // no such day
        assertNull(dayInName("2026-11-081.txt")) // a longer number, not a date
        assertNull(dayInName("notes 2026-11-08.txt")) // not at the start
    }

    @Test
    fun aNameWithNoDayFallsBackToWhenItWasWritten() {
        val evening = ZonedDateTime.of(2026, 11, 8, 21, 40, 0, 0, newYork).toInstant().toEpochMilli()
        assertEquals(nov8, dayOf("Letter to Ada.txt", evening, newYork))
        assertNull(dayOf("Letter to Ada.txt", 0L, newYork))
    }

    @Test
    fun theNameWinsOverTheTimeItWasWritten() {
        // Started late on the 8th, last written after midnight: still the 8th's.
        val pastMidnight = ZonedDateTime.of(2026, 11, 9, 0, 20, 0, 0, newYork).toInstant().toEpochMilli()
        assertEquals(nov8, dayOf("2026-11-08 2350.txt", pastMidnight, newYork))
    }

    // ---- Today's page

    @Test
    fun todaysPageIsTheBareDate() {
        assertEquals("2026-11-08", dayName(nov8))
        assertTrue(isPageFor("2026-11-08.txt", nov8))
        assertTrue(isPageFor("2026-11-08.md", nov8))
        assertTrue(isPageFor("2026-11-08", nov8))
        assertFalse(isPageFor("2026-11-08 1432.txt", nov8)) // an ordinary sheet started that day
        assertFalse(isPageFor("2026-11-08 (this phone).txt", nov8)) // a conflict copy
        assertFalse(isPageFor("2026-11-07.txt", nov8))
        assertFalse(isPageFor("2026-11-08.pdf", nov8))
    }

    @Test
    fun twoPagesForOneDayPickTheFormatNewSheetsUse() {
        val names = listOf("2026-11-08 0912.txt", "2026-11-08.md", "2026-11-08.txt")
        assertEquals("2026-11-08.txt", pageFor(names, nov8, Format.PLAIN))
        assertEquals("2026-11-08.md", pageFor(names, nov8, Format.MARKDOWN))
        assertEquals("2026-11-08.md", pageFor(listOf("2026-11-08.md"), nov8, Format.PLAIN))
        assertNull(pageFor(listOf("2026-11-08 0912.txt"), nov8, Format.PLAIN))
    }

    @Test
    fun aLaterSittingGetsATimeLineUnderWhatIsThere() {
        val morning = "Fog on the ridge. Tomas Reyes came by with eggs.\n"
        assertEquals(
            "Fog on the ridge. Tomas Reyes came by with eggs.\n\n— 21:40\n",
            pageReopened(morning, "21:40", minutesSince = 600),
        )
    }

    @Test
    fun noTimeLineOnAnEmptyPage() {
        assertEquals("", pageReopened("", "21:40", minutesSince = 600))
        assertEquals("  \n", pageReopened("  \n", "21:40", minutesSince = null))
    }

    @Test
    fun noTimeLineWhenTheSameSittingCarriesOn() {
        val text = "Fog on the ridge."
        assertEquals(text, pageReopened(text, "9:42 PM", minutesSince = 2))
        assertEquals(text, pageReopened(text, "9:42 PM", minutesSince = AGAIN_AFTER_MINUTES - 1))
        assertEquals("$text\n\n— 9:42 PM\n", pageReopened(text, "9:42 PM", minutesSince = AGAIN_AFTER_MINUTES))
    }

    @Test
    fun aPageWithNoDateOnItCountsAsALaterSitting() {
        assertEquals("a\n\n— 08:05\n", pageReopened("a", "08:05", minutesSince = null))
        // A clock that ran backwards is not "a moment ago".
        assertEquals("a\n\n— 08:05\n", pageReopened("a", "08:05", minutesSince = -30))
    }

    // ---- Search

    @Test
    fun searchFindsEachLineOnceInAnyCase() {
        val text = "Walked to the creek.\nThe CREEK was high, creek water brown.\nNothing else."
        val found = findIn(text, "creek")
        assertEquals(listOf(0, 1), found.map { it.line })
        assertEquals("Walked to the creek.", found[0].text)
        assertEquals(text.indexOf("creek"), found[0].at)
        assertEquals(text.indexOf("CREEK"), found[1].at)
        assertEquals(text.indexOf("The CREEK"), found[1].lineStart)
    }

    @Test
    fun searchIsPlainTextNotAPattern() {
        assertEquals(1, findIn("a.b\naxb", "a.b").size)
        assertEquals(0, findIn("costs \$5", "\\$").size)
    }

    @Test
    fun anEmptySearchFindsNothing() {
        assertTrue(findIn("anything", "").isEmpty())
        assertTrue(findIn("anything", "   ").isEmpty())
    }

    @Test
    fun theLastLineAndEmptyLinesCount() {
        val text = "one\n\nthree\n"
        assertEquals(listOf(2), findIn(text, "THREE").map { it.line })
        assertEquals(text.indexOf("three"), findIn(text, "three").single().at)
    }

    @Test
    fun accentedLettersMatchAcrossCase() {
        val found = findIn("Über den Fluss", "über")
        assertEquals(0, found.single().at)
    }

    @Test
    fun aLongLineIsCutToShowTheMatch() {
        val line = "In the morning we walked along the old mill road as far as the creek, then back."
        val at = line.indexOf("creek")
        val (shown, where) = around(line, at)
        assertTrue(shown.startsWith("…"))
        assertEquals("creek", shown.substring(where, where + 5))
        val (short, same) = around("the creek", 4)
        assertEquals("the creek" to 4, short to same)
    }

    // ---- The month

    @Test
    fun aMonthStartsInTheRightColumn() {
        // 1 November 2026 is a Sunday.
        val sundayFirst = monthWeeks(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(5, sundayFirst.size)
        assertEquals(LocalDate.of(2026, 11, 1), sundayFirst[0][0])
        assertEquals(LocalDate.of(2026, 11, 30), sundayFirst[4][1])
        assertNull(sundayFirst[4][2])

        val mondayFirst = monthWeeks(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(6, mondayFirst.size)
        assertNull(mondayFirst[0][5])
        assertEquals(LocalDate.of(2026, 11, 1), mondayFirst[0][6])
        assertTrue(mondayFirst.all { it.size == 7 })
    }

    @Test
    fun theWeekDaysWrapRound() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            weekDays(DayOfWeek.MONDAY),
        )
        assertEquals(DayOfWeek.SATURDAY, weekDays(DayOfWeek.SUNDAY).last())
    }
}
