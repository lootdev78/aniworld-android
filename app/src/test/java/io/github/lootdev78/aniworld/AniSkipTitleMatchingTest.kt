package io.github.lootdev78.aniworld

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSkipTitleMatchingTest {
    @Test
    fun `title matching ignores case only`() {
        assertTrue(aniWorldTitleEquals("Frieren: Beyond Journey's End", "FRIEREN: BEYOND JOURNEY'S END"))
        assertFalse(aniWorldTitleEquals("Frieren Beyond Journey's End", "Frieren: Beyond Journey's End"))
        assertFalse(aniWorldTitleEquals("Frieren - Beyond Journey's End", "Frieren: Beyond Journey's End"))
        assertFalse(aniWorldTitleEquals("Pokemon", "Pokémon"))
        assertFalse(aniWorldTitleEquals("My Hero Academia 2", "My Hero Academia II"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(aniWorldTitleEquals("  Anime Title  ", "anime title"))
    }
}
