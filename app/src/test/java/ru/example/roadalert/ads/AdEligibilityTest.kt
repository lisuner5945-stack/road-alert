package ru.example.roadalert.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdEligibilityTest {

    private val eligibleState = AdEligibility.State(
        tripsSinceLastAd = 3,
        lastAdShownAtMs = null,
        completedTripsTotal = 5,
        isFirstLaunch = false,
    )

    @Test
    fun `во время поездки fullscreen невозможен`() {
        assertFalse(
            AdEligibility.canShowInterstitial(
                state = eligibleState,
                tripActive = true,
                adsEnabled = true,
                nowMs = 0L,
            ),
        )
    }

    @Test
    fun `после завершённой поездки показ разрешён`() {
        assertTrue(
            AdEligibility.canShowInterstitial(eligibleState, tripActive = false, adsEnabled = true, nowMs = 0L),
        )
    }

    @Test
    fun `реклама выключена - показа нет`() {
        assertFalse(
            AdEligibility.canShowInterstitial(eligibleState, tripActive = false, adsEnabled = false, nowMs = 0L),
        )
    }

    @Test
    fun `при первом запуске рекламы нет`() {
        val state = eligibleState.copy(isFirstLaunch = true)
        assertFalse(AdEligibility.canShowInterstitial(state, false, true, 0L))
    }

    @Test
    fun `после первой поездки рекламы нет`() {
        val state = eligibleState.copy(completedTripsTotal = 1)
        assertFalse(AdEligibility.canShowInterstitial(state, false, true, 0L))
    }

    @Test
    fun `частота не чаще одного раза на три поездки`() {
        val state = eligibleState.copy(tripsSinceLastAd = 2)
        assertFalse(AdEligibility.canShowInterstitial(state, false, true, 0L))
    }

    @Test
    fun `не чаще одного раза в тридцать минут`() {
        val now = 10_000_000L
        val recent = eligibleState.copy(lastAdShownAtMs = now - 5 * 60_000L)
        assertFalse(AdEligibility.canShowInterstitial(recent, false, true, now))

        val old = eligibleState.copy(lastAdShownAtMs = now - 31 * 60_000L)
        assertTrue(AdEligibility.canShowInterstitial(old, false, true, now))
    }
}
