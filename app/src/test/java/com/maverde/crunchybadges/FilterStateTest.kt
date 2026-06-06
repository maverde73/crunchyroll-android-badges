package com.maverde.crunchybadges

import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.models.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStateTest {

    @Test fun defaultPlatformIsAll_andNotActive() {
        val f = FilterState()
        assertEquals(PlatformFilter.ALL, f.platform)
        assertFalse(f.hasActiveFilters())
    }

    @Test fun platformOtherThanAllIsActive() {
        val f = FilterState(platform = PlatformFilter.ANIME_GENERATION)
        assertTrue(f.hasActiveFilters())
    }

    @Test fun resetKeepsSortDropsPlatform() {
        val f = FilterState(platform = PlatformFilter.CRUNCHYROLL).reset()
        assertEquals(PlatformFilter.ALL, f.platform)
    }
}
