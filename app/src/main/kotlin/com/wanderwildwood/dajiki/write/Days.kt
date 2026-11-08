package com.wanderwildwood.dajiki.write

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/*
 * The diary half of the app: which day a sheet belongs to, which sheet is today's, what a
 * second visit to it adds, and where a word turns up in the folder. All of it plain functions
 * of names, dates and text, kept apart from Android so that each rule can be tested by itself.
 */

/**
 * The day at the start of a sheet's name: `2026-11-08`, `2026-11-08 1432` and
 * `2026-11-08 1432 (1).txt` are all the 8th of November. Underscores and dots between the
 * numbers count as well, since other apps name their notes that way. Null where the name
 * does not start with a real date (a 31st of February is not one).
 */
fun dayInName(name: String): LocalDate? {
    val match = DAY_IN_NAME.find(name) ?: return null
    val (year, month, day) = match.destructured
    return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/**
 * The day a sheet belongs to: the date its name starts with, and where the name carries none,
 * the day it was last written to. A sheet written in the small hours of the 9th under a name
 * started on the 8th stays on the 8th, which is what a diary means by it. Null for a sheet
 * with neither, which a provider that dates nothing can produce.
 */
fun dayOf(name: String, modified: Long, zone: ZoneId): LocalDate? =
    dayInName(name) ?: if (modified > 0L) Instant.ofEpochMilli(modified).atZone(zone).toLocalDate() else null

/** What today's page is called, before the extension: the date and nothing else. */
fun dayName(day: LocalDate): String = day.toString()

/**
 * Whether [name] is the page for [day]: the bare date with an extension this app writes or
 * opens. "2026-11-08.txt" is the 8th's page; "2026-11-08 1432.txt" is an ordinary sheet that
 * happened to be started that day, and stays one.
 */
fun isPageFor(name: String, day: LocalDate): Boolean {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name == dayName(day)
    val stem = name.substring(0, dot)
    val extension = name.substring(dot + 1).lowercase(Locale.ROOT)
    return stem == dayName(day) && extension in PAGE_EXTENSIONS
}

/**
 * Today's page among [names], or null where there is none yet. Where a folder holds two
 * (a `.txt` from this phone and a `.md` from a laptop), the one in the format new sheets are
 * saved as wins, so the choice is the same every time.
 */
fun pageFor(names: List<String>, day: LocalDate, format: Format): String? {
    val pages = names.filter { isPageFor(it, day) }
    return pages.firstOrNull { it.lowercase(Locale.ROOT).endsWith(".${format.extension}") } ?: pages.firstOrNull()
}

/**
 * What today's page holds when it is opened again: what is there, a blank line, and a line
 * with the time, ready for the new writing under it.
 *
 * Nothing is added to an empty page (the name already says the day), or to one written to in
 * the last [AGAIN_AFTER_MINUTES], which is the same sitting carried on rather than a later one.
 * [minutesSince] is null where the folder does not say when the page was written.
 */
fun pageReopened(text: String, time: String, minutesSince: Long?): String {
    if (text.isBlank()) return text
    if (minutesSince != null && minutesSince in 0 until AGAIN_AFTER_MINUTES) return text
    return text.trimEnd() + "\n\n" + timeLine(time) + "\n"
}

/** The line that heads a later sitting on the same page. A dash, so it reads as a mark rather than as writing. */
fun timeLine(time: String): String = "— $time"

/** Where a word turns up in a sheet: which line (from 0), where that line starts, and where the match starts. */
data class Found(val line: Int, val lineStart: Int, val at: Int, val text: String)

/**
 * Every line of [text] that holds [query], ignoring case, the first match on each.
 *
 * Plain text, not a pattern: a full stop is a full stop. Matched letter by letter with
 * Kotlin's own case folding so the positions found are positions in the text as written,
 * which the page needs to put the cursor in the right place.
 */
fun findIn(text: String, query: String): List<Found> {
    val wanted = query.trim()
    if (wanted.isEmpty()) return emptyList()
    val found = mutableListOf<Found>()
    var start = 0
    var line = 0
    while (start <= text.length) {
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val row = text.substring(start, end)
        val at = row.indexOf(wanted, ignoreCase = true)
        if (at >= 0) found += Found(line = line, lineStart = start, at = start + at, text = row)
        if (end >= text.length) break
        start = end + 1
        line++
    }
    return found
}

/**
 * A long line cut down to the part around the match, so the match is on the screen: up to
 * [before] characters ahead of it, a "…" where anything was cut. Returns the shown text and
 * where the match starts in it.
 */
fun around(line: String, at: Int, before: Int = 40): Pair<String, Int> {
    val trimmed = line.trimEnd()
    if (at <= before) return trimmed to at
    val from = (at - before).let { cut ->
        // Start on a word where one is near, rather than half way through it.
        val space = trimmed.indexOf(' ', cut)
        if (space in cut until at) space + 1 else cut
    }
    return ("…" + trimmed.substring(from)) to (at - from + 1)
}

/**
 * The weeks of [month] as rows of seven days starting on [firstDay], with null in the cells
 * that belong to the months either side. Four to six rows, as the month needs.
 */
fun monthWeeks(month: YearMonth, firstDay: DayOfWeek): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - firstDay.value + 7) % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    return padded.chunked(7)
}

/** The days of the week in the order the calendar shows them, starting on [firstDay]. */
fun weekDays(firstDay: DayOfWeek): List<DayOfWeek> = (0 until 7).map { firstDay.plus(it.toLong()) }

/** A new page only counts as a later sitting after this long; within it, writing carries on as it was. */
const val AGAIN_AFTER_MINUTES = 15L

private val PAGE_EXTENSIONS = setOf("txt", "md", "markdown", "text")
private val DAY_IN_NAME = Regex("""^(\d{4})[-_.](\d{2})[-_.](\d{2})(?!\d)""")
