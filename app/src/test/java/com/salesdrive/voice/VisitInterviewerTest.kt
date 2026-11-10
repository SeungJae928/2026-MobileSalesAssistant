package com.salesdrive.voice

import com.salesdrive.data.db.Trip
import com.salesdrive.navi.NoNaviSource
import com.salesdrive.trip.StopDecision
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripRecorder
import com.salesdrive.trip.TripStore
import com.salesdrive.trip.VisitPrompt
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

    private object NoBriefing : ArrivalBriefer {
        override suspend fun briefing(place: String): String? = null
    }

    private class KnownClient(val text: String) : ArrivalBriefer {
        var asked = false
        override suspend fun briefing(place: String): String? {
            asked = true
            return text
        }
    }

    @Test fun `known client is briefed before the visit question`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val spoken = Spoken()
        val interviewer = VisitInterviewer(recorder, spoken, Script("납품 완료"), Visits(), KnownClient("지난 방문은 11월 5일입니다."))

        interviewer.ask(VisitPrompt.Ask(7, place = "A상사 본사", greet = true))

        assertEquals(
            listOf("A상사 본사 도착하셨네요. 지난 방문은 11월 5일입니다.", VisitInterviewer.QUESTION, VisitInterviewer.SAVED),
            spoken.lines,
        )
    }

    @Test fun `first visit keeps the plain greeting`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val spoken = Spoken()

        VisitInterviewer(recorder, spoken, Script("납품 완료"), Visits(), NoBriefing).ask(VisitPrompt.Ask(7, place = "A상사 본사", greet = true))

        assertEquals(listOf("A상사 본사 도착하셨네요. ${VisitInterviewer.QUESTION}", VisitInterviewer.SAVED), spoken.lines)
    }

    @Test fun `no briefing unless navi named the arrival`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val briefer = KnownClient("지난 방문")

        VisitInterviewer(recorder, Spoken(), Script("납품 완료"), Visits(), briefer).ask(VisitPrompt.Ask(7))
        VisitInterviewer(recorder, Spoken(), Script(), Visits(), briefer).ask(VisitPrompt.Ask(7, answer = "바로 답함", place = "A상사", greet = true))

        assertEquals(false, briefer.asked)
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
        override suspend fun endTrip(at: VehicleSnapshot, destination: String?) = 7L
        override suspend fun lastEndedTripId() = 7L
        override suspend fun closeAbandoned(at: VehicleSnapshot) = Unit
        override suspend fun addStop(arrived: VehicleSnapshot, departed: VehicleSnapshot, name: String?) = Unit
    }

    private suspend fun ManualSource.driveAndArrive() {
        emit(0, 0f, Gear.P)
        emit(1, 40f, Gear.D)
        emit(100, 0f, Gear.P)
        emit(130, 0f, Gear.P)
    }

    @Test fun `answer on first try is saved for the ended trip`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val spoken = Spoken()
        val visits = Visits()

        VisitInterviewer(recorder, spoken, Script("A상사 김부장 납품 미팅"), visits, NoBriefing).interview(tripId = 7)

        assertEquals(listOf(7L to "A상사 김부장 납품 미팅"), visits.saved)
        assertEquals(listOf(VisitInterviewer.QUESTION, VisitInterviewer.SAVED), spoken.lines)
    }

    @Test fun `silence is asked again once, then left for later`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val spoken = Spoken()
        val visits = Visits()

        VisitInterviewer(recorder, spoken, Script(null, null), visits, NoBriefing).interview(tripId = 7)

        assertTrue(visits.saved.isEmpty())
        assertEquals(listOf(VisitInterviewer.QUESTION, VisitInterviewer.RETRY, VisitInterviewer.LATER), spoken.lines)
    }

    @Test fun `a filler like 어 counts as silence`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val visits = Visits()

        VisitInterviewer(recorder, Spoken(), Script("어", "D상사 재계약 논의"), visits, NoBriefing).interview(tripId = 7)

        assertEquals(listOf(7L to "D상사 재계약 논의"), visits.saved)
    }

    @Test fun `question greets with the place only when navi confirmed the arrival`() {
        assertEquals(VisitInterviewer.QUESTION, VisitInterviewer.question(VisitPrompt.Ask(7)))
        assertEquals(
            "A상사 본사 도착하셨네요. 방문 내용 말씀해 주세요.",
            VisitInterviewer.question(VisitPrompt.Ask(7, place = "A상사 본사", greet = true)),
        )
        assertEquals("도착하셨네요. 방문 내용 말씀해 주세요.", VisitInterviewer.question(VisitPrompt.Ask(7, greet = true)))
    }

    @Test fun `answer on retry is saved`() = runTest {
        val recorder = TripRecorder(ManualSource(), OneTripStore(), NoNaviSource())
        val visits = Visits()

        VisitInterviewer(recorder, Spoken(), Script(null, "개인 볼일"), visits, NoBriefing).interview(tripId = 7)

        assertEquals(listOf(7L to "개인 볼일"), visits.saved)
    }

    @Test fun `content given at confirmation is saved without asking again`() = runTest {
        val source = ManualSource()
        val recorder = TripRecorder(source, OneTripStore(), NoNaviSource())
        val visits = Visits()
        val interviewer = VisitInterviewer(recorder, Spoken(), Script("B물산 견적 회신"), visits, NoBriefing)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val jobs = listOf(launch(dispatcher) { recorder.run() }, launch(dispatcher) { interviewer.run() })

        source.driveAndArrive()
        runCurrent()

        assertEquals(listOf(7L to "B물산 견적 회신"), visits.saved)
        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
        jobs.forEach { it.cancel() }
    }

    /** 도착 → 확인 질문까지 진행시키고, 대화 결과를 돌려준다 */
    private suspend fun kotlinx.coroutines.test.TestScope.arriveWith(vararg answers: String?): Triple<TripRecorder, Spoken, Visits> {
        val source = ManualSource()
        val recorder = TripRecorder(source, OneTripStore(), NoNaviSource())
        val spoken = Spoken()
        val visits = Visits()
        val interviewer = VisitInterviewer(recorder, spoken, Script(*answers), visits, NoBriefing)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val jobs = listOf(launch(dispatcher) { recorder.run() }, launch(dispatcher) { interviewer.run() })
        source.driveAndArrive()
        runCurrent()
        jobs.forEach { it.cancel() }
        return Triple(recorder, spoken, visits)
    }

    @Test fun `yes at confirmation leads to the visit question`() = runTest {
        val (recorder, spoken, visits) = arriveWith("네", "C전자 박과장 데모")

        assertEquals(listOf(7L to "C전자 박과장 데모"), visits.saved)
        assertEquals(
            listOf(VisitInterviewer.CONFIRM, VisitInterviewer.QUESTION, VisitInterviewer.SAVED),
            spoken.lines,
        )
        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
    }

    @Test fun `no at confirmation keeps the trip going as a waypoint`() = runTest {
        val (recorder, spoken, visits) = arriveWith("아니 휴게소야")

        assertTrue(visits.saved.isEmpty())
        assertEquals(listOf(VisitInterviewer.CONFIRM, VisitInterviewer.DECLINED), spoken.lines)
        assertEquals(TripPhase.STOPPED, recorder.state.value.trip.phase)
        assertEquals(StopDecision.WAYPOINT, recorder.state.value.trip.stopDecision)
    }

    @Test fun `silence at confirmation leaves the decision to the screen`() = runTest {
        val (recorder, spoken, _) = arriveWith(null)

        assertEquals(listOf(VisitInterviewer.CONFIRM), spoken.lines)
        assertEquals(TripPhase.STOPPED, recorder.state.value.trip.phase)
        assertEquals(StopDecision.UNDECIDED, recorder.state.value.trip.stopDecision)
    }

    @Test fun `a misheard reply is asked again and not saved`() = runTest {
        val (recorder, spoken, visits) = arriveWith("이렇게", "네", "C전자 박과장 데모")

        assertEquals(listOf(7L to "C전자 박과장 데모"), visits.saved)
        assertEquals(
            listOf(VisitInterviewer.CONFIRM, VisitInterviewer.CONFIRM_RETRY, VisitInterviewer.QUESTION, VisitInterviewer.SAVED),
            spoken.lines,
        )
        assertEquals(TripPhase.READY, recorder.state.value.trip.phase)
    }

    @Test fun `misheard twice leaves the decision to the screen`() = runTest {
        val (recorder, spoken, visits) = arriveWith("이렇게", "이렇게")

        assertTrue(visits.saved.isEmpty())
        assertEquals(listOf(VisitInterviewer.CONFIRM, VisitInterviewer.CONFIRM_RETRY), spoken.lines)
        assertEquals(TripPhase.STOPPED, recorder.state.value.trip.phase)
        assertEquals(StopDecision.UNDECIDED, recorder.state.value.trip.stopDecision)
    }

    @Test fun `driving off while listening cancels the interview`() = runTest {
        val source = ManualSource()
        val recorder = TripRecorder(source, OneTripStore(), NoNaviSource())
        val capture = Script().apply { hangOnNext = true }
        val visits = Visits()
        val interviewer = VisitInterviewer(recorder, Spoken(), capture, visits, NoBriefing)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val jobs = listOf(launch(dispatcher) { recorder.run() }, launch(dispatcher) { interviewer.run() })

        source.driveAndArrive()
        runCurrent()
        assertEquals(TripPhase.STOPPED, recorder.state.value.trip.phase)

        source.emit(140, 30f, Gear.D)
        runCurrent()

        assertEquals(TripPhase.DRIVING, recorder.state.value.trip.phase)
        assertTrue(visits.saved.isEmpty())
        jobs.forEach { it.cancel() }
    }
}
