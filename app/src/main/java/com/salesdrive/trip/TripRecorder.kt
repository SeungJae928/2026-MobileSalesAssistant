package com.salesdrive.trip

import android.util.Log
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class RecorderState(
    val trip: TripState = TripState(),
    val snapshot: VehicleSnapshot? = null,
    val lastEffect: TripEffect? = null,
)

/**
 * 차량 신호를 상태머신에 넣고, 나온 effect를 저장소에 기록한다.
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

    /** [TripEffect.AskVisit] / [TripEffect.CancelVisitPrompt]. 음성 질문 쪽이 구독한다 */
    private val _visitPrompts = MutableSharedFlow<TripEffect>(extraBufferCapacity = 8)
    val visitPrompts: SharedFlow<TripEffect> = _visitPrompts.asSharedFlow()

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

    /** 방문 질문이 끝남 (응답 저장 또는 무응답 2회) */
    suspend fun finishVisitPrompt() = handle(TripEvent.VisitPromptFinished, snapshot = null)

    private suspend fun handle(event: TripEvent, snapshot: VehicleSnapshot?) = mutex.withLock {
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

    private suspend fun apply(effect: TripEffect) {
        when (effect) {
            is TripEffect.StartTrip -> Log.i(TAG, "trip #${store.startTrip(effect.at)} started")
            is TripEffect.EndTrip -> store.endTrip(effect.at)
            TripEffect.AskVisit, TripEffect.CancelVisitPrompt -> _visitPrompts.emit(effect)
        }
    }

    private companion object {
        const val TAG = "TripRecorder"
    }
}
