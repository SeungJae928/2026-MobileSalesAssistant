package com.salesdrive.trip

import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.PurposeSource
import com.salesdrive.data.db.Trip
import com.salesdrive.navi.NaviArrival
import com.salesdrive.navi.NaviState
import com.salesdrive.navi.NoNaviSource
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

        override suspend fun endTrip(at: VehicleSnapshot, destination: String?): Long? {
            val open = openTrip() ?: return null
            trips[trips.indexOf(open)] = open.copy(
                endedAt = Instant.ofEpochMilli(at.timestampMs),
                odoEndKm = at.odometerKm,
                naviDestination = destination,
            )
            return open.id
        }

        override suspend fun closeAbandoned(at: VehicleSnapshot) {
            val open = openTrip() ?: return
            trips[trips.indexOf(open)] = open.copy(endedAt = open.startedAt, odoEndKm = at.odometerKm)
        }

        val stops = mutableListOf<Pair<Long, VehicleSnapshot>>()
        val stopNames = mutableListOf<String?>()
        override suspend fun addStop(arrived: VehicleSnapshot, departed: VehicleSnapshot, name: String?) {
            openTrip()?.let {
                stops += it.id to arrived
                stopNames += name
            }
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
        val recorder = TripRecorder(source, store, NoNaviSource())
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

        val recorder = TripRecorder(ListSource(parked), store, NoNaviSource())
        recorder.run()
        recorder.confirmVisit()

        val trip = store.trips.single()
        assertEquals(512.0, trip.odoEndKm!!, 0.0)
    }

    @Test fun `trip abandoned overnight is closed on the first signal and the next drive is a new trip`() = runTest {
        val startedAt = 1_000_000_000_000L
        val nextDay = startedAt + 26 * 3_600_000L
        val store = MemoryStore(listOf(trip(1, odoStart = 500.0, startedAt = startedAt)))
        val parked = (0..5).map { t -> VehicleSnapshot(nextDay + t * 1000L, true, 0f, Gear.P, true, 512.0, null) }
        val drive = (6..9).map { t -> VehicleSnapshot(nextDay + t * 1000L, true, 40f, Gear.D, false, 512.0 + t, null) }
        val recorder = TripRecorder(ListSource(parked + drive), store, NoNaviSource())
        val prompts = mutableListOf<VisitPrompt>()
        val promptJob = launch(UnconfinedTestDispatcher(testScheduler)) { recorder.visitPrompts.toList(prompts) }

        recorder.run()
        promptJob.cancel()

        val closed = store.trips[0]
        assertEquals(Instant.ofEpochMilli(startedAt), closed.endedAt)
        assertEquals(512.0, closed.odoEndKm!!, 0.0)
        assertEquals(2, store.trips.size)
        assertNull(store.trips[1].endedAt)
        assertEquals(emptyList<VisitPrompt>(), prompts)
    }

    @Test fun `no open trip starts from IDLE and records nothing while parked`() = runTest {
        val store = MemoryStore()
        val parked = (0..60).map { t -> VehicleSnapshot(t * 1000L, true, 0f, Gear.P, true, 100.0, null) }

        val recorder = TripRecorder(ListSource(parked), store, NoNaviSource())
        recorder.run()

        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
        assertEquals(0, store.trips.size)
        assertNull(recorder.state.value.lastEffect)
    }

    @Test fun `answer given at confirmation is passed along with the ask`() = runTest {
        val store = MemoryStore(listOf(trip(1, odoStart = 500.0, startedAt = 0)))
        val parked = (0..31).map { t -> VehicleSnapshot(t * 1000L, true, 0f, Gear.P, true, 512.0, null) }
        val recorder = TripRecorder(ListSource(parked), store, NoNaviSource())
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

    @Test fun `leaving a stop without confirming records a waypoint on the same trip`() = runTest {
        val store = MemoryStore()
        val drive = (0..9).map { t -> VehicleSnapshot(t * 1000L, true, 60f, Gear.D, false, 100.0 + t, null) }
        val rest = (10..45).map { t -> VehicleSnapshot(t * 1000L, true, 0f, Gear.P, false, 110.0, null) }
        val again = (46..50).map { t -> VehicleSnapshot(t * 1000L, true, 60f, Gear.D, false, 110.0 + t - 46, null) }

        val recorder = TripRecorder(ListSource(drive + rest + again), store, NoNaviSource())
        recorder.run()

        assertEquals(1, store.trips.size)
        assertNull(store.trips.single().endedAt)
        assertEquals(1L to 110.0, store.stops.single().let { it.first to it.second.odometerKm })
    }

    // --- 내비 정보로 정차 판단 ---

    /** 출발 → 30초 이상 주차(정차 확정) → 다시 출발. 정차 확정 시각은 40초 */
    private val stopAndGo =
        (0..9).map { t -> VehicleSnapshot(t * 1000L, true, 60f, Gear.D, false, 100.0 + t, null) } +
            (10..45).map { t -> VehicleSnapshot(t * 1000L, true, 0f, Gear.P, false, 110.0, null) } +
            (46..50).map { t -> VehicleSnapshot(t * 1000L, true, 60f, Gear.D, false, 110.0 + t - 46, null) }

    @Test fun `navi destination arrival closes the trip and asks with the place name, without confirming`() = runTest {
        val store = MemoryStore()
        val navi = NoNaviSource(NaviState(arrival = NaviArrival(NaviArrival.Kind.DESTINATION, "A상사 본사", atMs = 9_000)))
        val parked = stopAndGo.take(46) // 다시 출발하지 않음
        val recorder = TripRecorder(ListSource(parked), store, navi)
        val prompts = mutableListOf<VisitPrompt>()
        val promptJob = launch(UnconfinedTestDispatcher(testScheduler)) { recorder.visitPrompts.toList(prompts) }

        recorder.run()
        promptJob.cancel()

        assertEquals(listOf<VisitPrompt>(VisitPrompt.Ask(1, place = "A상사 본사", greet = true)), prompts)
        assertEquals("A상사 본사", store.trips.single().naviDestination)
        assertEquals(TripPhase.ASKING, recorder.state.value.trip.phase)
        assertNull(navi.state.value.arrival)
    }

    @Test fun `far from the navi destination, a stop is a silent waypoint`() = runTest {
        val store = MemoryStore()
        val navi = NoNaviSource(NaviState(guiding = true, destinationName = "부산 B물산", remainMeters = 250_000))
        val recorder = TripRecorder(ListSource(stopAndGo), store, navi)
        val prompts = mutableListOf<VisitPrompt>()
        val promptJob = launch(UnconfinedTestDispatcher(testScheduler)) { recorder.visitPrompts.toList(prompts) }

        recorder.run()
        promptJob.cancel()

        assertEquals(listOf<VisitPrompt>(VisitPrompt.Cancel), prompts) // 확인 질문 없음
        assertNull(store.trips.single().endedAt)
        assertEquals(1, store.stops.size)
    }

    @Test fun `navi waypoint arrival names the stop`() = runTest {
        val store = MemoryStore()
        val navi = NoNaviSource(NaviState(arrival = NaviArrival(NaviArrival.Kind.WAYPOINT, "망향휴게소", atMs = 9_500)))
        val recorder = TripRecorder(ListSource(stopAndGo), store, navi)

        recorder.run()

        assertEquals(listOf<String?>("망향휴게소"), store.stopNames)
        assertNull(store.trips.single().endedAt)
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
