package com.wanderwildwood.dajiki.write

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The size a page opens at, and the step a press takes.
 *
 * Both ways of getting the first one wrong are quiet on the screen: a sheet that has never
 * been set showing something other than the setting, and a size set on one sheet following
 * the reader into the next.
 */
class SizeTest {

    @Test
    fun `one size sets them all`() {
        assertEquals(Size.SMALL, sizeFor(perSheet = false, own = null, setting = Size.SMALL))
    }

    @Test
    fun `a sheet's own size is ignored while sizes are not kept per sheet`() {
        assertEquals(
            Size.MEDIUM,
            sizeFor(perSheet = false, own = Size.LARGE, setting = Size.MEDIUM),
        )
    }

    @Test
    fun `a sheet that has been set keeps what it was set to`() {
        assertEquals(
            Size.LARGE,
            sizeFor(perSheet = true, own = Size.LARGE, setting = Size.MEDIUM),
        )
    }

    @Test
    fun `a sheet that has never been set follows the setting`() {
        assertEquals(
            Size.SMALL,
            sizeFor(perSheet = true, own = null, setting = Size.SMALL),
        )
    }

    @Test
    fun `a press steps on and wraps round`() {
        assertEquals(Size.MEDIUM, next(Size.SMALL))
        assertEquals(Size.LARGE, next(Size.MEDIUM))
        assertEquals(Size.SMALL, next(Size.LARGE))
    }

    @Test
    fun `the same step serves the other setting that cycles`() {
        assertEquals(Turn.AROUND, next(Turn.ACROSS))
        assertEquals(Turn.ACROSS, next(Turn.DEVICE))
    }
}
