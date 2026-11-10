package com.salesdrive.profile

import android.util.Log
import com.salesdrive.data.repo.ProfileRepository
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripRecorder
import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.SeatMemory
import com.salesdrive.voice.TodayBriefer
import com.salesdrive.voice.VoicePrompter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 마지막 시트 불러오기 결과. 화면에 "프리셋 1번 불러옴"처럼 보여 준다 */
data class SeatRecall(val slot: Int, val ok: Boolean, val at: Instant, val reason: String? = null)

/**
 * 시동이 켜지는 순간(IDLE → READY), 주차·정지 상태에서만.
 * 헤드유닛은 시동과 함께 켜지므로, 앱이 막 시작해 첫 신호로 READY가 되는 것도 여기에 포함된다.
 */
fun shouldRecallSeat(previous: TripPhase?, state: RecorderState): Boolean =
    previous == TripPhase.IDLE && state.trip.phase == TripPhase.READY && isParked(state)

/** 기어 P, 속도 0. 속도를 못 읽으면 기어만 본다 */
fun isParked(state: RecorderState): Boolean {
    val s = state.snapshot ?: return false
    return s.gear == Gear.P && (s.speedKmh ?: 0f) < 0.5f
}

/** 내 정보에 연결한 차량 시트 메모리 번호를 시동 시 불러온다. 주행 중에는 절대 움직이지 않는다 */
@Singleton
class SeatRestorer @Inject constructor(
    private val recorder: TripRecorder,
    private val profiles: ProfileRepository,
    private val seat: SeatMemory,
    private val voice: VoicePrompter,
    private val todayBriefer: TodayBriefer,
) {
    private val _last = MutableStateFlow<SeatRecall?>(null)
    val last: StateFlow<SeatRecall?> = _last.asStateFlow()

    private val lock = Mutex()

    suspend fun supportedSlots(): IntRange? = seat.supportedSlots()

    /** 서비스 코루틴에서 호출. 두 안내가 겹치지 않도록 시트를 맞춘 뒤 오늘 일정을 읽는다 */
    suspend fun run() {
        var previous: TripPhase? = null
        recorder.state.collect { state ->
            if (shouldRecallSeat(previous, state)) {
                profiles.me().seatMemorySlot?.let { recall(it, announce = true) }
                todayBriefer.announceOnce()
            }
            previous = state.trip.phase
        }
    }

    /** 내 정보 화면의 "지금 불러오기". 정차 중이 아니면 거절한다 */
    suspend fun recallNow(slot: Int): SeatRecall =
        if (!isParked(recorder.state.value)) {
            SeatRecall(slot, ok = false, at = Instant.now(), reason = "기어를 P에 두고 정차한 상태에서만 시트를 움직여요").also { _last.value = it }
        } else {
            recall(slot, announce = false)
        }

    private suspend fun recall(slot: Int, announce: Boolean): SeatRecall = lock.withLock {
        val result = when (val slots = seat.supportedSlots()) {
            null -> SeatRecall(slot, false, Instant.now(), "이 차량에서는 시트 메모리를 쓸 수 없어요")
            else -> if (slot !in slots) {
                SeatRecall(slot, false, Instant.now(), "이 차량의 시트 메모리는 ${slots.first}~${slots.last}번이에요")
            } else {
                if (announce) voice.speak("운전자 프리셋 ${slot}번으로 시트를 맞춥니다")
                // 안내하는 사이 출발했으면 움직이지 않는다
                if (!isParked(recorder.state.value)) {
                    SeatRecall(slot, false, Instant.now(), "출발해서 시트를 움직이지 않았어요")
                } else {
                    val ok = seat.recall(slot)
                    SeatRecall(slot, ok, Instant.now(), if (ok) null else "차량이 시트 명령을 받지 않았어요")
                }
            }
        }
        Log.i(TAG, "recall $result")
        _last.value = result
        result
    }

    private companion object {
        const val TAG = "SeatRestorer"
    }
}
