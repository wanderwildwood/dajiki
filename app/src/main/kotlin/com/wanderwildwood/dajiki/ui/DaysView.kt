package com.wanderwildwood.dajiki.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.dajiki.R
import com.wanderwildwood.dajiki.write.Looking
import com.wanderwildwood.dajiki.write.Sheet
import com.wanderwildwood.dajiki.write.dayOf
import com.wanderwildwood.dajiki.write.isPageFor
import com.wanderwildwood.dajiki.write.monthWeeks
import com.wanderwildwood.dajiki.write.weekDays
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as DayText
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/**
 * The folder as a month of days, for reading a diary back: a dot under each day there is
 * writing, today in a box, ‹ and › for the months either side.
 *
 * Which day a sheet belongs to is the date its name starts with, or failing that the day it
 * was last written ([dayOf]). Tapping a day with one sheet opens it; a day with several lists
 * them beside the month (below it, held upright) to choose from.
 *
 * Black on white and nothing that moves: the month changes in one repaint.
 */
@Composable
internal fun DaysView(sheets: List<Sheet>, looking: Looking, act: FolderActions) {
    val zone = remember { ZoneId.systemDefault() }
    val byDay = remember(sheets) {
        sheets.groupBy { dayOf(it.name, it.modified, zone) }
    }
    val today = LocalDate.now()
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weeks = remember(looking.month, firstDay) { monthWeeks(looking.month, firstDay) }

    val tap: (LocalDate) -> Unit = { day ->
        act.pickDay(day)
        byDay[day]?.singleOrNull()?.let { act.open(it, null) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Held across: the month on the left at the full height, the day's sheets beside it.
            Row(Modifier.fillMaxSize()) {
                Month(
                    weeks = weeks,
                    firstDay = firstDay,
                    written = byDay.keys,
                    today = today,
                    picked = looking.day,
                    small = true,
                    onDay = tap,
                    modifier = Modifier.weight(1.15f).fillMaxHeight().padding(start = 12.dp, top = 4.dp, bottom = 6.dp),
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    MonthStep(looking.month, act)
                    DaySheets(looking.day, byDay[looking.day].orEmpty(), today, act)
                }
            }
        } else {
            // Held upright: the month across the top, square-ish days, the day's sheets below.
            val rowHeight = minOf(maxWidth / 7, 52.dp)
            Column(Modifier.fillMaxSize()) {
                MonthStep(looking.month, act)
                Month(
                    weeks = weeks,
                    firstDay = firstDay,
                    written = byDay.keys,
                    today = today,
                    picked = looking.day,
                    small = false,
                    onDay = tap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WEEKDAY_ROW + rowHeight * weeks.size)
                        .padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(4.dp))
                DaySheets(looking.day, byDay[looking.day].orEmpty(), today, act)
            }
        }
    }
}

/** ‹ November 2026 ›, the name of the month a press back to this month. */
@Composable
private fun MonthStep(month: YearMonth, act: FolderActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Step("‹", stringResource(R.string.days_cd_earlier)) { act.stepMonth(-1) }
        TextMMD(
            text = monthName(month),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = act.thisMonth)
                .padding(vertical = 10.dp),
        )
        Step("›", stringResource(R.string.days_cd_later)) { act.stepMonth(1) }
    }
}

@Composable
private fun Step(mark: String, said: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp, 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = mark, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * The weeks of a month. The rows share the height they are given, so the same grid fills a
 * short panel held across and a tall one held upright.
 */
@Composable
private fun Month(
    weeks: List<List<LocalDate?>>,
    firstDay: DayOfWeek,
    written: Set<LocalDate?>,
    today: LocalDate,
    picked: LocalDate,
    small: Boolean,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val number = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(WEEKDAY_ROW), verticalAlignment = Alignment.CenterVertically) {
            for (day in weekDays(firstDay)) {
                TextMMD(
                    text = day.getDisplayName(DayText.SHORT_STANDALONE, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in weeks) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (day in week) {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                        if (day != null) {
                            Day(
                                day = day,
                                written = day in written,
                                today = day == today,
                                picked = day == picked,
                                style = number,
                                onClick = { onDay(day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One day. Writing is a black dot under the number; today is a box round it; the day whose
 * sheets are listed has its number in bold. Three marks that never stand in for each other,
 * so a day can carry all three and each still reads.
 */
@Composable
private fun Day(
    day: LocalDate,
    written: Boolean,
    today: Boolean,
    picked: Boolean,
    style: TextStyle,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val said = buildString {
        append(longDay(day))
        if (written) append(", ").append(stringResource(R.string.days_cd_written))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (today) Modifier.border(2.dp, ink, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            TextMMD(
                text = day.dayOfMonth.toString(),
                style = style,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            Box(
                Modifier
                    .size(DOT)
                    .background(if (written) Color.Black else Color.Transparent, CircleShape),
            )
        }
    }
}

/**
 * The picked day and its sheets. Today with no page of its own yet offers to start one, so
 * the calendar is also a way in to writing.
 */
@Composable
private fun DaySheets(day: LocalDate, sheets: List<Sheet>, today: LocalDate, act: FolderActions) {
    PagedColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
        showCutRow = true,
    ) {
        item {
            TextMMD(
                text = longDay(day),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
        }
        if (day == today && sheets.none { isPageFor(it.name, today) }) {
            item {
                TextMMD(
                    text = stringResource(R.string.days_start_today),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = act.today)
                        .padding(vertical = 12.dp),
                )
                if (sheets.isNotEmpty()) DottedRule(start = 0.dp, end = 0.dp)
            }
        } else if (sheets.isEmpty()) {
            item {
                TextMMD(
                    text = stringResource(R.string.days_nothing),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        itemsIndexed(sheets, key = { _, it -> it.uri.toString() }) { i, sheet ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { act.open(sheet, null) }
                    .padding(vertical = 12.dp),
            ) {
                TextMMD(text = sheet.name, style = MaterialTheme.typography.bodyMedium)
                if (sheet.modified > 0L) {
                    TextMMD(text = written(sheet.modified), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (i < sheets.lastIndex) DottedRule(start = 0.dp, end = 0.dp)
        }
    }
}

/** "Sunday, November 8", or with the year where it is not this year's. */
internal fun longDay(day: LocalDate): String {
    val skeleton = if (day.year == LocalDate.now().year) "EEEEMMMMd" else "EEEEMMMMdyyyy"
    return format(skeleton, day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
}

/** "November 2026". */
private fun monthName(month: YearMonth): String =
    format("MMMMyyyy", month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

/** When a sheet was last written: "Last written Nov 9, 9:40 PM", in the phone's own clock. */
@Composable
private fun written(modified: Long): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(modified))
    val date = format("MMMd", modified)
    return stringResource(R.string.days_last_written, date, time)
}

private fun format(skeleton: String, millis: Long): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return java.text.SimpleDateFormat(pattern, locale).format(Date.from(Instant.ofEpochMilli(millis)))
}

private val WEEKDAY_ROW: Dp = 26.dp
private val DOT: Dp = 6.dp
