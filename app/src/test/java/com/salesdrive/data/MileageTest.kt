package com.salesdrive.data

import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.PurposeSource
import com.salesdrive.data.db.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MileageTest {

    private fun trip(start: Double, end: Double?, purpose: Purpose) = Trip(
        vehicleId = 1, driverId = 1, startedAt = Instant.EPOCH, endedAt = end?.let { Instant.EPOCH },
        odoStartKm = start, odoEndKm = end, startLat = null, startLng = null,
        purpose = purpose, purposeSource = PurposeSource.DEFAULT,
    )

    @Test fun `totals split by purpose and ratio counts commute as business use`() {
        val t = Mileage.totals(
            listOf(
                trip(0.0, 60.0, Purpose.BUSINESS),
                trip(60.0, 80.0, Purpose.COMMUTE),
                trip(80.0, 100.0, Purpose.PERSONAL),
                trip(100.0, null, Purpose.BUSINESS), // 진행 중은 0
            ),
        )
        assertEquals(100.0, t.totalKm, 1e-9)
        assertEquals(60.0, t.businessKm, 1e-9)
        assertEquals(20.0, t.commuteKm, 1e-9)
        assertEquals(20.0, t.personalKm, 1e-9)
        assertEquals(0.8, t.businessRatio!!, 1e-9)
    }

    @Test fun `no driving has no ratio`() {
        assertNull(Mileage.totals(emptyList()).businessRatio)
    }
}
