package com.pary.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatonusRouteFallbackTest {
    @Test
    fun scheduleDiscoveryContinuesAfterRouteSpecificErrors() {
        assertEquals(true, shouldTryNextScheduleEndpoint(403))
        assertEquals(true, shouldTryNextScheduleEndpoint(404))
        assertEquals(true, shouldTryNextScheduleEndpoint(405))
    }

    @Test
    fun authenticationAndServerFailuresRemainTerminal() {
        assertEquals(false, shouldTryNextScheduleEndpoint(200))
        assertEquals(false, shouldTryNextScheduleEndpoint(401))
        assertEquals(false, shouldTryNextScheduleEndpoint(429))
        assertEquals(false, shouldTryNextScheduleEndpoint(500))
        assertEquals(false, shouldTryNextScheduleEndpoint(-1))
    }
}
