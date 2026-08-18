package ru.example.roadalert.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.BuildConfig

class AdUnitsTest {

    @Test
    fun `debug использует официальные demo-блоки Яндекса`() {
        assertEquals(AdUnits.DEMO_BANNER, BuildConfig.AD_UNIT_BANNER)
        assertEquals(AdUnits.DEMO_INTERSTITIAL, BuildConfig.AD_UNIT_INTERSTITIAL)
    }

    @Test
    fun `demo-блоки распознаются`() {
        assertTrue(AdUnits.isDemo("demo-banner-yandex"))
        assertTrue(AdUnits.isDemo("demo-interstitial-yandex"))
        assertFalse(AdUnits.isDemo("R-M-1234567-1"))
    }
}
