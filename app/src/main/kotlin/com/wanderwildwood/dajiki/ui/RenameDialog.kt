package com.wanderwildwood.dajiki.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * Give the sheet a name.
 *
 * A dialog, which this shop otherwise avoids, because there is nowhere else to put a name
 * being typed: the row cannot ask this one, and the page is the thing being named.
 *
 * The field is **prefilled** with the current name rather than showing it as a placeholder.
 * A placeholder is drawn only while a field is empty *and* focused, so on an unfocused empty
 * box it renders nothing at all and reads as "this sheet has no name" — which is how a form
 * ends up lying about its own contents.
 *
 * The extension is left off what is offered and put back by the folder if the reader does not
 * type one, so renaming a sheet is typing a name rather than remembering to keep `.txt` on the
 * end of it.
 */
@Composable
fun RenameDialog(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    val base = remember(name) { name.substringBeforeLast('.', name) }
    var value by remember(name) {
        mutableStateOf(TextFieldValue(base, TextRange(base.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val commit = {
        onRename(value.text)
        onDismiss()
    }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = "Name this sheet",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(14.dp))
        // The keyboard's own key finishes this, not just the button.
        //
        // Held across, an on-screen keyboard takes most of a 480 tall panel, and it pushed
        // the Cancel and Rename buttons up behind itself: the dialog was unfinishable
        // without first dismissing the keyboard, which nothing on the screen said to do.
        // A writerdeck has a real keyboard and never sees this, but the phone it is a fork
        // of does, every time. Done also means Enter finishes it on a hardware keyboard,
        // which is what a typist's hands will do anyway.
        TextFieldMMD(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = "Cancel", style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.width(10.dp))

            OutlinedButtonMMD(
                onClick = commit,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = "Rename", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
