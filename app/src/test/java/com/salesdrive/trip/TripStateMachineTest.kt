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
        speed: Float? = 0f,
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

    @Test fun `parked for hold time stops and asks for confirmation`() {
        val (before, none) = run(
            TripState(TripPhase.DRIVING),
            snap(0, speed = 0f),
            snap(1, gear = Gear.P, brake = true),
            snap(30, gear = Gear.P, brake = true),
        )
        assertEquals(TripPhase.PAUSED, before.phase)
        assertTrue(none.isEmpty())

        val (after, effects) = run(before, snap(31, gear = Gear.P, brake = true, odo = 130.0))
        assertEquals(TripPhase.STOPPED, after.phase)
        assertEquals(130.0, (effects.single() as TripEffect.StopDetected).at.odometerKm, 0.0)
        assertEquals(StopDecision.UNDECIDED, after.stopDecision)
    }

    @Test fun `parking brake alone counts as parked when gear is unknown`() {
        val (state, effects) = run(
            TripState(TripPhase.PAUSED),
            snap(0, gear = Gear.UNKNOWN, brake = true),
            snap(30, gear = Gear.UNKNOWN, brake = true),
        )
        assertEquals(TripPhase.STOPPED, state.phase)
        assertTrue(effects.single() is TripEffect.StopDetected)
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

    @Test fun `without speed, gear alone drives a full trip`() {
        val (state, effects) = run(
            TripState(TripPhase.READY),
            snap(0, speed = null, gear = Gear.D, odo = 100.0),
            snap(600, speed = null, gear = Gear.D, odo = 110.0),
            snap(601, speed = null, gear = Gear.P, odo = 110.0),
            snap(631, speed = null, gear = Gear.P, odo = 110.0),
        )
        assertEquals(TripPhase.STOPPED, state.phase)
        val start = effects.filterIsInstance<TripEffect.StartTrip>().single().at
        val end = effects.filterIsInstance<TripEffect.StopDetected>().single().at
        assertEquals(10.0, end.odometerKm - start.odometerKm, 0.0)
    }

    @Test fun `without speed, waiting in D stays DRIVING`() {
        val (state, effects) = run(
            TripState(TripPhase.DRIVING),
            *Array(120) { snap(it.toLong(), speed = null, gear = Gear.D) },
        )
        assertEquals(TripPhase.DRIVING, state.phase)
        assertTrue(effects.isEmpty())
    }

    @Test fun `demo scenario records exactly one 30km trip`() = runTest {
        val source = FakeVehicleSource(
            scenario = FakeVehicleSource.DEMO_SCENARIO,
            from = FakeVehicleSource.DEMO_FROM,
            to = FakeVehicleSource.DEMO_TO,
            startTimeMs = 0,
        )
        val (state, effects) = run(TripState(), *source.snapshots.toList().toTypedArray())

        assertEquals(TripPhase.STOPPED, state.phase)
        val start = effects.filterIsInstance<TripEffect.StartTrip>().single().at
        val stop = effects.filterIsInstance<TripEffect.StopDetected>().single().at
        assertEquals(30.0, stop.odometerKm - start.odometerKm, 0.1)
        assertTrue(effects.none { it is TripEffect.EndTrip })
    }

    // --- 정차 후 운전자의 결정 ---

    private val stopped = TripState(TripPhase.STOPPED, stopAt = snap(30, gear = Gear.P, odo = 150.0))

    @Test fun `confirming a stop ends the trip at the stop time and asks`() {
        val tr = machine.reduce(stopped, TripEvent.VisitConfirmed(now = snap(45, gear = Gear.P, odo = 150.0)))
        assertEquals(TripPhase.ASKING, tr.state.phase)
        assertEquals(30_000L, (tr.effects[0] as TripEffect.EndTrip).at.timestampMs)
        assertEquals(TripEffect.AskVisit, tr.effects[1])
    }

    @Test fun `declined stop becomes a waypoint and the same trip continues`() {
        val declined = machine.reduce(stopped, TripEvent.VisitDeclined).state
        assertEquals(StopDecision.WAYPOINT, declined.stopDecision)

        val (state, effects) = run(declined, snap(600, speed = 50f, odo = 150.0))
        assertEquals(TripPhase.DRIVING, state.phase)
        assertTrue(effects.none { it is TripEffect.EndTrip || it is TripEffect.StartTrip })
    }

    @Test fun `driving off from an unanswered stop also continues the trip`() {
        val departure = snap(300, speed = 40f)
        val (state, effects) = run(stopped, departure)
        assertEquals(TripPhase.DRIVING, state.phase)
        assertEquals(
            listOf(TripEffect.CancelVisitPrompt, TripEffect.RecordWaypoint(stopped.stopAt!!, departure)),
            effects,
        )
    }

    @Test fun `rest stop with engine off keeps one trip and records the stop`() {
        val waypoint = machine.reduce(stopped, TripEvent.VisitDeclined).state

        val (state, effects) = run(
            waypoint,
            snap(600, ignition = false),          // 휴게소에서 시동 끄고 식사
            snap(2400, ignition = true, gear = Gear.P),
            snap(2410, speed = 30f, odo = 150.0), // 다시 출발
        )
        assertEquals(TripPhase.DRIVING, state.phase)
        assertTrue(effects.none { it is TripEffect.EndTrip || it is TripEffect.StartTrip })
        val stop = effects.filterIsInstance<TripEffect.RecordWaypoint>().single()
        assertEquals(30_000L, stop.arrived.timestampMs)
        assertEquals(2_410_000L, stop.departed.timestampMs)
    }

    @Test fun `ignition off at an unanswered stop ends the trip there without asking`() {
        val (state, effects) = run(stopped, snap(600, ignition = false, odo = 150.0))
        assertEquals(TripPhase.IDLE, state.phase)
        assertEquals(30_000L, effects.filterIsInstance<TripEffect.EndTrip>().single().at.timestampMs)
        assertTrue(effects.none { it == TripEffect.AskVisit })
    }

    @Test fun `button right after parking ends the trip now`() {
        val tr = machine.reduce(TripState(TripPhase.PAUSED), TripEvent.VisitConfirmed(now = snap(5, gear = Gear.P, odo = 120.0)))
        assertEquals(TripPhase.ASKING, tr.state.phase)
        assertEquals(120.0, (tr.effects[0] as TripEffect.EndTrip).at.odometerKm, 0.0)
    }

    @Test fun `button at a traffic light or while driving is ignored`() {
        val light = machine.reduce(TripState(TripPhase.PAUSED), TripEvent.VisitConfirmed(now = snap(5, gear = Gear.D)))
        assertEquals(TripPhase.PAUSED, light.state.phase)
        assertTrue(light.effects.isEmpty())

        val driving = machine.reduce(TripState(TripPhase.DRIVING), TripEvent.VisitConfirmed(now = snap(5, speed = 60f)))
        assertEquals(TripPhase.DRIVING, driving.state.phase)
        assertTrue(driving.effects.isEmpty())
    }

    @Test fun `button while waiting adds a visit to the last trip`() {
        val tr = machine.reduce(TripState(TripPhase.READY), TripEvent.VisitConfirmed(now = snap(5, gear = Gear.P)))
        assertEquals(TripPhase.ASKING, tr.state.phase)
        assertEquals(listOf<TripEffect>(TripEffect.AskVisit), tr.effects)
    }
}
