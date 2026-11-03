package com.salesdrive.profile

import com.salesdrive.data.db.DriverProfile
import com.salesdrive.data.repo.needsSetup
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripState
import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatRestorerTest {

    private fun state(phase: TripPhase, gear: Gear = Gear.P, speed: Float? = 0f) =
        RecorderState(TripState(phase), VehicleSnapshot(0, true, speed, gear, true, 100.0, null))

    @Test fun `recalls once when ignition turns on while parked`() {
        assertTrue(shouldRecallSeat(TripPhase.IDLE, state(TripPhase.READY)))
        // 시동 켠 뒤 계속 READY거나, 방문 기록을 마치고 READY로 돌아온 경우는 다시 움직이지 않는다
        assertFalse(shouldRecallSeat(TripPhase.READY, state(TripPhase.READY)))
        assertFalse(shouldRecallSeat(TripPhase.ASKING, state(TripPhase.READY)))
        assertFalse(shouldRecallSeat(null, state(TripPhase.READY)))
    }

    @Test fun `never moves the seat unless parked`() {
        assertFalse(shouldRecallSeat(TripPhase.IDLE, state(TripPhase.READY, gear = Gear.D)))
        assertFalse(shouldRecallSeat(TripPhase.IDLE, state(TripPhase.READY, speed = 12f)))
        assertFalse(isParked(RecorderState()))
        assertTrue(isParked(state(TripPhase.READY, speed = null)))
    }

    @Test fun `profile needs setup until name and department are filled`() {
        assertTrue((null as DriverProfile?).needsSetup)
        assertTrue(DriverProfile(name = "기본 운전자", department = "영업1팀").needsSetup)
        assertTrue(DriverProfile(name = "김영업", department = null).needsSetup)
        assertFalse(DriverProfile(name = "김영업", department = "영업1팀").needsSetup)
    }
}
