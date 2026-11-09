package com.salesdrive.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class OpenTripPolicyTest {

    private val zone = ZoneId.of("Asia/Seoul")

    private fun abandoned(startedAt: LocalDateTime, now: LocalDateTime) =
        OpenTripPolicy.isAbandoned(startedAt.atZone(zone).toInstant(), now.atZone(zone).toInstant(), zone)

    @Test fun `restart on the same day continues the trip`() {
        assertFalse(abandoned(LocalDateTime.of(2026, 11, 4, 8, 0), LocalDateTime.of(2026, 11, 4, 22, 0)))
    }

    @Test fun `restart while driving past midnight continues the trip`() {
        assertFalse(abandoned(LocalDateTime.of(2026, 11, 4, 23, 50), LocalDateTime.of(2026, 11, 5, 0, 10)))
    }

    @Test fun `trip left open until the next day is abandoned`() {
        assertTrue(abandoned(LocalDateTime.of(2026, 11, 4, 19, 16), LocalDateTime.of(2026, 11, 5, 14, 19)))
        assertTrue(abandoned(LocalDateTime.of(2026, 11, 4, 23, 0), LocalDateTime.of(2026, 11, 5, 8, 0)))
    }
}
