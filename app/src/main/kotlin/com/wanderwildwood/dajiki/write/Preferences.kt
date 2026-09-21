package com.wanderwildwood.dajiki.write

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri

/**
 * Which way up the screen is held.
 *
 * ACROSS is what the manifest already declares, and is the reason the app exists. AROUND is
 * the same thing rotated half a turn, for a keyboard case that puts the hinge on the other
 * side. DEVICE hands the decision back, for reading rather than writing.
 *
 * None of these needs a sensor. A panel with no accelerometer in it — which several of these
 * devices are — still honours all three, where anything sensor-driven would pick one at random
 * and stay there.
 */
enum class Turn(val activityInfo: Int) {
    ACROSS(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    AROUND(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    DEVICE(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
}

/**
 * How large the text on the page is set.
 *
 * Three steps of MMD's own scale, not three numbers — but three taken from across it rather
 * than three neighbours on the body ramp. What the page is set to is read for hours; what the
 * ramp was built for is a label on a row.
 */
enum class Size { SMALL, MEDIUM, LARGE }

/**
 * What a sheet is shown at: its own size where it has one and sizes are kept per sheet,
 * and the setting otherwise.
 *
 * Apart from the store so the rule can be read and tested on its own. The two ways of
 * getting it wrong are both quiet: a sheet with no size of its own showing something other
 * than the setting, and a size set on one sheet following the reader into the next.
 */
fun sizeFor(perSheet: Boolean, own: Size?, setting: Size): Size =
    if (perSheet) own ?: setting else setting

/** The next step of a setting that cycles, wrapping round at the end. */
inline fun <reified T : Enum<T>> next(current: T): T {
    val all = enumValues<T>()
    return all[(current.ordinal + 1) % all.size]
}

/** The three things there are to set, and the folder the reader chose once. */
class Preferences(context: Context) {

    private val store = context.getSharedPreferences("dajiki", Context.MODE_PRIVATE)

    /**
     * The folder, as the document provider named it.
     *
     * Stored as a string rather than a path: this is a grant from the system picker, not a
     * place on a disk, and the folder may well belong to a sync app that owns no path at all.
     */
    var folder: Uri?
        get() = store.getString(FOLDER, null)?.let(Uri::parse)
        set(value) = store.edit().putString(FOLDER, value?.toString()).apply()

    var turn: Turn
        get() = runCatching { Turn.valueOf(store.getString(TURN, null) ?: "") }
            .getOrDefault(Turn.ACROSS)
        set(value) = store.edit().putString(TURN, value.name).apply()

    var size: Size
        get() = runCatching { Size.valueOf(store.getString(SIZE, null) ?: "") }
            .getOrDefault(Size.MEDIUM)
        set(value) = store.edit().putString(SIZE, value.name).apply()

    /**
     * Whether a sheet keeps a size of its own, or one size sets them all.
     *
     * Off is the default and the simpler thing: the size is a property of the reader's eyes,
     * not of the writing. On is for a folder where the sheets are not all the same job — a
     * long draft read at arm's length and a page of notes squinted at close to.
     */
    var sizePerSheet: Boolean
        get() = store.getBoolean(SIZE_PER_SHEET, false)
        set(value) = store.edit().putBoolean(SIZE_PER_SHEET, value).apply()

    /**
     * The size a particular sheet has been set to, or null where it has never been set.
     *
     * Kept here rather than beside the writing. A sidecar file in the reader's folder would
     * be the tidier answer to look at and the wrong one to live with: the folder is theirs,
     * it may be synced, and what is in it should be the sheets and nothing else.
     *
     * ⚠ The key is the document address, which is what the provider hands back and not
     * anything about the name. That is why [moveSize] exists: a rename gives the sheet a new
     * address, and without carrying the size across, changing a sheet's name would silently
     * reset how it looks.
     */
    fun sizeOf(sheet: Uri): Size? =
        store.getString(SIZE_OF + sheet, null)
            ?.let { name -> runCatching { Size.valueOf(name) }.getOrNull() }

    fun setSizeOf(sheet: Uri, size: Size) =
        store.edit().putString(SIZE_OF + sheet, size.name).apply()

    /** A sheet that is gone takes its size with it, rather than leaving a key behind. */
    fun forgetSize(sheet: Uri) = store.edit().remove(SIZE_OF + sheet).apply()

    /** A renamed sheet is the same writing at a new address, so the size follows it. */
    fun moveSize(from: Uri, to: Uri) {
        val size = sizeOf(from) ?: return
        store.edit().remove(SIZE_OF + from).putString(SIZE_OF + to, size.name).apply()
    }

    /** A sheet forked in a conflict is a copy of the writing, so the size is copied too. */
    fun copySize(from: Uri, to: Uri) {
        val size = sizeOf(from) ?: return
        setSizeOf(to, size)
    }

    /**
     * Whether the foot of the page counts the words.
     *
     * Off is a real answer, not a tidying preference. The count is runs of non-whitespace,
     * which is what a writer of English expects and is simply wrong for a language that does
     * not put spaces between its words: a page of Japanese counts as one word. Rather than
     * show a number that is wrong in somebody's language, it can be put away.
     */
    var wordCount: Boolean
        get() = store.getBoolean(WORD_COUNT, true)
        set(value) = store.edit().putBoolean(WORD_COUNT, value).apply()

    /** The sheet that was open when the app was last put down, so it opens there again. */
    var lastOpen: Uri?
        get() = store.getString(LAST_OPEN, null)?.let(Uri::parse)
        set(value) = store.edit().putString(LAST_OPEN, value?.toString()).apply()

    private companion object {
        const val FOLDER = "folder"
        const val TURN = "turn"
        const val SIZE = "size"
        const val SIZE_PER_SHEET = "size_per_sheet"
        const val SIZE_OF = "size_of:"
        const val WORD_COUNT = "word_count"
        const val LAST_OPEN = "last_open"
    }
}
