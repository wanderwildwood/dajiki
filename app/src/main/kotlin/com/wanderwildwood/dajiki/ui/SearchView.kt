package com.wanderwildwood.dajiki.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.dajiki.R
import com.wanderwildwood.dajiki.write.Hit
import com.wanderwildwood.dajiki.write.Looking
import com.wanderwildwood.dajiki.write.around
import com.wanderwildwood.dajiki.write.dayInName

/**
 * Every sheet in the folder, searched for a word.
 *
 * Plain text and any case. Each line it turns up on is a row: the line, cut down to the part
 * around the word with the word in bold, and under it the sheet it is in. A row opens the
 * sheet with the cursor at the word, so what was found is what is on the screen.
 */
@Composable
internal fun SearchView(looking: Looking, act: FolderActions) {
    var value by remember { mutableStateOf(TextFieldValue(looking.query, TextRange(looking.query.length))) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Straight into the field, so a keyboard user types the word without reaching for the screen.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        TextFieldMMD(
            value = value,
            onValueChange = {
                value = it
                act.search(it.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(focus),
            singleLine = true,
            placeholder = { TextMMD(text = stringResource(R.string.search_hint), style = MaterialTheme.typography.labelSmall) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // The search runs by itself once the typing pauses; the key only puts the keyboard
            // away so the results have the screen.
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )

        PagedColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
            showCutRow = true,
        ) {
            item {
                val count = looking.hits.size
                val said = when {
                    looking.query.isBlank() -> stringResource(R.string.search_empty)
                    looking.searching || looking.searched != looking.query -> stringResource(R.string.search_searching)
                    count == 0 -> stringResource(R.string.search_none, looking.searched.trim())
                    looking.more -> stringResource(R.string.search_more, count)
                    else -> pluralStringResource(R.plurals.search_lines, count, count)
                }
                TextMMD(text = said, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            // Stale rows stay up while the next search runs rather than blanking, which on
            // this panel would be two repaints where one will do.
            itemsIndexed(looking.hits, key = { _, hit -> hit.sheet.uri.toString() + "#" + hit.found.line }) { i, hit ->
                HitRow(hit, looking.searched.trim()) { act.open(hit.sheet, hit.found.at) }
                if (i < looking.hits.lastIndex) DottedRule(start = 0.dp, end = 0.dp)
            }
        }
    }
}

@Composable
private fun HitRow(hit: Hit, query: String, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp),
    ) {
        TextMMD(
            text = marked(hit, query),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val where = hit.sheet.name
        // A name that does not say its own day gets the day it was written, so every row says when.
        val day = if (dayInName(where) == null && hit.sheet.modified > 0L) {
            longDay(java.time.Instant.ofEpochMilli(hit.sheet.modified).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        } else null
        TextMMD(
            text = if (day != null) "$where · $day" else where,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The line around the match, the match in bold and underlined: two marks that need no grey to read. */
private fun marked(hit: Hit, query: String): AnnotatedString {
    val inLine = hit.found.at - hit.found.lineStart
    val (shown, at) = around(hit.found.text, inLine)
    val end = (at + query.length).coerceAtMost(shown.length)
    return buildAnnotatedString {
        append(shown)
        if (at in 0 until end) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline), at, end)
        }
    }
}
