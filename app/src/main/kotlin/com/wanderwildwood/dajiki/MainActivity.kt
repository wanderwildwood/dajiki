package com.wanderwildwood.dajiki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.dajiki.ui.EInkDialog
import com.wanderwildwood.dajiki.ui.FilesScreen
import com.wanderwildwood.dajiki.ui.PageScreen
import com.wanderwildwood.dajiki.ui.SettingsScreen
import com.wanderwildwood.dajiki.ui.monochrome
import com.wanderwildwood.dajiki.write.PageViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                Typewriter(this)
            }
        }
    }
}

@Composable
private fun Typewriter(
    activity: ComponentActivity,
    viewModel: PageViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }

    /*
     * Which way round the screen is held. The manifest already declares landscape, so this
     * only has work to do when the reader has chosen one of the other two — but it is set on
     * every run rather than only on a change, because the manifest's value is what the
     * activity comes back with after the system has restarted it.
     */
    LaunchedEffect(state.turn) {
        activity.requestedOrientation = state.turn.activityInfo
    }

    /*
     * Write the page out when the app stops, and look for a newer copy when it starts.
     *
     * The pause between keystrokes catches almost every save; this catches the rest — the
     * reader who types a sentence and immediately presses home, and the app that is killed
     * for memory a moment later.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val watcher = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.saveNow()
                // Coming back is when a sync has most likely been and gone. If the open
                // sheet moved on while the app was away and nothing here is unsaved, this
                // shows what arrived rather than letting the next keystroke write over it.
                Lifecycle.Event.ON_START -> viewModel.resumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    val choose = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { picked ->
        if (picked != null) {
            // Without this the grant lasts only until the app is killed. It is allowed to
            // fail: the folder still works for this run, and the view model says so.
            val remembered = runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.isSuccess
            viewModel.folderChosen(picked, remembered)
            settingsOpen = false
        }
    }

    BackHandler(enabled = settingsOpen || state.opened != null) {
        if (settingsOpen) settingsOpen = false else viewModel.close()
    }

    val opened = state.opened
    when {
        settingsOpen -> SettingsScreen(
            state = state,
            onClose = { settingsOpen = false },
            onChooseFolder = { choose.launch(state.folder) },
            onTurn = viewModel::setTurn,
            onSize = viewModel::setSize,
        )

        opened != null -> PageScreen(
            opened = opened,
            size = state.size,
            unsaved = state.unsaved,
            onEdited = viewModel::edited,
            onSaveNow = viewModel::saveNow,
            onNew = viewModel::newSheet,
            onFiles = viewModel::close,
        )

        else -> FilesScreen(
            state = state,
            onChooseFolder = { choose.launch(state.folder) },
            onOpen = viewModel::open,
            onNew = viewModel::newSheet,
            onSettings = { settingsOpen = true },
        )
    }

    state.trouble?.let { words ->
        Trouble(words, viewModel::troubleRead)
    }
}

/**
 * Something that went wrong, in front of the reader until they have read it.
 *
 * A dialog, which this shop otherwise avoids, because this is the one thing on the screen
 * that is not reversible by looking away: a save that failed is an afternoon's work that is
 * not where the writer thinks it is, and a line at the foot of a page they are typing into is
 * a line they will not see.
 */
@Composable
private fun Trouble(words: String, onRead: () -> Unit) {
    EInkDialog(onDismiss = onRead) {
        TextMMD(text = words, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(
            onClick = onRead,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = "Close", style = MaterialTheme.typography.bodySmall) }
    }
}
