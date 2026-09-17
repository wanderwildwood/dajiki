package com.wanderwildwood.dajiki.write

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A sheet and what was in it when it opened, handed over together so the page can key on it. */
data class Opened(val sheet: Sheet, val text: String)

data class PageState(
    val folder: Uri? = null,
    val sheets: List<Sheet> = emptyList(),
    val opened: Opened? = null,
    val unsaved: Boolean = false,
    val turn: Turn = Turn.ACROSS,
    val size: Size = Size.MEDIUM,
    /** Something that went wrong, in words, shown until it is read and dismissed. */
    val trouble: String? = null,
)

class PageViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = Preferences(application)
    private val folder = Folder(application)

    private val _state = MutableStateFlow(
        PageState(
            folder = preferences.folder,
            turn = preferences.turn,
            size = preferences.size,
        ),
    )
    val state: StateFlow<PageState> = _state.asStateFlow()

    /**
     * What is on the page right now.
     *
     * A plain field rather than state, and the page does not read it back. The text the reader
     * is typing into lives in the text field itself, where a keystroke lands synchronously;
     * routing every character out to a flow and back would put an emission between the key and
     * the glyph, which on a fast typist with a real keyboard is how characters get lost.
     */
    private var pending: String = ""

    private var saving: Job? = null

    init {
        preferences.folder?.let { list(it) }
    }

    /**
     * The folder the reader picked.
     *
     * [remembered] is whether the activity managed to take a lasting grant on it. A grant that
     * only lasts this run still works for now and is gone by morning, and a reader who is not
     * told that finds out by opening the app to an empty folder.
     */
    fun folderChosen(uri: Uri, remembered: Boolean) {
        preferences.folder = uri
        preferences.lastOpen = null
        _state.update {
            it.copy(
                folder = uri,
                opened = null,
                sheets = emptyList(),
                trouble = if (remembered) {
                    it.trouble
                } else {
                    "That folder opened, but the phone would not let it be remembered. It " +
                        "will need choosing again next time."
                },
            )
        }
        list(uri)
    }

    private fun list(uri: Uri) {
        viewModelScope.launch { listNow(uri) }
    }

    private suspend fun listNow(uri: Uri) {
        run {
            val sheets = runCatching { withContext(Dispatchers.IO) { folder.list(uri) } }
            sheets.onSuccess { found ->
                _state.update { it.copy(sheets = found) }
                // Back to whatever was being written when the app was last put down.
                val last = preferences.lastOpen
                if (last != null && state.value.opened == null) {
                    found.firstOrNull { it.uri == last }?.let(::open)
                }
            }.onFailure { reason ->
                // The grant can be gone: the folder may have been deleted, or its provider
                // uninstalled, or the reader may have cleared the app's data. Say which,
                // rather than showing an empty list that looks like an empty folder.
                say("That folder could not be read. Choose it again in Settings.", reason)
            }
        }
    }

    fun open(sheet: Sheet) {
        saveNow()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { folder.read(sheet.uri) } }
                .onSuccess { text ->
                    pending = text
                    preferences.lastOpen = sheet.uri
                    _state.update { it.copy(opened = Opened(sheet, text), unsaved = false) }
                }
                .onFailure { say("${sheet.name} could not be opened.", it) }
        }
    }

    fun newSheet() {
        val where = state.value.folder ?: return
        saveNow()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { folder.create(where) } }
                .onSuccess { sheet ->
                    pending = ""
                    preferences.lastOpen = sheet.uri
                    _state.update {
                        it.copy(
                            opened = Opened(sheet, ""),
                            sheets = listOf(sheet) + it.sheets,
                            unsaved = false,
                        )
                    }
                }
                .onFailure { say("A new sheet could not be started in that folder.", it) }
        }
    }

    /**
     * Close the sheet and go back to the folder, having written it out first.
     *
     * The sheet and the text are taken now, before anything is cleared. They used to be read
     * inside the save, which ran a moment later against a state whose open sheet this method
     * had already set to null — so closing a page with unsaved words in it wrote nothing at
     * all, silently, which is the worst thing this app could do.
     *
     * The folder is listed again afterwards rather than alongside, so the row for the sheet
     * just closed shows when it was actually written rather than when it was last opened.
     */
    fun close() {
        val sheet = state.value.opened?.sheet
        val text = pending
        val dirty = state.value.unsaved

        saving?.cancel()
        saving = null
        preferences.lastOpen = null
        _state.update { it.copy(opened = null) }

        viewModelScope.launch {
            if (dirty && sheet != null) write(sheet, text)
            state.value.folder?.let { listNow(it) }
        }
    }

    /**
     * A keystroke. Restarts the wait, so a sheet is written once the typing stops rather than
     * once per character — a save is cheap, but a save per keystroke on a folder a sync app is
     * watching is not.
     */
    fun edited(text: String) {
        pending = text
        if (!state.value.unsaved) _state.update { it.copy(unsaved = true) }
        saving?.cancel()
        saving = viewModelScope.launch {
            delay(PAUSE)
            val sheet = state.value.opened?.sheet ?: return@launch
            write(sheet, pending)
        }
    }

    /**
     * Write now, whatever the wait was doing. Called on the way out of the app and the sheet.
     *
     * What is being written is settled here rather than inside the coroutine. A save that
     * looks up its own subject when it finally runs is a save that can find the subject gone.
     */
    fun saveNow() {
        saving?.cancel()
        saving = null
        if (!state.value.unsaved) return
        val sheet = state.value.opened?.sheet ?: return
        val text = pending
        viewModelScope.launch { write(sheet, text) }
    }

    private suspend fun write(sheet: Sheet, text: String) {
        runCatching { withContext(Dispatchers.IO) { folder.write(sheet.uri, text) } }
            .onSuccess {
                // Only if nothing was typed while the write was in flight. Clearing the mark
                // on a page that has moved on since would tell the reader their newest
                // sentence is on disk when it is not.
                if (pending == text) _state.update { it.copy(unsaved = false) }
            }
            .onFailure { say("${sheet.name} could not be saved. What you typed is still here.", it) }
    }

    fun setTurn(turn: Turn) {
        preferences.turn = turn
        _state.update { it.copy(turn = turn) }
    }

    fun setSize(size: Size) {
        preferences.size = size
        _state.update { it.copy(size = size) }
    }

    fun troubleRead() = _state.update { it.copy(trouble = null) }

    /**
     * Put something wrong in front of the reader.
     *
     * Every failure in this class comes through here. A writing app that loses a save and says
     * nothing is worse than one that will not start, because the reader finds out later, from
     * the gap where their afternoon was.
     */
    private fun say(words: String, reason: Throwable) {
        _state.update { it.copy(trouble = words) }
        reason.printStackTrace()
    }

    private companion object {
        const val PAUSE = 2_000L
    }
}
