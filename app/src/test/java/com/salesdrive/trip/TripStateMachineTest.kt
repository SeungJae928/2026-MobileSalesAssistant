package com.salesdrive.trip

import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSnapshot
import com.salesdrive.vehicle.fake.FakeVehicleSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStateMachineTest {

    private val machine = TripStateMachine(TripConfig(parkedHoldMs = 30_000))

    private fun snap(
        t: Long,
        speed: Float = 0f,
        gear: Gear = Gear.D,
        brake: Boolean? = false,
        ignition: Boolean? = true,
        odo: Double = 100.0,
    ) = VehicleSnapshot(t * 1000, ignition, speed, gear, brake, odo, null)

    /** 신호를 순서대로 넣고 최종 상태와 모든 effect를 돌려준다 */
    private fun run(start: TripState, vararg snaps: VehicleSnapshot): Pair<TripState, List<TripEffect>> {
        var state = start
        val effects = mutableListOf<TripEffect>()
        for (s in snaps) {
            val tr = machine.reduce(state, TripEvent.Signal(s))
            state = tr.state
            effects += tr.effects
        }
        return state to effects
    }

    @Test fun `first signal with ignition on goes READY`() {
        val (state, effects) = run(TripState(), snap(0, gear = Gear.P, brake = true))
        assertEquals(TripPhase.READY, state.phase)
        assertTrue(effects.isEmpty())
    }

    @Test fun `unknown ignition still goes READY`() {
        val (state, _) = run(TripState(), snap(0, ignition = null, gear = Gear.UNKNOWN))
        assertEquals(TripPhase.READY, state.phase)
    }

    @Test fun `speed starts trip even when gear is unknown`() {
        val (state, effects) = run(TripState(TripPhase.READY), snap(0, speed = 20f, gear = Gear.UNKNOWN))
        assertEquals(TripPhase.DRIVING, state.phase)
        assertTrue(effects.single() is TripEffect.StartTrip)
    }

    @Test fun `long traffic light stop in D is not an arrival`() {
        val (state, effects) = run(
            TripState(TripPhase.DRIVING),
            *Array(120) { snap(it.toLong(), speed = 0f, gear = Gear.D) },
        )
        assertEquals(TripPhase.PAUSED, state.phase)
        assertTrue(effects.isEmpty())
    }

    @Test fun `parked for hold time confirms arrival and asks`() {
        val (before, none) = run(
            TripState(TripPhase.DRIVING),
            snap(0, speed = 0f),
            snap(1, gear = Gear.P, brake = true),
            snap(30, gear = Gear.P, brake = true),
        )
        assertEquals(TripPhase.PAUSED, before.phase)
        assertTrue(none.isEmpty())

        val (after, effects) = run(before, snap(31, gear = Gear.P, brake = true, odo = 130.0))
        assertEquals(TripPhase.ASKING, after.phase)
        assertEquals(130.0, (effects[0] as TripEffect.EndTrip).at.odometerKm, 0.0)
        assertEquals(TripEffect.AskVisit, effects[1])
    }

    @Test fun `parking brake alone counts as parked when gear is unknown`() {
        val (state, effects) = run(
            TripState(TripPhase.PAUSED),
            snap(0, gear = Gear.UNKNOWN, brake = true),
            snap(30, gear = Gear.UNKNOWN, brake = true),
        )
        assertEquals(TripPhase.ASKING, state.phase)
        assertEquals(2, effects.size)
    }

    @Test fun `releasing park resets the hold timer`() {
        val (state, effects) = run(
            TripState(TripPhase.PAUSED),
            snap(0, gear = Gear.P),
            snap(20, gear = Gear.D),
            snap(21, gear = Gear.P),
            snap(40, gear = Gear.P),
        )
        assertEquals(TripPhase.PAUSED, state.phase)
        assertEquals(21_000L, state.parkedSinceMs)
        assertTrue(effects.isEmpty())
    }

    @Test fun `driving off while asking cancels prompt and starts new trip`() {
        val (state, effects) = run(TripState(TripPhase.ASKING), snap(0, speed = 15f))
        assertEquals(TripPhase.DRIVING, state.phase)
        assertEquals(TripEffect.CancelVisitPrompt, effects[0])
        assertTrue(effects[1] is TripEffect.StartTrip)
    }

    @Test fun `visit prompt finished returns to READY`() {
        val tr = machine.reduce(TripState(TripPhase.ASKING), TripEvent.VisitPromptFinished)
        assertEquals(TripPhase.READY, tr.state.phase)
    }

    @Test fun `ignition off mid trip ends trip without asking`() {
        val (state, effects) = run(TripState(TripPhase.PAUSED), snap(0, ignition = false))
        assertEquals(TripPhase.IDLE, state.phase)
        assertTrue(effects.single() is TripEffect.EndTrip)
    }

    @Test fun `demo scenario records exactly one 30km trip`() = runTest {
        val source = FakeVehicleSource(
            scenario = FakeVehicleSource.DEMO_SCENARIO,
            from = FakeVehicleSource.DEMO_FROM,
            to = FakeVehicleSource.DEMO_TO,
            startTimeMs = 0,
        )
        val (state, effects) = run(TripState(), *source.snapshots.toList().toTypedArray())

        assertEquals(TripPhase.ASKING, state.phase)
        val start = effects.filterIsInstance<TripEffect.StartTrip>().single().at
        val end = effects.filterIsInstance<TripEffect.EndTrip>().single().at
        assertEquals(30.0, end.odometerKm - start.odometerKm, 0.1)
        assertEquals(1, effects.count { it == TripEffect.AskVisit })
    }
}
