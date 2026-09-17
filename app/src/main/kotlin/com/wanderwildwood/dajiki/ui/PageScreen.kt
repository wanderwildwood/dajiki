package com.wanderwildwood.dajiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.dajiki.write.Opened
import com.wanderwildwood.dajiki.write.Size
import com.wanderwildwood.dajiki.write.countWords
import kotlinx.coroutines.delay

/**
 * The page. Everything else in the app exists to get here and then get out of the way.
 *
 * **There is no top bar**, which is the one place this app departs from the house style, and
 * the reason is the shape of the screen it is written for. Held across, the panel is about
 * 320dp tall; a 64dp bar is a fifth of it, and a fifth of a writing surface spent saying the
 * name of the app the reader just opened is the wrong trade on the one screen where the
 * content *is* the app. The single line at the foot carries the way back and the count, and
 * the cog lives on the folder screen, which has a bar to hang it from.
 *
 * The text field holds the text itself rather than reading it back out of the view model. A
 * keystroke has to land synchronously: put a flow between the key and the glyph and a fast
 * typist on a real keyboard loses characters, which on a writing app is the only unforgivable
 * bug there is.
 */
@Composable
fun PageScreen(
    opened: Opened,
    size: Size,
    unsaved: Boolean,
    onEdited: (String) -> Unit,
    onSaveNow: () -> Unit,
    onNew: () -> Unit,
    onFiles: () -> Unit,
    onRename: (String) -> Unit,
) {
    // Keyed on the opened sheet, so opening another one starts a fresh field rather than
    // carrying the last one's cursor into it.
    var value by remember(opened) {
        mutableStateOf(
            // The cursor starts at the end: a sheet is almost always opened to carry on,
            // and the alternative is a reader who types their next sentence into the top
            // of the last one.
            TextFieldValue(opened.text, TextRange(opened.text.length)),
        )
    }

    /**
     * The count trails the typing by a moment on purpose. Recomputing it per keystroke is
     * cheap, but *showing* it per keystroke is not: the foot of the screen would repaint on
     * every character, and a repaint on this panel is a visible flash. Keyed on the text, so
     * each keystroke restarts the wait and the count lands once the writer pauses.
     */
    var words by remember(opened) { mutableIntStateOf(countWords(opened.text)) }
    LaunchedEffect(value.text) {
        delay(WORD_COUNT_PAUSE)
        words = countWords(value.text)
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(opened) { focus.requestFocus() }

    var renaming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // No keyboard inset is applied here: the manifest asks for adjustResize, so
            // the window has already shrunk by the time a soft keyboard is up, and
            // padding it again would leave a second gap the same size. This app expects
            // a real keyboard anyway, and then there is no inset at all.
            .background(MaterialTheme.colorScheme.surface),
    ) {
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onEdited(it.text)
            },
            textStyle = when (size) {
                Size.SMALL -> MaterialTheme.typography.bodySmall
                Size.MEDIUM -> MaterialTheme.typography.bodyMedium
                Size.LARGE -> MaterialTheme.typography.bodyLarge
            }.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { event -> shortcut(event, onSaveNow, onNew, onFiles) },
        )

        HorizontalDividerMMD()
        Foot(
            name = opened.sheet.name,
            words = words,
            unsaved = unsaved,
            onFiles = onFiles,
            onRename = { renaming = true },
        )
    }

    if (renaming) {
        RenameDialog(
            name = opened.sheet.name,
            onRename = onRename,
            onDismiss = { renaming = false },
        )
    }
}

/**
 * The keyboard shortcuts, which are the point of a writing app on a device with a real
 * keyboard: a writer who has both hands on the keys should not have to reach for the screen
 * to save, start a page or go back to the folder.
 *
 * Ctrl-S, Ctrl-N and Ctrl-O because those are what a writer's fingers already do, and Escape
 * because a keyboard case has one and nothing else here uses it. Anything not claimed here
 * falls through untouched, which is what leaves the text field's own Home, End, page keys,
 * shift-selection and word-wise arrows working — none of that is reimplemented, because the
 * platform's text field already does all of it correctly the moment a hardware key reaches it.
 */
private fun shortcut(
    event: androidx.compose.ui.input.key.KeyEvent,
    onSaveNow: () -> Unit,
    onNew: () -> Unit,
    onFiles: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) {
        onFiles()
        return true
    }
    if (!event.isCtrlPressed) return false
    return when (event.key) {
        Key.S -> { onSaveNow(); true }
        Key.N -> { onNew(); true }
        Key.O -> { onFiles(); true }
        else -> false
    }
}

/**
 * One line at the foot: the way back on the left, what the sheet is called and how long it is
 * on the right.
 *
 * The word "Files" is there rather than a bare chevron because this is the only door out of
 * the page, and a door nobody finds is a door that is not there.
 */
@Composable
private fun Foot(
    name: String,
    words: Int,
    unsaved: Boolean,
    onFiles: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onFiles)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Back,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = "Files", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.weight(1f))

        // The name is the way in to renaming it. A sheet is named for the minute it was
        // started, which is no name at all by the third one, and the place a reader looks
        // for what a thing is called is the place it is written.
        TextMMD(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clickable(onClick = onRename)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        )
        TextMMD(
            text = buildString {
                append("· ")
                append(if (words == 1) "1 word" else "$words words")
                // Said in words rather than shown as a dot. A dot on a panel with sixteen
                // greys is a speck the reader has to learn the meaning of, and this is the
                // one thing on the screen they might need to act on.
                if (unsaved) append(" · not saved yet")
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
        )
    }
}

private const val WORD_COUNT_PAUSE = 400L
