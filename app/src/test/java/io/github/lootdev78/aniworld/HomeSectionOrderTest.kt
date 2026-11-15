package io.github.lootdev78.aniworld

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionOrderTest {
    @Test
    fun `moving a section preserves every section and its new position`() {
        val original = HomeSection.DEFAULT_ORDER

        val moved = HomeSection.moveInOrder(original, HomeSection.FAVORITES, -2)

        assertEquals(HomeSection.FAVORITES, moved.first())
        assertEquals(HomeSection.entries.toSet(), moved.toSet())
        assertEquals(HomeSection.entries.size, moved.size)
    }

    @Test
    fun `moving past either end leaves the order unchanged`() {
        val original = HomeSection.DEFAULT_ORDER

        assertEquals(original, HomeSection.moveInOrder(original, original.first(), -1))
        assertEquals(original, HomeSection.moveInOrder(original, original.last(), 1))
    }
}
