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

/** How large the text on the page is set. Three steps of MMD's own scale, not three numbers. */
enum class Size { SMALL, MEDIUM, LARGE }

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
        const val WORD_COUNT = "word_count"
        const val LAST_OPEN = "last_open"
    }
}
