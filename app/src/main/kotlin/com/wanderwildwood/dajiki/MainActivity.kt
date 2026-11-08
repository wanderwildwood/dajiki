package com.wanderwildwood.dajiki

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.wanderwildwood.dajiki.ui.FolderActions
import com.wanderwildwood.dajiki.ui.LockScreen
import com.wanderwildwood.dajiki.write.LockScope
import com.wanderwildwood.dajiki.write.Unlock
import com.wanderwildwood.dajiki.write.isDiaryPage
import com.wanderwildwood.dajiki.write.Turn
import com.wanderwildwood.dajiki.ui.PageScreen
import com.wanderwildwood.dajiki.ui.SettingsScreen
import com.wanderwildwood.dajiki.ui.houseTypography
import com.wanderwildwood.dajiki.ui.monochrome
import com.wanderwildwood.dajiki.write.PageViewModel
import com.wanderwildwood.dajiki.ui.ArmedScope

class MainActivity : ComponentActivity() {
    private val turner = TurnWithPhone(this)
    private var turning = false

    /**
     * Hold the screen the way the reader chose. Three of the four are an orientation asked for
     * by name, which every panel honours with or without a sensor. "Turns with the phone"
     * watches the accelerometer itself, starting from the way the screen is now so that a
     * phone lying on a desk stays as it was.
     */
    fun turn(turn: Turn) {
        if (turn == Turn.DEVICE) {
            TurnWithPhone.shown = when {
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE -> TurnWithPhone.PORTRAIT
                requestedOrientation == TurnWithPhone.REVERSE_LANDSCAPE -> TurnWithPhone.REVERSE_LANDSCAPE
                else -> TurnWithPhone.LANDSCAPE
            }
            turner.set(on = true)
            turning = true
        } else {
            // Stopped by holding the screen where it is rather than by switching off, which
            // asks for portrait on the way and would turn the panel twice.
            if (turning) turner.set(on = true, held = true)
            turning = false
            requestedOrientation = turn.activityInfo
        }
    }

    /** The way the screen was held before [uprightForLock] turned it, or null when it has not. */
    private var beforeLock: Int? = null

    /**
     * Turn the screen upright while Android asks for the phone's lock. Held sideways, the
     * phone's keyboard takes the whole screen over Android's PIN box (landscape "extract" mode):
     * no title, no box to tap out of, only Back, which is easy to miss on a Kompakt (found
     * 8 Nov). Upright, the keyboard sits below the title and the PIN box,
     * and a tap above them cancels, as anywhere else on the phone.
     */
    fun uprightForLock() {
        if (beforeLock == null) beforeLock = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    /** Put the screen back the way it was held before [uprightForLock]. */
    fun backFromLock() {
        beforeLock?.let { requestedOrientation = it }
        beforeLock = null
    }

    override fun onResume() {
        super.onResume()
        turner.resume()
    }

    override fun onPause() {
        turner.pause()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A steady cursor: a blinking one is a repaint twice a second on an e-ink panel
            // for as long as a field has focus.
            CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                ThemeMMD(colorScheme = monochrome, typography = houseTypography) {
                    ArmedScope { Typewriter(this) }
                }
            }
        }
    }
}

