package com.wanderwildwood.dajiki.write

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A sheet and what was in it when it opened, handed over together so the page can key on it. */
data class Opened(val sheet: Sheet, val text: String)

/**
 * How far along the folder is. Three states rather than two, because "no sheets yet" and
 * "not read yet" look identical on screen and only one of them is true — and on a cold start
 * the folder can take the better part of ten seconds to arrive, which is a long time to be
 * telling somebody their writing is not there.
 */
enum class Reading { NOT_YET, DONE, FAILED }

data class PageState(
    val folder: Uri? = null,
    val sheets: List<Sheet> = emptyList(),
    val reading: Reading = Reading.NOT_YET,
    val opened: Opened? = null,
    val unsaved: Boolean = false,
    val turn: Turn = Turn.ACROSS,
    val size: Size = Size.MEDIUM,
    val wordCount: Boolean = true,
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
            wordCount = preferences.wordCount,
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

    /**
     * What the sheet looked like from the outside when this app last read or wrote it, and
     * what it put there. Together they answer the only question that matters before a save:
     * is the file still the one we were editing?
     *
     * Private fields rather than state, for the same reason [pending] is. Putting them in
     * [Opened] would re-key the text field on every save and throw away the reader's cursor
     * mid-sentence.
     */
    private var seen: Stamp? = null
    private var written: String? = null

    /**
     * Set when a sheet moved on underneath us *and* the spare copy could not be written
     * either. It stops the pause-and-save loop from putting the same dialog up every two
     * seconds; opening any sheet, or a spare copy finally landing, clears it.
     */
    private var stalled = false

    /**
     * The pause between the last keystroke and the write. Cancelled freely — that is what
     * makes it a pause — and it holds nothing but the waiting.
     */
    private var pause: Job? = null

    /**
     * One writer at a time, and never half a writer.
     *
     * A write is two things: putting the bytes somewhere, and recording where they went. The
     * bytes go through a blocking call that cancellation cannot reach, so a save cancelled by
     * the next keystroke could finish its first half and skip its second. On the conflict
     * path that meant the spare copy was written to disk and then not switched to, and the
     * following save made a second spare copy of the same sheet. Every write takes this lock
     * and runs uncancellable inside it, so the halves cannot come apart and two writes cannot
     * overlap.
     */
    private val pen = Mutex()

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
                reading = Reading.NOT_YET,
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
                _state.update { it.copy(sheets = found, reading = Reading.DONE) }
                // Back to whatever was being written when the app was last put down.
                val last = preferences.lastOpen
                if (last != null && state.value.opened == null) {
                    found.firstOrNull { it.uri == last }?.let(::open)
                }
            }.onFailure { reason ->
                _state.update { it.copy(reading = Reading.FAILED) }
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
            runCatching {
                withContext(Dispatchers.IO) {
                    // Stamped before the read, never after. If the file changes in the gap
                    // between the two, this way round leaves an old stamp against new text,
                    // which costs a needless second copy later; the other way round leaves a
                    // new stamp against old text, and that overwrites somebody's work.
                    val before = folder.stamp(sheet.uri)
                    before to folder.read(sheet.uri)
                }
            }
                .onSuccess { (stamp, text) ->
                    pending = text
                    seen = stamp
                    written = text
                    stalled = false
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
                    seen = withContext(Dispatchers.IO) { folder.stamp(sheet.uri) }
                    written = ""
                    stalled = false
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

        pause?.cancel()
        pause = null
        preferences.lastOpen = null
        _state.update { it.copy(opened = null) }

        viewModelScope.launch {
            if (dirty && sheet != null) save(sheet, text)
            state.value.folder?.let { listNow(it) }
        }
    }

    /**
     * A keystroke. Restarts the wait, so a sheet is written once the typing stops rather than
     * once per character — a save is cheap, but a save per keystroke on a folder a sync app is
     * watching is not.
     *
     * The text field reports more than typing. Moving the cursor, selecting a word, and the
     * keyboard re-attaching when the app comes back to the front all arrive here with the
     * text unchanged. Treating those as edits marked a sheet unsaved that nobody had touched,
     * which cost a pointless write every time somebody moved the cursor — and worse, on the
     * way back into the app it set the unsaved mark just before the check for a newer copy
     * ran, so the check stood down and the next save forked a sheet that only needed
     * reloading. Nothing that leaves the text as it was is an edit.
     */
    fun edited(text: String) {
        if (text == pending) return
        pending = text
        if (!state.value.unsaved) _state.update { it.copy(unsaved = true) }
        pause?.cancel()
        if (stalled) return
        pause = viewModelScope.launch {
            delay(PAUSE)
            save()
        }
    }

    /** Write now, whatever the wait was doing. Called on the way out of the app and the sheet. */
    fun saveNow() {
        pause?.cancel()
        pause = null
        if (!state.value.unsaved) return
        viewModelScope.launch { save() }
    }

    /**
     * Write what is on the page into the sheet it belongs to, one at a time and to the end.
     *
     * The sheet and the text are read inside the lock rather than before it, except where a
     * caller names them. A save that queued behind another one has to act on where things
     * stand when its turn comes: the save ahead of it may have hit a conflict and moved the
     * page to a different sheet, and writing to the sheet that was open a moment ago is
     * exactly how the same conflict gets handled twice.
     */
    private suspend fun save(sheet: Sheet? = null, text: String? = null) = pen.withLock {
        withContext(NonCancellable) {
            val target = sheet ?: state.value.opened?.sheet ?: return@withContext
            write(target, text ?: pending)
        }
    }

    private sealed interface Saved {
        /** Written, and this is what the sheet looks like now. */
        data class Ok(val stamp: Stamp?) : Saved

        /** Not written: the sheet on disk is no longer the one we were editing. */
        data object MovedOn : Saved
    }

    /**
     * Write [text] into [sheet] — unless the sheet has changed underneath us, in which case
     * write nothing and keep what was typed beside it instead.
     *
     * This is the guard the app most needs. The folder is meant to be one a sync app owns,
     * so the ordinary case is a laptop editing the same sheet and the phone pulling a newer
     * copy down mid-sentence. Without the check, the next pause in typing quietly pastes the
     * screen over the newer file and the other machine's paragraphs are gone, with nothing
     * said and nothing to undo it from.
     *
     * What the sheet looked like is settled before going near the disk, so that the check
     * and the write are reasoning about the same moment.
     */
    private suspend fun write(sheet: Sheet, text: String) {
        val before = seen
        val onDisk = written

        runCatching {
            withContext(Dispatchers.IO) {
                if (movedOn(sheet.uri, before, onDisk)) {
                    Saved.MovedOn
                } else {
                    folder.write(sheet.uri, text)
                    Saved.Ok(folder.stamp(sheet.uri))
                }
            }
        }
            .onSuccess { outcome ->
                when (outcome) {
                    is Saved.Ok -> {
                        seen = outcome.stamp
                        written = text
                        // Only if nothing was typed while the write was in flight. Clearing
                        // the mark on a page that has moved on since would tell the reader
                        // their newest sentence is on disk when it is not.
                        if (pending == text) _state.update { it.copy(unsaved = false) }
                    }

                    Saved.MovedOn -> keepBeside(sheet, text)
                }
            }
            .onFailure { say("${sheet.name} could not be saved. What you typed is still here.", it) }
    }

    /**
     * Whether the sheet at [uri] is still the one this app was editing.
     *
     * Dates and lengths answer it where the provider gives them. Where they cannot — the same
     * length, and nothing dated — the contents are read and compared against what this app
     * put there, which is slower and certain. A sheet that cannot be read at all is treated
     * as changed: that is the answer that costs a spare copy, and the other one costs the
     * writing.
     */
    private fun movedOn(uri: Uri, before: Stamp?, onDisk: String?): Boolean {
        // Nothing to compare against: this app has not yet seen the sheet from the outside.
        if (before == null) return false
        val now = folder.stamp(uri) ?: return false
        changedBetween(before, now)?.let { return it }
        if (onDisk == null) return false
        return runCatching { folder.read(uri) }.getOrNull() != onDisk
    }

    /**
     * Put what was typed here into a sheet of its own, leaving the copy that arrived alone.
     *
     * Neither version is thrown away and the reader is not asked to choose between them at
     * the worst possible moment — on a 4.3" panel, mid-sentence, with no way to see what the
     * other copy even says. They get both files and can sort it out on a machine with a
     * screen. The page then follows the new sheet, or the very next keystroke would conflict
     * against the same file again and leave a trail of copies.
     */
    private suspend fun keepBeside(sheet: Sheet, text: String) {
        val where = state.value.folder ?: return

        runCatching {
            withContext(Dispatchers.IO) {
                val copy = folder.createBeside(where, besideName(sheet.name))
                folder.write(copy.uri, text)
                copy to folder.stamp(copy.uri)
            }
        }
            .onSuccess { (copy, stamp) ->
                seen = stamp
                written = text
                pending = text
                stalled = false

                // This also runs on the way out of a sheet, where the reader has just asked
                // for the folder. Following the copy is right when they are still on the
                // page and wrong when they are not: pressing Files and being dropped back
                // into a sheet they did not open would be its own small betrayal.
                val stillReading = state.value.opened != null
                if (stillReading) preferences.lastOpen = copy.uri

                _state.update {
                    it.copy(
                        opened = if (stillReading) Opened(copy, text) else null,
                        sheets = listOf(copy) + it.sheets,
                        unsaved = false,
                        trouble = buildString {
                            append(sheet.name)
                            append(" changed somewhere else while you were writing, so ")
                            append("nothing was written over it. What you typed is in ")
                            append(copy.name)
                            append(if (stillReading) ", which is the sheet you are in." else ".")
                        },
                    )
                }
            }
            .onFailure {
                // Stall the pause-and-save loop. Without this the next pause tries again,
                // fails again, and puts the same dialog up every two seconds while the
                // reader is trying to read the first one. Going back to the folder or
                // opening any sheet clears it.
                stalled = true
                say(
                    "${sheet.name} changed somewhere else, and a second copy could not be " +
                        "made either. Nothing has been written over. What you typed is " +
                        "still on this page — get it somewhere safe before leaving it.",
                    it,
                )
            }
    }

    /**
     * The app has come back to the front. If the open sheet moved on while it was away and
     * nothing here is unsaved, show what arrived.
     *
     * Only when there is nothing in flight. With unsaved words on the page this does nothing
     * and leaves it to the save, which will keep both copies rather than pick one.
     */
    fun resumed() {
        val sheet = state.value.opened?.sheet ?: return
        if (state.value.unsaved) return
        val before = seen ?: return
        val onDisk = written

        viewModelScope.launch {
            val fresh = runCatching {
                withContext(Dispatchers.IO) {
                    if (!movedOn(sheet.uri, before, onDisk)) {
                        null
                    } else {
                        // Stamped before the read, for the reason open() is.
                        folder.stamp(sheet.uri) to folder.read(sheet.uri)
                    }
                }
            }.getOrNull() ?: return@launch

            val (stamp, text) = fresh
            pending = text
            seen = stamp
            written = text
            _state.update { it.copy(opened = Opened(sheet, text), unsaved = false) }
        }
    }

    /**
     * Rename the open sheet.
     *
     * Written out first, because the rename moves the address the save would have gone to.
     * The provider hands back a new one, so the open sheet, the remembered sheet and the
     * stamp all have to follow it; leaving any of them on the old address is a save that
     * lands nowhere or a conflict against a file that no longer exists.
     */
    fun renameOpen(wanted: String) {
        val sheet = state.value.opened?.sheet ?: return
        val text = pending
        viewModelScope.launch {
            save(sheet, text)
            val moved = runCatching {
                withContext(Dispatchers.IO) { folder.rename(sheet.uri, wanted, sheet.name) }
            }.getOrElse { reason ->
                say("${sheet.name} could not be renamed.", reason); return@launch
            } ?: return@launch

            seen = withContext(Dispatchers.IO) { folder.stamp(moved.uri) }
            written = text
            preferences.lastOpen = moved.uri
            _state.update {
                it.copy(
                    opened = Opened(moved, text),
                    sheets = it.sheets.map { row -> if (row.uri == sheet.uri) moved else row },
                )
            }
        }
    }

    /**
     * Remove a sheet from the folder.
     *
     * Only ever from the folder screen, so nothing here is open. The remembered sheet is
     * cleared where it was this one, or the app would try to reopen a file that is gone on
     * its next start.
     */
    fun deleteSheet(sheet: Sheet) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { folder.delete(sheet.uri) } }
                .onSuccess {
                    if (preferences.lastOpen == sheet.uri) preferences.lastOpen = null
                    _state.update { it.copy(sheets = it.sheets.filterNot { row -> row.uri == sheet.uri }) }
                }
                .onFailure { say("${sheet.name} could not be deleted.", it) }
        }
    }

    fun toggleWordCount() {
        val wanted = !state.value.wordCount
        preferences.wordCount = wanted
        _state.update { it.copy(wordCount = wanted) }
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
