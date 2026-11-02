package com.salesdrive.trip

import android.util.Log
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 음성 대화 쪽으로 보내는 요청 */
sealed interface VisitPrompt {
    /** 정차가 확정됨. 방문을 기록할지 운전자에게 묻는다 */
    data object Confirm : VisitPrompt

    /**
     * 방금 끝난 주행 [tripId]의 방문 내용을 묻는다. 닫을 주행이 없었으면 null.
     * 확인 질문에 방문 내용으로 바로 답했다면 [answer]에 담겨 있어 다시 묻지 않는다.
     */
    data class Ask(val tripId: Long?, val answer: String? = null) : VisitPrompt

    /** 대화 도중 다시 출발하거나 시동이 꺼짐 */
    data object Cancel : VisitPrompt
}

data class RecorderState(
    val trip: TripState = TripState(),
    val snapshot: VehicleSnapshot? = null,
    val lastEffect: TripEffect? = null,
)

/**
 * 차량 신호와 운전자의 결정을 상태머신에 넣고, 나온 effect를 저장소에 기록한다.
 * 화면·서비스는 이 객체 하나만 보고, 차량 신호를 따로 구독하지 않는다.
 */
@Singleton
class TripRecorder @Inject constructor(
    private val source: VehicleSignalSource,
    private val store: TripStore,
) {
    private val machine = TripStateMachine()
    private val mutex = Mutex()

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _visitPrompts = MutableSharedFlow<VisitPrompt>(extraBufferCapacity = 8)
    val visitPrompts: SharedFlow<VisitPrompt> = _visitPrompts.asSharedFlow()

    /** EndTrip과 AskVisit은 같은 전이에서 연달아 나오므로, 닫은 주행 id를 잠시 들고 있다가 질문에 붙인다 */
    private var lastEndedTripId: Long? = null

    /** 확인 질문에 방문 내용으로 바로 답한 경우, 다음 AskVisit에 실어 보낸다 */
    private var pendingAnswer: String? = null

    /**
     * 신호가 끊길 때까지 기록한다. 서비스 코루틴에서 호출.
     * 끝나지 않은 주행이 DB에 있으면(앱이 주행 중 종료됨) DRIVING에서 이어 가서, 다음 도착 때 그 주행을 닫는다.
     */
    suspend fun run() {
        if (store.openTrip() != null) {
            _state.value = RecorderState(trip = TripState(TripPhase.DRIVING))
            Log.i(TAG, "resume open trip")
        }
        source.snapshots.collect { handle(TripEvent.Signal(it), it) }
    }

    /**
     * 운전자가 방문을 기록하겠다고 함 (확인 질문에 "응", 또는 방문 기록 버튼).
     * [answer]가 있으면 확인 질문에 방문 내용으로 바로 답한 것이라 다시 묻지 않고 저장한다.
     */
    suspend fun confirmVisit(answer: String? = null) {
        pendingAnswer = answer
        handle(TripEvent.VisitConfirmed(_state.value.snapshot), snapshot = null)
        pendingAnswer = null // 주행 중이라 무시된 경우 다음 질문에 섞이지 않게
    }

    /** 이번 정차는 방문이 아님 (확인 질문에 "아니", 또는 건너뛰기 버튼) */
    suspend fun declineVisit() = handle(TripEvent.VisitDeclined, snapshot = null)

    /** 방문 질문이 끝남 (응답 저장 또는 무응답 2회) */
    suspend fun finishVisitPrompt() = handle(TripEvent.VisitPromptFinished, snapshot = null)

    /** 호출한 쪽(음성 대화)이 취소돼도 상태 전이와 DB 기록은 끝까지 마친다 */
    private suspend fun handle(event: TripEvent, snapshot: VehicleSnapshot?) = withContext(NonCancellable) {
        mutex.withLock {
            val before = _state.value
            val tr = machine.reduce(before.trip, event)
            for (effect in tr.effects) apply(effect)
            if (tr.state.phase != before.trip.phase) Log.i(TAG, "${before.trip.phase} -> ${tr.state.phase} ${tr.effects}")
            _state.value = RecorderState(
                trip = tr.state,
                snapshot = snapshot ?: before.snapshot,
                lastEffect = tr.effects.lastOrNull() ?: before.lastEffect,
            )
        }
    }

    private suspend fun apply(effect: TripEffect) {
        when (effect) {
            is TripEffect.StartTrip -> Log.i(TAG, "trip #${store.startTrip(effect.at)} started")
            is TripEffect.EndTrip -> lastEndedTripId = store.endTrip(effect.at)
            is TripEffect.StopDetected -> _visitPrompts.emit(VisitPrompt.Confirm)
            TripEffect.AskVisit -> {
                val tripId = lastEndedTripId ?: store.lastEndedTripId()
                _visitPrompts.emit(VisitPrompt.Ask(tripId, pendingAnswer))
            }
            TripEffect.CancelVisitPrompt -> _visitPrompts.emit(VisitPrompt.Cancel)
        }
    }

    private companion object {
        const val TAG = "TripRecorder"
    }
}
