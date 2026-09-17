package com.wanderwildwood.dajiki.ui

import android.view.KeyCharacterMap
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.dajiki.write.Opened
import com.wanderwildwood.dajiki.write.Size
import com.wanderwildwood.dajiki.write.countWords
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
    showWordCount: Boolean,
    onEdited: (String) -> Unit,
    onSaveNow: () -> Unit,
    onNew: () -> Unit,
    onFiles: () -> Unit,
    onRename: (String) -> Unit,
) {
    /*
     * The text lives in a TextFieldState rather than in a value this screen hands back and
     * forth, and that is what brings undo with it: the state keeps its own edit history and
     * the field maps Ctrl-Z and Ctrl-Shift-Z onto it. A writing app that cannot take back the
     * last thing you typed is wrong, and an undo stack written by hand here would be a worse
     * copy of the one already in the library.
     *
     * Keyed on the opened sheet, so opening another one starts a fresh field with a fresh
     * history rather than carrying the last sheet's cursor, or its undo, into it. The cursor
     * starts at the end, which is where the constructor puts it: a sheet is almost always
     * opened to carry on, and the alternative is a reader who types their next sentence into
     * the top of the last one.
     */
    val field = remember(opened) { TextFieldState(opened.text) }

    // Every change out to the view model, which does its own waiting before it writes. A
    // change that leaves the text as it was is dropped there rather than here.
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collectLatest(onEdited)
    }

    /**
     * The count trails the typing by a moment on purpose. Recomputing it per keystroke is
     * cheap, but *showing* it per keystroke is not: the foot of the screen would repaint on
     * every character, and a repaint on this panel is a visible flash. Keyed on the text, so
     * each keystroke restarts the wait and the count lands once the writer pauses.
     */
    var words by remember(opened) { mutableIntStateOf(countWords(opened.text)) }
    // Keyed on the setting as well, so that turning the count off also stops the counting.
    LaunchedEffect(field, showWordCount) {
        if (!showWordCount) return@LaunchedEffect
        snapshotFlow { field.text.toString() }.collectLatest { text ->
            delay(WORD_COUNT_PAUSE)
            words = countWords(text)
        }
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
            state = field,
            lineLimits = TextFieldLineLimits.MultiLine(),
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
            words = words.takeIf { showWordCount },
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
    // Ctrl, and NOT Alt. On a good many layouts AltGr arrives as Ctrl+Alt together, and
    // without the second half of this test the shortcuts eat the reader's own alphabet: on
    // the Polish programmer's layout AltGr+S is ś, AltGr+N is ń and AltGr+O is ó, which is
    // all three of these letters. A Polish writer would have pressed for an accent and been
    // sent to the folder instead - and Polish is the first language in the store's own list.
    // Anything held with Alt belongs to the text field, whatever else is held with it.
    if (!event.isCtrlPressed || event.isAltPressed) return false
    return when (printed(event)) {
        's' -> { onSaveNow(); true }
        'n' -> { onNew(); true }
        'o' -> { onFiles(); true }
        else -> false
    }
}

/**
 * The letter printed on the key, under whatever layout the reader is using.
 *
 * Matching the key *code* would bind these shortcuts to positions on a US keyboard rather
 * than to letters. That happens to survive AZERTY, where S, N and O sit where they do on
 * QWERTY, and it does not survive Dvorak, where Ctrl-S would fall on whichever key happens
 * to occupy the QWERTY S position and the key marked S would do nothing at all.
 *
 * Asked with no modifiers, so that the answer is the letter on the keycap rather than
 * whatever Shift or anything else held at the time would have produced. A key that prints
 * nothing, and a dead key - which comes back with the combining flag set rather than as a
 * character - are both "not a letter", which is the right answer for a shortcut.
 */
private fun printed(event: androidx.compose.ui.input.key.KeyEvent): Char? {
    val code = event.nativeKeyEvent.getUnicodeChar(0)
    if (code == 0 || (code and KeyCharacterMap.COMBINING_ACCENT) != 0) return null
    return code.toChar().lowercaseChar()
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
    words: Int?,
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
                if (words != null) {
                    append("· ")
                    append(if (words == 1) "1 word" else "$words words")
                }
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
