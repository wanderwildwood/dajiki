package com.wanderwildwood.dajiki.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.dajiki.R
import com.wanderwildwood.dajiki.write.PageState
import com.wanderwildwood.dajiki.write.Size
import com.wanderwildwood.dajiki.write.next
import com.wanderwildwood.dajiki.write.Turn

/**
 * What there is to set.
 *
 * Each row says what it is set to, and a press moves it on to the next value. These do not
 * want a screen each, and a picker is two full repaints to choose between three things that
 * already fit on the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: PageState,
    onClose: () -> Unit,
    onChooseFolder: () -> Unit,
    onTurn: (Turn) -> Unit,
    onSize: (Size) -> Unit,
    onSizePerSheet: () -> Unit,
    onWordCount: () -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }
    var noLayoutPage by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.settings_cd_close), onClose) },
                actions = { BarButton(Icons.Info, stringResource(R.string.settings_cd_about), { aboutOpen = true }) },
            )
        },
    ) { contentPadding ->
        // MMD's list rather than a plain Column: four rows fit the panel held across only
        // while nothing grows. A reader who has turned the system font size up wraps the note
        // under "Count the words" onto a second line, and that one line was enough to push
        // "Text size" off the bottom edge, where it could be neither seen nor pressed. A
        // list carries whatever the rows come to, and brings the chevron rail that says so.
        LazyColumnMMD(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        ) {
            item {
                Setting(
                    title = stringResource(R.string.settings_folder),
                    value = state.folder?.let(::folderName) ?: stringResource(R.string.settings_folder_none),
                    onClick = onChooseFolder,
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.settings_turn),
                    value = when (state.turn) {
                        Turn.ACROSS -> stringResource(R.string.settings_turn_across)
                        Turn.AROUND -> stringResource(R.string.settings_turn_around)
                        Turn.DEVICE -> stringResource(R.string.settings_turn_device)
                    },
                    onClick = { onTurn(next(state.turn)) },
                )
            }
            item {
                Toggle(
                    title = stringResource(R.string.settings_word_count),
                    // The one setting whose reason a label cannot carry, so it gets the
                    // second line the house style reserves for exactly that: the count is
                    // runs of non-whitespace, and that is not what a word is in every
                    // language.
                    note = stringResource(R.string.settings_word_count_note),
                    checked = state.wordCount,
                    onClick = onWordCount,
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.settings_text_size),
                    value = when (state.size) {
                        Size.SMALL -> stringResource(R.string.text_size_small)
                        Size.MEDIUM -> stringResource(R.string.text_size_medium)
                        Size.LARGE -> stringResource(R.string.text_size_large)
                    },
                    onClick = { onSize(next(state.size)) },
                )
            }
            item {
                Toggle(
                    title = stringResource(R.string.settings_size_per_sheet),
                    // The other setting whose cost a label cannot carry. What it does is
                    // plain; what it is held against is not. A size belongs to the sheet's
                    // address, so a rename here carries it and a sheet renamed by another
                    // app comes back at the size above.
                    note = stringResource(R.string.settings_size_per_sheet_note),
                    checked = state.sizePerSheet,
                    onClick = onSizePerSheet,
                )
            }
            item {
                // Kompakt OS reads every Bluetooth keyboard as US QWERTY and its own settings
                // have no way to say otherwise, so the keys printed å, ä and ö type [, ' and ;.
                // The layouts are on the phone -- Nordic, German, Swiss and the rest -- behind
                // Android's own Physical keyboard page, which nothing on the Kompakt links to.
                // This is that link. The letters are the platform's to get right, not ours.
                Setting(
                    title = stringResource(R.string.settings_keyboard_layout),
                    value = stringResource(
                        if (noLayoutPage) R.string.settings_keyboard_layout_missing
                        else R.string.settings_keyboard_layout_note,
                    ),
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            noLayoutPage = true
                        }
                    },
                )
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
        TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
    }
}
