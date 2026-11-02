package com.salesdrive.navi

import org.junit.Assert.assertEquals
import org.junit.Test

class StopPolicyTest {

    private val stopAt = 3_600_000L

    @Test fun `no navi means ask the driver`() {
        assertEquals(StopVerdict.Ask, StopPolicy.classify(NaviState(), stopAt))
    }

    @Test fun `recent destination arrival is an arrival`() {
        val navi = NaviState(arrival = NaviArrival(NaviArrival.Kind.DESTINATION, "A상사", atMs = stopAt - 60_000))
        assertEquals(StopVerdict.Arrival("A상사"), StopPolicy.classify(navi, stopAt))
    }

    @Test fun `recent waypoint arrival is a waypoint`() {
        val navi = NaviState(arrival = NaviArrival(NaviArrival.Kind.WAYPOINT, "망향휴게소", atMs = stopAt - 60_000))
        assertEquals(StopVerdict.Waypoint("망향휴게소"), StopPolicy.classify(navi, stopAt))
    }

    @Test fun `stale arrival event is ignored`() {
        val navi = NaviState(arrival = NaviArrival(NaviArrival.Kind.DESTINATION, "A상사", atMs = stopAt - 30 * 60_000))
        assertEquals(StopVerdict.Ask, StopPolicy.classify(navi, stopAt))
    }

    @Test fun `guiding and far from destination is a rest stop`() {
        val navi = NaviState(guiding = true, destinationName = "B물산", remainMeters = 120_000)
        assertEquals(StopVerdict.Waypoint(null), StopPolicy.classify(navi, stopAt))
    }

    @Test fun `guiding and near the destination is an arrival`() {
        val navi = NaviState(guiding = true, destinationName = "B물산", remainMeters = 300)
        assertEquals(StopVerdict.Arrival("B물산"), StopPolicy.classify(navi, stopAt))
    }

    @Test fun `guiding without remaining distance asks`() {
        assertEquals(StopVerdict.Ask, StopPolicy.classify(NaviState(guiding = true, destinationName = "B물산"), stopAt))
    }
}
