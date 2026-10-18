package com.ibs.ibs_antdrivers

import org.junit.Assert.*
import org.junit.Test

class CatalogueRenderingTest {
    @Test fun `large posters and unusual page ratios stay within the bitmap memory budget`() {
        listOf(100_000 to 70_000, 70_000 to 100_000, 1 to 100_000, Int.MAX_VALUE to 1)
            .forEach { (width, height) ->
                val (bitmapWidth, bitmapHeight) = catalogueBitmapSize(width, height, 1080)
                assertTrue(bitmapWidth in 1..2048)
                assertTrue(bitmapHeight in 1..2048)
                assertTrue(bitmapWidth.toLong() * bitmapHeight * 4 <= 16L * 1024 * 1024)
            }
    }

    @Test fun `normal pages preserve their proportions at screen resolution`() {
        val (width, height) = catalogueBitmapSize(600, 900, 1080)
        assertEquals(1080, width)
        assertEquals(1620, height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid page dimensions are rejected before allocating a bitmap`() {
        catalogueBitmapSize(0, 0, 1080)
    }
}