@Composable
private fun Typewriter(
    activity: MainActivity,
    viewModel: PageViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val looking by viewModel.looking.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    /*
     * Which way round the screen is held. The manifest already declares landscape, so this
     * only has work to do when the reader has chosen one of the other two — but it is set on
     * every run rather than only on a change, because the manifest's value is what the
     * activity comes back with after the system has restarted it.
     */
    LaunchedEffect(state.turn) {
        activity.turn(state.turn)
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
                Lifecycle.Event.ON_STOP -> {
                    viewModel.saveNow()
                    viewModel.stopped()
                }
                // Coming back is when a sync has most likely been and gone. If the open
                // sheet moved on while the app was away and nothing here is unsaved, this
                // shows what arrived rather than letting the next keystroke write over it.
                Lifecycle.Event.ON_START -> {
                    viewModel.started()
                    viewModel.resumed()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    val choose = rememberLauncherForActivityResult(
        FilesOpenTree(),
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

    /*
     * The lock. With the whole app locked, nothing shows until the phone's lock is given; with
     * the diary locked, only a day's page left open when the app came back after a while.
     */
    val opened = state.opened
    val covered = !state.unlocked && when (state.lock) {
        LockScope.OFF -> false
        LockScope.APP -> true
        LockScope.DIARY -> opened != null && !settingsOpen && isDiaryPage(opened.sheet)
    }
    val lockTitle = stringResource(if (state.lock == LockScope.APP) R.string.lock_title_app else R.string.lock_title_diary)
    /*
     * Asked through the phone's own "confirm your PIN" screen (pattern or password, as the
     * phone is set), which needs no permission: the app still asks for none. It is the older of
     * Android's two ways in; the newer, BiometricPrompt, would add USE_BIOMETRIC to a manifest
     * that is otherwise empty, for a fingerprint the Kompakt does not have. No second code to
     * remember or lose, and nothing of it is kept here. A phone with no screen lock has nothing
     * to ask for, and Android says so by handing back no screen at all.
     */
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activity.backFromLock()
        viewModel.unlockAnswered(if (result.resultCode == Activity.RESULT_OK) Unlock.DONE else Unlock.CANCELLED)
    }
    LaunchedEffect(state.asking) {
        if (!state.asking) return@LaunchedEffect
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val ask = keyguard?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent(lockTitle, null)
        if (ask == null) {
            viewModel.unlockAnswered(Unlock.NO_SCREEN_LOCK)
        } else {
            activity.uprightForLock()
            runCatching { confirm.launch(ask) }.onFailure {
                activity.backFromLock()
                viewModel.unlockAnswered(Unlock.CANCELLED)
            }
        }
    }

    BackHandler(enabled = settingsOpen || state.opened != null) {
        if (settingsOpen) settingsOpen = false else viewModel.close()
    }

    when {
        covered -> LockScreen(
            app = state.lock == LockScope.APP,
            onUnlock = viewModel::askUnlock,
            // Away from a locked page to the sheets, which the diary lock leaves open.
            onBack = if (state.lock == LockScope.DIARY) viewModel::close else null,
        )


        settingsOpen -> SettingsScreen(
            state = state,
            onClose = { settingsOpen = false },
            onChooseFolder = { choose.launch(state.folder) },
            onTurn = viewModel::setTurn,
            onOpenToToday = viewModel::toggleOpenToToday,
            onSize = viewModel::setSize,
            onSizePerSheet = viewModel::toggleSizePerSheet,
            onWordCount = viewModel::toggleWordCount,
            onFormat = viewModel::setFormat,
            onDiaryFolder = viewModel::toggleDiaryFolder,
            onLock = viewModel::setLock,
        )

        opened != null -> PageScreen(
            opened = opened,
            size = state.size,
            unsaved = state.unsaved,
            showWordCount = state.wordCount,
            onEdited = viewModel::edited,
            onSaveNow = viewModel::saveNow,
            onNew = viewModel::newSheet,
            onToday = viewModel::openToday,
            onFiles = viewModel::close,
            onRename = viewModel::renameOpen,
            onSize = viewModel::setSizeHere,
        )

        else -> FilesScreen(
            state = state,
            looking = looking,
            act = FolderActions(
                chooseFolder = { choose.launch(state.folder) },
                open = viewModel::open,
                newSheet = viewModel::newSheet,
                today = viewModel::openToday,
                delete = viewModel::deleteSheet,
                settings = { settingsOpen = true },
                show = viewModel::show,
                stepMonth = viewModel::stepMonth,
                pickDay = viewModel::pickDay,
                thisMonth = viewModel::thisMonth,
                search = { viewModel.search(it) },
            ),
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
        ) { TextMMD(text = stringResource(R.string.trouble_close), style = MaterialTheme.typography.bodySmall) }
    }
}
