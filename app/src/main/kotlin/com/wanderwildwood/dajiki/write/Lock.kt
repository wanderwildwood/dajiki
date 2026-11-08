package com.wanderwildwood.dajiki.write

/*
 * The lock: the phone's own screen lock (its PIN, pattern or password), asked for before the
 * diary or the whole app opens.
 *
 * It keeps the pages from somebody holding the phone while it is unlocked, and nothing more.
 * The sheets are ordinary files in the reader's folder, which any file manager or sync app can
 * read, and Settings says so beside the switch. The rules are plain functions here so they can
 * be tested apart from Android.
 */

/** What the lock covers. */
enum class LockScope { OFF, DIARY, APP }

/**
 * Whether the phone's lock has to be asked for before something opens. [diarySide] is whether
 * the thing is part of the diary: today's page, By day, Search (which reads every page), a day's
 * page from the list, and the lock setting itself, so the lock cannot be switched off past it.
 */
fun needsLock(lock: LockScope, unlocked: Boolean, diarySide: Boolean): Boolean = when (lock) {
    LockScope.OFF -> false
    LockScope.DIARY -> diarySide && !unlocked
    LockScope.APP -> !unlocked
}

/**
 * Whether coming back to the app locks it again: it was put away ([stoppedAt], or null if it
 * never was) at least [LOCK_AFTER_MS] ago. A glance at a message and back does not ask again.
 */
fun relocks(stoppedAt: Long?, now: Long): Boolean =
    stoppedAt != null && now - stoppedAt >= LOCK_AFTER_MS

/** How long the app can be away before the lock asks again. */
const val LOCK_AFTER_MS = 3 * 60_000L

/** What came back from asking for the phone's lock. */
enum class Unlock { DONE, CANCELLED, NO_SCREEN_LOCK }
