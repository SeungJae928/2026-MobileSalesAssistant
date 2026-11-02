package com.salesdrive.ui

import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripState
import com.salesdrive.ui.driving.isRestricted
import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRestrictionTest {

    private fun state(phase: TripPhase, gear: Gear) =
        RecorderState(TripState(phase), VehicleSnapshot(0, true, 0f, gear, false, 0.0, null))

    @Test fun `car restriction always wins`() {
        assertTrue(isRestricted(true, state(TripPhase.READY, Gear.P)))
    }

    @Test fun `driving or waiting at a light in D is restricted`() {
        assertTrue(isRestricted(false, state(TripPhase.DRIVING, Gear.D)))
        assertTrue(isRestricted(false, state(TripPhase.PAUSED, Gear.D)))
    }

    @Test fun `parked screens are usable`() {
        assertFalse(isRestricted(false, state(TripPhase.PAUSED, Gear.P)))
        assertFalse(isRestricted(false, state(TripPhase.STOPPED, Gear.P)))
        assertFalse(isRestricted(false, state(TripPhase.READY, Gear.P)))
    }
}
