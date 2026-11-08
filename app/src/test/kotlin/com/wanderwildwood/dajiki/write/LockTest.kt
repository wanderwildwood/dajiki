package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {

    @Test
    fun offNeverAsks() {
        assertFalse(needsLock(LockScope.OFF, unlocked = false, diarySide = true))
        assertFalse(needsLock(LockScope.OFF, unlocked = false, diarySide = false))
    }

    @Test
    fun theDiaryLockAsksOnlyForTheDiary() {
        assertTrue(needsLock(LockScope.DIARY, unlocked = false, diarySide = true))
        assertFalse(needsLock(LockScope.DIARY, unlocked = false, diarySide = false))
    }

    @Test
    fun theWholeAppLockAsksForEverything() {
        assertTrue(needsLock(LockScope.APP, unlocked = false, diarySide = false))
        assertTrue(needsLock(LockScope.APP, unlocked = false, diarySide = true))
    }

    @Test
    fun onceUnlockedNothingAsks() {
        assertFalse(needsLock(LockScope.DIARY, unlocked = true, diarySide = true))
        assertFalse(needsLock(LockScope.APP, unlocked = true, diarySide = true))
    }

    @Test
    fun aShortTimeAwayDoesNotLockAgain() {
        assertFalse(relocks(stoppedAt = 1_000L, now = 1_000L + LOCK_AFTER_MS - 1))
        assertFalse(relocks(stoppedAt = null, now = 10_000_000L))
    }

    @Test
    fun aLongerTimeAwayLocksAgain() {
        assertTrue(relocks(stoppedAt = 1_000L, now = 1_000L + LOCK_AFTER_MS))
    }
}
