package com.wanderwildwood.dajiki.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.dajiki.write.PageState
import com.wanderwildwood.dajiki.write.Size
import com.wanderwildwood.dajiki.write.Turn

/**
 * The three things there are to set.
 *
 * Each row says what it is set to, and a press moves it on to the next value. Three settings
 * do not want a screen each, and a picker is two full repaints to choose between three things
 * that already fit on the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: PageState,
    onClose: () -> Unit,
    onChooseFolder: () -> Unit,
    onTurn: (Turn) -> Unit,
    onSize: (Size) -> Unit,
    onWordCount: () -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = "Settings") },
                navigationIcon = { BarButton(Icons.Close, "Close", onClose) },
                actions = { BarButton(Icons.Info, "About", { aboutOpen = true }) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            Setting(
                title = "Folder",
                value = state.folder?.let(::folderName) ?: "None chosen",
                onClick = onChooseFolder,
            )
            Setting(
                title = "Which way round",
                value = when (state.turn) {
                    Turn.ACROSS -> "Across"
                    Turn.AROUND -> "Across, turned around"
                    Turn.DEVICE -> "However the device is held"
                },
                onClick = { onTurn(next(state.turn)) },
            )
            Toggle(
                title = "Count the words",
                // The one setting whose reason a label cannot carry, so it gets the second
                // line the house style reserves for exactly that: the count is runs of
                // non-whitespace, and that is not what a word is in every language.
                note = "Counted as runs between spaces, so it is wrong for a language that " +
                    "does not use them.",
                checked = state.wordCount,
                onClick = onWordCount,
            )
            Setting(
                title = "Text size",
                value = when (state.size) {
                    Size.SMALL -> "Small"
                    Size.MEDIUM -> "Medium"
                    Size.LARGE -> "Large"
                },
                onClick = { onSize(next(state.size)) },
            )
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
}

private inline fun <reified T : Enum<T>> next(current: T): T {
    val all = enumValues<T>()
    return all[(current.ordinal + 1) % all.size]
}

/**
 * The folder, said the way the reader named it rather than the way the provider did.
 *
 * A tree address ends in the document id, which for the ordinary storage provider is
 * `primary:Documents/Writing`. The part after the colon is the path the reader would
 * recognise; anything else is shown whole rather than guessed at.
 */
private fun folderName(folder: Uri): String {
    val last = folder.lastPathSegment ?: return folder.toString()
    return last.substringAfter(':', last).ifBlank { last }
}

/**
 * A setting that is on or off. A switch rather than a value to cycle, because that is what
 * this shop's library draws for a boolean; `onCheckedChange = null` is the documented way to
 * let the row around it take the press.
 */
@Composable
private fun Toggle(title: String, note: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(16.dp))
        SwitchMMD(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Setting(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
        TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
    }
}
