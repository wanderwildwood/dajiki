package com.wanderwildwood.dajiki.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.dajiki.R
import com.wanderwildwood.dajiki.write.PageState
import com.wanderwildwood.dajiki.write.Reading
import com.wanderwildwood.dajiki.write.Sheet

/**
 * The folder: what is in it, and a way to start something new.
 *
 * This screen carries the bar, and so it carries the cog. The page has neither, which is the
 * right way round — the folder is where you are between pieces of work, and the page is the
 * work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: PageState,
    onChooseFolder: () -> Unit,
    onOpen: (Sheet) -> Unit,
    onNew: () -> Unit,
    onDelete: (Sheet) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = { BarButton(Icons.Settings, stringResource(R.string.files_cd_settings), onSettings) },
            )
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            if (state.folder == null) {
                NoFolder(onChooseFolder)
            } else {
                Sheets(state.sheets, state.reading, onOpen, onNew, onDelete)
            }
        }
    }
}

/**
 * Before there is anywhere to write.
 *
 * It says where the writing will live, because that is the one thing about this app a reader
 * cannot guess: most note apps keep their notes inside themselves, and this one does not.
 */
@Composable
private fun NoFolder(onChooseFolder: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        TextMMD(
            text = stringResource(R.string.files_no_folder_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        TextMMD(
            text = stringResource(R.string.files_no_folder_body),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButtonMMD(
            onClick = onChooseFolder,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.files_choose_folder), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Sheets(
    sheets: List<Sheet>,
    reading: Reading,
    onOpen: (Sheet) -> Unit,
    onNew: () -> Unit,
    onDelete: (Sheet) -> Unit,
) {
    LazyColumnMMD(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNew)
                    .padding(vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                TextMMD(text = stringResource(R.string.files_new_sheet), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDividerMMD()
        }

        if (sheets.isEmpty()) {
            item {
                // Three states, not two. A cold start can take the better part of ten
                // seconds to get the folder back, and "nothing here" during those seconds
                // tells a writer their work is gone. A word rather than a spinner: nothing
                // on this panel animates.
                TextMMD(
                    text = when (reading) {
                        Reading.NOT_YET -> stringResource(R.string.files_reading)
                        Reading.DONE -> stringResource(R.string.files_empty)
                        Reading.FAILED -> stringResource(R.string.files_read_failed)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }

        items(sheets, key = { it.uri.toString() }) { sheet ->
            SheetRow(sheet, onOpen = { onOpen(sheet) }, onDelete = { onDelete(sheet) })
        }
    }
}

/**
 * One sheet, and the only way to be rid of it.
 *
 * The row asks rather than a dialog: one repaint instead of two, and the question is put in
 * the place the answer belongs. It disarms itself after four seconds, so a stray tap leaves
 * nothing live for whoever picks the phone up next, and tapping the row while it is armed
 * puts it away rather than opening the sheet.
 */
@Composable
private fun SheetRow(sheet: Sheet, onOpen: () -> Unit, onDelete: () -> Unit) {
    var armed by remember(sheet.uri) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (armed) armed = false else onOpen() }
            .padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(
                text = if (armed) stringResource(R.string.files_delete_armed) else sheet.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextMMD(
                text = when_(
                    sheet.modified,
                    stringResource(R.string.files_not_dated),
                    stringResource(R.string.files_just_now),
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Icon(
            imageVector = Icons.Delete,
            contentDescription = if (armed) stringResource(R.string.files_delete_armed) else stringResource(R.string.files_cd_delete),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(24.dp)
                .clickable { if (armed) onDelete() else armed = true },
        )
    }
}

/**
 * When a sheet was last written to, in the reader's terms.
 *
 * A provider that keeps no modification time reports zero, and "56 years ago" is worse than
 * saying nothing, so that case says nothing.
 */
private fun when_(modified: Long, notDated: String, justNow: String): String {
    if (modified <= 0L) return notDated
    val ago = System.currentTimeMillis() - modified
    // Android's own phrasing for anything under a minute is "0 minutes ago", which is not
    // a thing anyone says, and it is the line the sheet you just closed will be showing.
    if (ago in 0 until DateUtils.MINUTE_IN_MILLIS) return justNow
    return DateUtils.getRelativeTimeSpanString(
        modified,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

@Composable
internal fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
