package com.wanderwildwood.dajiki.write

import android.app.Application
import android.app.KeyguardManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.dajiki.R
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A sheet and what was in it when it opened, handed over together so the page can key on it.
 * [at] is where the cursor starts, for a sheet opened from a search; null is the end, where a
 * sheet is almost always opened to carry on.
 */
data class Opened(val sheet: Sheet, val text: String, val at: Int? = null)

/** The three ways of looking at the folder: its sheets newest first, a month of days, and a search. */
enum class Showing { SHEETS, DAYS, SEARCH }

/** A line found by a search, and the sheet it is in. */
data class Hit(val sheet: Sheet, val found: Found)

/**
 * How the folder screen is being looked at, kept here rather than on the screen so it is
 * where it was on the way back from a sheet: the same month, the same search.
 */
data class Looking(
    val showing: Showing = Showing.SHEETS,
    val month: YearMonth = YearMonth.now(),
    /** The day picked on the calendar, whose sheets are listed beside it. */
    val day: LocalDate = LocalDate.now(),
    val query: String = "",
    /** What [hits] were found for; behind [query] while a search is waiting or running. */
    val searched: String = "",
    val hits: List<Hit> = emptyList(),
    val searching: Boolean = false,
    /** More lines matched than are shown. */
    val more: Boolean = false,
)

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
    /** Whether [size] belongs to the open sheet or to every sheet. */
    val sizePerSheet: Boolean = false,
    val wordCount: Boolean = true,
    val format: Format = Format.PLAIN,
    val openToToday: Boolean = false,
    /** Today's pages in a Diary folder of their own, and the Sheets tab without them. */
    val diaryFolder: Boolean = false,
    val lock: LockScope = LockScope.OFF,
    /** The phone's lock was given this sitting, and the app has not been away long since. */
    val unlocked: Boolean = false,
    /** The phone's lock is to be asked for now; the activity puts the prompt up. */
    val asking: Boolean = false,
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
            sizePerSheet = preferences.sizePerSheet,
            wordCount = preferences.wordCount,
            format = preferences.format,
            openToToday = preferences.openToToday,
            diaryFolder = preferences.diaryFolder,
            lock = preferences.lock,
            // The whole app locked: asked for as it opens.
            asking = preferences.lock == LockScope.APP,
        ),
    )
    val state: StateFlow<PageState> = _state.asStateFlow()

    private val _looking = MutableStateFlow(Looking())
    val looking: StateFlow<Looking> = _looking.asStateFlow()

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
     * The sheet this app made a moment ago and nothing has been typed into yet. Leaving it
     * empty would leave a 0-byte file in the reader's folder, so it is taken away again.
     */
    private var fresh: Uri? = null

    /** The fresh sheet if it is still untouched and being left, else null. */
    private fun untouched(sheet: Sheet?, text: String): Sheet? =
        sheet?.takeIf { it.uri == fresh && text.isEmpty() && written == "" }

    private suspend fun discard(sheet: Sheet) {
        runCatching { withContext(Dispatchers.IO) { folder.delete(sheet.uri) } }.onSuccess {
            if (fresh == sheet.uri) fresh = null
            if (preferences.lastOpen == sheet.uri) preferences.lastOpen = null
            preferences.forgetSize(sheet.uri)
            _state.update { it.copy(sheets = it.sheets.filterNot { row -> row.uri == sheet.uri }) }
        }
    }

    /** True while the sheet is being closed, so a spare copy does not pull the page onto itself. */
    private var leaving = false

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
        preferences.folder?.let { where ->
            if (preferences.openToToday) {
                // Straight to today's page rather than back to the sheet last left open.
                viewModelScope.launch {
                    listNow(where, reopen = false)
                    if (state.value.reading == Reading.DONE) openToday()
                }
            } else {
                list(where)
            }
        }
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
                    getApplication<Application>().getString(R.string.trouble_not_remembered)
                },
            )
        }
        list(uri)
    }

    private fun list(uri: Uri) {
        viewModelScope.launch { listNow(uri) }
    }

    private suspend fun listNow(uri: Uri, reopen: Boolean = true) {
        run {
            val sheets = runCatching { withContext(Dispatchers.IO) { folder.list(uri, state.value.diaryFolder) } }
            sheets.onSuccess { found ->
                _state.update { it.copy(sheets = found, reading = Reading.DONE) }
                // Back to whatever was being written when the app was last put down.
                val last = preferences.lastOpen
                if (reopen && last != null && state.value.opened == null) {
                    // Not a day's page past the diary lock: the folder rather than a prompt as
                    // the app opens. Behind the whole-app lock it opens under the lock screen.
                    found.firstOrNull { it.uri == last }
                        ?.takeUnless { state.value.lock == LockScope.DIARY && !state.value.unlocked && isDiaryPage(it) }
                        ?.let { openNow(it) }
                }
                // A search left open is answered again from the folder as it is now: the
                // sheet just closed may have gained or lost the very line it found.
                looking.value.query.takeIf { it.isNotBlank() }?.let { search(it, again = true) }
            }.onFailure { reason ->
                _state.update { it.copy(reading = Reading.FAILED) }
                // The grant can be gone: the folder may have been deleted, or its provider
                // uninstalled, or the reader may have cleared the app's data. Say which,
                // rather than showing an empty list that looks like an empty folder.
                say(getApplication<Application>().getString(R.string.trouble_folder_unreadable), reason)
            }
        }
    }

    fun open(sheet: Sheet, at: Int? = null) = guarded(isDiaryPage(sheet)) { openNow(sheet, at) }

    private fun openNow(sheet: Sheet, at: Int? = null) {
        saveNow()
        val abandoned = untouched(state.value.opened?.sheet, pending)
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
                    if (abandoned != null && abandoned.uri != sheet.uri) discard(abandoned)
                    fresh = null
                    pending = text
                    seen = stamp
                    written = text
                    stalled = false
                    leaving = false
                    preferences.lastOpen = sheet.uri
                    _state.update {
                        it.copy(
                            opened = Opened(sheet, text, at?.coerceIn(0, text.length)),
                            unsaved = false,
                            size = sizeOpening(sheet.uri),
                        )
                    }
                }
                .onFailure { say(getApplication<Application>().getString(R.string.trouble_open_failed, sheet.name), it) }
        }
    }

    /**
     * Today's page: the sheet named for today's date alone, made if there is none yet.
     *
     * The folder is read again first rather than trusted from the list on screen, so a page a
     * laptop made this morning is found rather than a second one started beside it. Opened
     * again later in the day, it gains a line with the time under what is there; nothing is
     * written until something is typed, so looking at the page changes nothing.
     */
    fun openToday() = guarded(diarySide = true) { openTodayNow() }

    private fun openTodayNow() {
        val where = state.value.folder ?: return
        val today = LocalDate.now()
        val current = state.value.opened?.sheet
        if (current != null && isPageFor(current.name, today)) return
        saveNow()
        val abandoned = untouched(current, pending)
        val format = state.value.format
        val diary = state.value.diaryFolder

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val found = folder.list(where, diary)
                    // With the diary in its own folder, its page there first; one made in the
                    // folder itself before the switch was turned on is still found.
                    val inDiary = found.filter { it.diary }
                    val elsewhere = found.filterNot { it.diary }
                    val existing = pageFor(inDiary.map { it.name }, today, format)?.let { name -> inDiary.firstOrNull { it.name == name } }
                        ?: pageFor(elsewhere.map { it.name }, today, format)?.let { name -> elsewhere.firstOrNull { it.name == name } }
                    if (existing != null) {
                        // Stamped before the read, for the reason open() is.
                        val stamp = folder.stamp(existing.uri)
                        Today(existing, stamp, folder.read(existing.uri), found, made = false)
                    } else {
                        val made = folder.create(where, format, dayName(today), diary = diary)
                        Today(made, folder.stamp(made.uri), "", listOf(made) + found, made = true)
                    }
                }
            }
                .onSuccess { page ->
                    val since = page.stamp?.modified?.takeIf { it > 0L }
                        ?.let { (System.currentTimeMillis() - it) / 60_000L }
                    val shown = pageReopened(page.text, clock(), since)
                    fresh = if (page.made) page.sheet.uri else null
                    pending = shown
                    seen = page.stamp
                    // What is on disk, not what is shown: the time line is not in the file
                    // until something is typed under it.
                    written = page.text
                    stalled = false
                    leaving = false
                    preferences.lastOpen = page.sheet.uri
                    _state.update {
                        it.copy(
                            opened = Opened(page.sheet, shown),
                            sheets = page.found,
                            reading = Reading.DONE,
                            unsaved = false,
                            size = sizeOpening(page.sheet.uri),
                        )
                    }
                    if (abandoned != null && abandoned.uri != page.sheet.uri) discard(abandoned)
                }
                .onFailure { say(getApplication<Application>().getString(R.string.trouble_today_failed), it) }
        }
    }

    private class Today(val sheet: Sheet, val stamp: Stamp?, val text: String, val found: List<Sheet>, val made: Boolean)

    /** The time now, the way the phone's own clock shows it: 21:40, or 9:40 PM. */
    private fun clock(): String {
        val pattern = if (DateFormat.is24HourFormat(getApplication())) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    /** By day and Search are the diary's side: By day lists its pages, Search reads them. */
    fun show(showing: Showing) =
        guarded(diarySide = showing != Showing.SHEETS) { _looking.update { it.copy(showing = showing) } }

    fun stepMonth(by: Long) = _looking.update { it.copy(month = it.month.plusMonths(by)) }

    fun pickDay(day: LocalDate) = _looking.update { it.copy(day = day) }

    /** Back to this month, with today picked. */
    fun thisMonth() = _looking.update { it.copy(month = YearMonth.now(), day = LocalDate.now()) }

    /** The search under way, cancelled by the next keystroke; the pause before it is the same idea as the save's. */
    private var hunt: Job? = null

    /**
     * What each sheet said when it was last read for a search, against the time it was
     * written, so a second search over the same folder reads only what has changed since.
     */
    private val readForSearch = ConcurrentHashMap<Uri, Pair<Long, String>>()

    /**
     * Look for [query] in every sheet in the folder, once the typing has paused.
     *
     * The files are read off the main thread, newest sheet first, and the search gives way the
     * moment another key is pressed. [again] runs it even though the query has not changed,
     * for when the folder has.
     */
    fun search(query: String, again: Boolean = false) {
        if (!again && query == looking.value.query) return
        _looking.update { it.copy(query = query) }
        hunt?.cancel()
        if (query.isBlank()) {
            _looking.update { it.copy(searched = "", hits = emptyList(), searching = false, more = false) }
            return
        }
        hunt = viewModelScope.launch {
            delay(if (again) 0L else SEARCH_PAUSE)
            _looking.update { it.copy(searching = true) }
            val (hits, more) = withContext(Dispatchers.IO) {
                // The folder as it is now rather than as it was last listed: a sync app may
                // have brought in a sheet while the app was open, and a search that cannot
                // find what is plainly in the folder is a search nobody trusts again.
                val sheets = state.value.folder
                    ?.let { where -> runCatching { folder.list(where, state.value.diaryFolder) }.getOrNull() }
                    ?.also { found -> _state.update { it.copy(sheets = found) } }
                    ?: state.value.sheets
                val hits = mutableListOf<Hit>()
                for (sheet in sheets) {
                    ensureActive()
                    val text = textForSearch(sheet) ?: continue
                    for (found in findIn(text, query)) {
                        if (hits.size == MAX_HITS) return@withContext hits to true
                        hits += Hit(sheet, found)
                    }
                }
                hits to false
            }
            _looking.update { it.copy(searched = query, hits = hits, searching = false, more = more) }
        }
    }

    private fun textForSearch(sheet: Sheet): String? {
        val kept = readForSearch[sheet.uri]
        if (kept != null && sheet.modified > 0L && kept.first == sheet.modified) return kept.second
        // A sheet that will not open is left out of the results rather than stopping them.
        val text = runCatching { folder.read(sheet.uri) }.getOrNull() ?: return null
        readForSearch[sheet.uri] = sheet.modified to text
        return text
    }

    fun newSheet() {
        val where = state.value.folder ?: return
        saveNow()
        val abandoned = untouched(state.value.opened?.sheet, pending)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { folder.create(where, state.value.format) } }
                .onSuccess { sheet ->
                    if (abandoned != null) discard(abandoned)
                    fresh = sheet.uri
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
                            size = sizeOpening(sheet.uri),
                        )
                    }
                }
                .onFailure { say(getApplication<Application>().getString(R.string.trouble_new_failed), it) }
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
     * The page is only put away once the save has landed. If it could not be written, anywhere,
     * the page stays and says why: the words are never dropped with the screen.
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
        leaving = true

        viewModelScope.launch {
            if (dirty && sheet != null) save(sheet, text)
            leaving = false
            // The page stays on screen until what is on it is safe. A save that failed leaves
            // the unsaved mark up and the words in the field, and the reason on a dialog.
            if (state.value.unsaved && sheet != null) return@launch
            preferences.lastOpen = null
            _state.update { it.copy(opened = null) }
            untouched(sheet, text)?.let { discard(it) }
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
        /** Not written: the sheet is no longer in the folder at all. */
        data object Gone : Saved

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
                if (before != null && vanished(before, folder.stamp(sheet.uri))) {
                    Saved.Gone
                } else if (movedOn(sheet.uri, before, onDisk)) {
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
                    Saved.Gone -> keepBeside(sheet, text, gone = true)
                }
            }
            .onFailure { say(getApplication<Application>().getString(R.string.trouble_save_failed, sheet.name), it) }
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
        val now = folder.stamp(uri)
        // Gone from the folder altogether (deleted or moved from outside while it was open):
        // writing to its address would fail, so the words have to go beside it.
        if (vanished(before, now)) return true
        if (now == null) return false
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
    private suspend fun keepBeside(sheet: Sheet, text: String, gone: Boolean = false) {
        val where = state.value.folder ?: return

        runCatching {
            withContext(Dispatchers.IO) {
                val copy = folder.createBeside(where, besideName(sheet.name), sheet.diary)
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
                val stillReading = state.value.opened != null && !leaving
                if (stillReading) preferences.lastOpen = copy.uri
                preferences.copySize(sheet.uri, copy.uri)

                _state.update {
                    it.copy(
                        opened = if (stillReading) Opened(copy, text) else null,
                        sheets = listOf(copy) + it.sheets,
                        unsaved = false,
                        trouble = getApplication<Application>().getString(
                            when {
                                gone && stillReading -> R.string.trouble_gone_here
                                gone -> R.string.trouble_gone_away
                                stillReading -> R.string.trouble_moved_on_here
                                else -> R.string.trouble_moved_on_away
                            },
                            sheet.name,
                            copy.name,
                        ),
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
                    getApplication<Application>().getString(R.string.trouble_moved_on_no_copy, sheet.name),
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
        // No sheet open: the folder list may be stale after a sync or another app's work.
        if (state.value.opened == null) {
            state.value.folder?.let { list(it) }
            return
        }
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
                withContext(Dispatchers.IO) { folder.rename(sheet, wanted) }
            }.getOrElse { reason ->
                say(getApplication<Application>().getString(R.string.trouble_rename_failed, sheet.name), reason); return@launch
            } ?: return@launch

            seen = withContext(Dispatchers.IO) { folder.stamp(moved.uri) }
            written = text
            preferences.lastOpen = moved.uri
            if (fresh == sheet.uri) fresh = moved.uri
            preferences.moveSize(sheet.uri, moved.uri)
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
                    preferences.forgetSize(sheet.uri)
                    _state.update { it.copy(sheets = it.sheets.filterNot { row -> row.uri == sheet.uri }) }
                }
                .onFailure { say(getApplication<Application>().getString(R.string.trouble_delete_failed, sheet.name), it) }
        }
    }

    fun toggleOpenToToday() {
        val wanted = !state.value.openToToday
        preferences.openToToday = wanted
        _state.update { it.copy(openToToday = wanted) }
    }

    /** The diary in a folder of its own, or not; the folder is read again either way. */
    fun toggleDiaryFolder() {
        val wanted = !state.value.diaryFolder
        preferences.diaryFolder = wanted
        _state.update { it.copy(diaryFolder = wanted) }
        state.value.folder?.let { list(it) }
    }

    /**
     * Move the lock on to [wanted]. Changing a lock that is on asks for the phone's lock first,
     * or it could be switched off by anybody holding the phone. Turning it on needs a screen
     * lock on the phone; without one it stays off and says why.
     */
    fun setLock(wanted: LockScope) = guarded(diarySide = true) {
        if (wanted != LockScope.OFF && !phoneHasLock()) {
            _state.update { it.copy(trouble = getApplication<Application>().getString(R.string.trouble_lock_needs_screen_lock)) }
            return@guarded
        }
        preferences.lock = wanted
        // Whoever turned it on is the one holding the phone now.
        _state.update { it.copy(lock = wanted, unlocked = true) }
    }

    private fun phoneHasLock(): Boolean =
        getApplication<Application>().getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** What waits on the phone's lock being given: the page, the tab or the setting asked for. */
    private var afterUnlock: (() -> Unit)? = null

    /** Do [action] now, or once the phone's lock has been given if it [needsLock]. */
    private fun guarded(diarySide: Boolean, action: () -> Unit) {
        val now = state.value
        if (!needsLock(now.lock, now.unlocked, diarySide)) {
            action()
            return
        }
        afterUnlock = action
        _state.update { it.copy(asking = true) }
    }

    /** Ask for the phone's lock again: the button on the lock screen. */
    fun askUnlock() = _state.update { it.copy(asking = true) }

    /** What came back from the phone's lock. */
    fun unlockAnswered(answer: Unlock) {
        val waiting = afterUnlock
        afterUnlock = null
        when (answer) {
            Unlock.DONE -> {
                _state.update { it.copy(asking = false, unlocked = true) }
                waiting?.invoke()
            }
            Unlock.CANCELLED -> _state.update { it.copy(asking = false) }
            Unlock.NO_SCREEN_LOCK -> {
                // The phone's screen lock was taken off since the lock was set. Nothing is
                // left for it to ask for, so it is off, and the reader is told rather than
                // shut out of their own writing.
                preferences.lock = LockScope.OFF
                _state.update {
                    it.copy(
                        lock = LockScope.OFF,
                        asking = false,
                        unlocked = true,
                        trouble = getApplication<Application>().getString(R.string.trouble_lock_gone),
                    )
                }
                waiting?.invoke()
            }
        }
    }

    /** When the app was last put away, for [relocks]. */
    private var stoppedAt: Long? = null

    /** The app has gone to the background. */
    fun stopped() {
        stoppedAt = System.currentTimeMillis()
    }

    /**
     * The app is back. Away long enough, the lock asks again: the whole app at once; with the
     * diary locked, By day and Search go back to Sheets, and a day's page left open is covered.
     */
    fun started() {
        val away = relocks(stoppedAt, System.currentTimeMillis())
        stoppedAt = null
        val now = state.value
        if (!away || now.lock == LockScope.OFF || !now.unlocked) return
        afterUnlock = null
        val covered = now.lock == LockScope.APP || now.opened?.sheet?.let(::isDiaryPage) == true
        _state.update { it.copy(unlocked = false, asking = covered) }
        if (now.lock == LockScope.DIARY) _looking.update { it.copy(showing = Showing.SHEETS) }
    }

    fun toggleWordCount() {
        val wanted = !state.value.wordCount
        preferences.wordCount = wanted
        _state.update { it.copy(wordCount = wanted) }
    }

    fun setFormat(format: Format) {
        preferences.format = format
        _state.update { it.copy(format = format) }
    }

    fun setTurn(turn: Turn) {
        preferences.turn = turn
        _state.update { it.copy(turn = turn) }
    }

    /**
     * The size, set from Settings, where no sheet is open.
     *
     * This is the setting itself: what a sheet with no size of its own is shown at, which
     * with the scope off is all of them.
     */
    fun setSize(size: Size) {
        preferences.size = size
        _state.update { it.copy(size = if (it.sizePerSheet) it.size else size) }
    }

    /**
     * The size, set from the page, where the text it sizes is the thing on the screen.
     *
     * Which of the two it writes is the whole of the scope setting: with it on the size
     * belongs to this sheet and the rest are left alone; with it off there is one size and
     * this is it.
     */
    fun setSizeHere(size: Size) {
        val open = state.value.opened?.sheet?.uri
        if (state.value.sizePerSheet && open != null) {
            preferences.setSizeOf(open, size)
        } else {
            preferences.size = size
        }
        _state.update { it.copy(size = size) }
    }

    /**
     * Turn the scope over.
     *
     * Sizes already set on sheets are kept rather than cleared, so turning this off and on
     * again finds them where they were. Turning it off shows the setting's size at once,
     * because that is what every sheet is now, including the one being read.
     */
    fun toggleSizePerSheet() {
        val wanted = !state.value.sizePerSheet
        preferences.sizePerSheet = wanted
        val open = state.value.opened?.sheet?.uri
        _state.update {
            it.copy(
                sizePerSheet = wanted,
                size = if (wanted && open != null) sizeOpening(open) else preferences.size,
            )
        }
    }

    /** What a sheet is shown at, by the rule in [sizeFor]. */
    private fun sizeOpening(sheet: Uri): Size =
        sizeFor(
            perSheet = preferences.sizePerSheet,
            own = preferences.sizeOf(sheet),
            setting = preferences.size,
        )

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

        /** Between the last key in the search field and the search: one repaint of results, not one per letter. */
        const val SEARCH_PAUSE = 500L

        /** Enough to read on a phone; a word that turns up more often than this wants a longer search. */
        const val MAX_HITS = 200
    }
}
