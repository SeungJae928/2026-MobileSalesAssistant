package com.salesdrive.trip

import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.PurposeSource
import com.salesdrive.data.db.Trip
import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import com.salesdrive.vehicle.fake.FakeVehicleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TripRecorderTest {

    private class MemoryStore(initial: List<Trip> = emptyList()) : TripStore {
        val trips = initial.toMutableList()

        override suspend fun openTrip() = trips.lastOrNull { it.endedAt == null }

        override suspend fun startTrip(at: VehicleSnapshot): Long {
            val id = trips.size + 1L
            trips += trip(id, odoStart = at.odometerKm, startedAt = at.timestampMs)
            return id
        }

        override suspend fun endTrip(at: VehicleSnapshot): Long? {
            val open = openTrip() ?: return null
            trips[trips.indexOf(open)] = open.copy(endedAt = Instant.ofEpochMilli(at.timestampMs), odoEndKm = at.odometerKm)
            return open.id
        }

        override suspend fun lastEndedTripId() = trips.lastOrNull { it.endedAt != null }?.id
    }

    private class ListSource(snapshots: List<VehicleSnapshot>) : VehicleSignalSource {
        override val snapshots: Flow<VehicleSnapshot> = snapshots.asFlow()
    }

    @Test fun `demo scenario asks to confirm, and confirming closes one 30km trip`() = runTest {
        val store = MemoryStore()
        val source = FakeVehicleSource(
            scenario = FakeVehicleSource.DEMO_SCENARIO,
            from = FakeVehicleSource.DEMO_FROM,
            to = FakeVehicleSource.DEMO_TO,
            startTimeMs = 0,
        )
        val recorder = TripRecorder(source, store)
        val prompts = mutableListOf<VisitPrompt>()
        val promptJob = launch(UnconfinedTestDispatcher(testScheduler)) { recorder.visitPrompts.toList(prompts) }

        recorder.run()
        assertEquals(TripPhase.STOPPED, recorder.state.value.trip.phase)
        assertNull(store.trips.single().endedAt)

        recorder.confirmVisit()
        promptJob.cancel()

        val trip = store.trips.single()
        assertEquals(30.0, trip.odoEndKm!! - trip.odoStartKm, 0.1)
        assertEquals(listOf(VisitPrompt.Confirm, VisitPrompt.Ask(tripId = 1)), prompts)
        assertEquals(TripPhase.ASKING, recorder.state.value.trip.phase)

        recorder.finishVisitPrompt()
        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
    }

    @Test fun `open trip left by a crash is closed on the next arrival`() = runTest {
        val store = MemoryStore(listOf(trip(1, odoStart = 500.0, startedAt = 0)))
        val parked = (0..31).map { t ->
            VehicleSnapshot(t * 1000L, true, 0f, Gear.P, true, 512.0, null)
        }

        val recorder = TripRecorder(ListSource(parked), store)
        recorder.run()
        recorder.confirmVisit()

        val trip = store.trips.single()
        assertEquals(512.0, trip.odoEndKm!!, 0.0)
    }

    @Test fun `no open trip starts from IDLE and records nothing while parked`() = runTest {
        val store = MemoryStore()
        val parked = (0..60).map { t -> VehicleSnapshot(t * 1000L, true, 0f, Gear.P, true, 100.0, null) }

        val recorder = TripRecorder(ListSource(parked), store)
        recorder.run()

        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
        assertEquals(0, store.trips.size)
        assertNull(recorder.state.value.lastEffect)
    }

    @Test fun `answer given at confirmation is passed along with the ask`() = runTest {
        val store = MemoryStore(listOf(trip(1, odoStart = 500.0, startedAt = 0)))
        val parked = (0..31).map { t -> VehicleSnapshot(t * 1000L, true, 0f, Gear.P, true, 512.0, null) }
        val recorder = TripRecorder(ListSource(parked), store)
        val prompts = mutableListOf<VisitPrompt>()
        val promptJob = launch(UnconfinedTestDispatcher(testScheduler)) { recorder.visitPrompts.toList(prompts) }

        recorder.run()
        recorder.confirmVisit(answer = "A상사 김부장 미팅")
        recorder.finishVisitPrompt()
        recorder.confirmVisit() // 대기 중 버튼: 같은 주행에 방문 하나 더
        promptJob.cancel()

        assertEquals(
            listOf(VisitPrompt.Confirm, VisitPrompt.Ask(1, "A상사 김부장 미팅"), VisitPrompt.Ask(1, null)),
            prompts,
        )
    }

    private companion object {
        fun trip(id: Long, odoStart: Double, startedAt: Long) = Trip(
            id = id, vehicleId = 1, driverId = 1,
            startedAt = Instant.ofEpochMilli(startedAt),
            odoStartKm = odoStart,
            startLat = null, startLng = null,
            purpose = Purpose.BUSINESS, purposeSource = PurposeSource.DEFAULT,
        )
    }
}
