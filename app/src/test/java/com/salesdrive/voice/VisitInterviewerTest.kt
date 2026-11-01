package com.salesdrive.voice

import com.salesdrive.data.db.Trip
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripRecorder
import com.salesdrive.trip.TripStore
import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitInterviewerTest {

    private class Script(vararg answers: String?) : SpeechCapture {
        private val queue = ArrayDeque(answers.toList())
        var hangOnNext = false
        override suspend fun listen(): String? {
            if (hangOnNext) awaitCancellation()
            return queue.removeFirstOrNull()
        }
    }

    private class Spoken : VoicePrompter {
        val lines = mutableListOf<String>()
        override suspend fun speak(text: String) { lines += text }
    }

    private class Visits : VisitStore {
        val saved = mutableListOf<Pair<Long, String>>()
        override suspend fun saveRaw(tripId: Long, utterance: String): Long {
            saved += tripId to utterance
            return saved.size.toLong()
        }
    }

    /** 신호를 테스트가 직접 밀어 넣는 소스. 주행 하나를 만들고 도착시켜 ASKING 상태로 만든다 */
    private class ManualSource : VehicleSignalSource {
        val flow = MutableSharedFlow<VehicleSnapshot>()
        override val snapshots: Flow<VehicleSnapshot> = flow
        suspend fun emit(t: Long, speed: Float, gear: Gear) =
            flow.emit(VehicleSnapshot(t * 1000, true, speed, gear, gear == Gear.P, 100.0 + t, null))
    }

    private class OneTripStore : TripStore {
        override suspend fun openTrip(): Trip? = null
        override suspend fun startTrip(at: VehicleSnapshot) = 7L
        override suspend fun endTrip(at: VehicleSnapshot) = 7L
    }

    private suspend fun ManualSource.driveAndArrive() {
        emit(0, 0f, Gear.P)
        emit(1, 40f, Gear.D)
        emit(100, 0f, Gear.P)
        emit(130, 0f, Gear.P)
    }

    @Test fun `answer on first try is saved for the ended trip`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore())
        val spoken = Spoken()
        val visits = Visits()

        VisitInterviewer(recorder, spoken, Script("A상사 김부장 납품 미팅"), visits).interview(tripId = 7)

        assertEquals(listOf(7L to "A상사 김부장 납품 미팅"), visits.saved)
        assertEquals(listOf(VisitInterviewer.QUESTION, VisitInterviewer.SAVED), spoken.lines)
    }

    @Test fun `silence is asked again once, then left for later`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore())
        val spoken = Spoken()
        val visits = Visits()

        VisitInterviewer(recorder, spoken, Script(null, null), visits).interview(tripId = 7)

        assertTrue(visits.saved.isEmpty())
        assertEquals(listOf(VisitInterviewer.QUESTION, VisitInterviewer.RETRY, VisitInterviewer.LATER), spoken.lines)
    }

    @Test fun `answer on retry is saved`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore())
        val visits = Visits()

        VisitInterviewer(recorder, Spoken(), Script(null, "개인 볼일"), visits).interview(tripId = 7)

        assertEquals(listOf(7L to "개인 볼일"), visits.saved)
    }

    @Test fun `arrival asks, answer returns recorder to READY`() = runTest {
        val source = ManualSource()
        val recorder = TripRecorder(source, OneTripStore())
        val visits = Visits()
        val interviewer = VisitInterviewer(recorder, Spoken(), Script("B물산 견적 회신"), visits)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val jobs = listOf(launch(dispatcher) { recorder.run() }, launch(dispatcher) { interviewer.run() })

        source.driveAndArrive()
        runCurrent()

        assertEquals(listOf(7L to "B물산 견적 회신"), visits.saved)
        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
        jobs.forEach { it.cancel() }
    }

    @Test fun `driving off while listening cancels the interview`() = runTest {
        val source = ManualSource()
        val recorder = TripRecorder(source, OneTripStore())
        val capture = Script().apply { hangOnNext = true }
        val visits = Visits()
        val interviewer = VisitInterviewer(recorder, Spoken(), capture, visits)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val jobs = listOf(launch(dispatcher) { recorder.run() }, launch(dispatcher) { interviewer.run() })

        source.driveAndArrive()
        runCurrent()
        assertEquals(TripPhase.ASKING, recorder.state.value.trip.phase)

        source.emit(140, 30f, Gear.D)
        runCurrent()

        assertEquals(TripPhase.DRIVING, recorder.state.value.trip.phase)
        assertTrue(visits.saved.isEmpty())
        jobs.forEach { it.cancel() }
    }
}
