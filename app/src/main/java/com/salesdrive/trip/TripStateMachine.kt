package com.salesdrive.trip

import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSnapshot

/**
 * ARRIVED는 머무는 상태가 아니라 주행이 확정되는 전이 순간이다.
 * 도착이 확정되면 같은 전이에서 [TripEffect.EndTrip]과 [TripEffect.AskVisit]을 내고 바로 ASKING으로 간다.
 */
enum class TripPhase { IDLE, READY, DRIVING, PAUSED, ASKING }

data class TripConfig(
    /** 이 속도를 넘으면 움직이는 것으로 본다 (정차 중 센서 잡음 무시) */
    val movingSpeedKmh: Float = 1f,
    /** PAUSED에서 주차 상태(P단 또는 주차브레이크)가 이만큼 유지되면 도착 확정 */
    val parkedHoldMs: Long = 30_000,
)

data class TripState(
    val phase: TripPhase = TripPhase.IDLE,
    /** PAUSED에서 주차 상태가 시작된 시각. 주차가 풀리면 null */
    val parkedSinceMs: Long? = null,
)

sealed interface TripEvent {
    data class Signal(val snapshot: VehicleSnapshot) : TripEvent

    /** 방문 질문이 끝남 (STT 응답 저장 또는 무응답 2회) */
    data object VisitPromptFinished : TripEvent
}

sealed interface TripEffect {
    data class StartTrip(val at: VehicleSnapshot) : TripEffect
    data class EndTrip(val at: VehicleSnapshot) : TripEffect
    data object AskVisit : TripEffect

    /** 질문 도중 다시 출발하거나 시동이 꺼져 질문을 접는다. 원문이 있으면 그대로 저장한다 */
    data object CancelVisitPrompt : TripEffect
}

data class Transition(val state: TripState, val effects: List<TripEffect> = emptyList())

/**
 * 순수 함수 상태머신. 시간은 스냅샷의 timestampMs만 쓰므로 Fake 재생 속도와 무관하게 같은 결과가 나온다.
 *
 * 시동 신호를 읽을 수 없으면(ignitionOn == null) 첫 신호를 시동으로 보고, 기어를 읽을 수 없으면 속도만으로 출발을 판단한다.
 */
class TripStateMachine(private val config: TripConfig = TripConfig()) {

    fun reduce(state: TripState, event: TripEvent): Transition = when (event) {
        is TripEvent.Signal -> onSignal(state, event.snapshot)
        TripEvent.VisitPromptFinished ->
            if (state.phase == TripPhase.ASKING) Transition(TripState(TripPhase.READY)) else Transition(state)
    }

    private fun onSignal(state: TripState, s: VehicleSnapshot): Transition {
        if (s.ignitionOn == false) return onIgnitionOff(state, s)

        val moving = s.speedKmh > config.movingSpeedKmh
        return when (state.phase) {
            TripPhase.IDLE -> Transition(TripState(TripPhase.READY))

            TripPhase.READY ->
                if (moving || s.gear == Gear.D || s.gear == Gear.R) {
                    Transition(TripState(TripPhase.DRIVING), listOf(TripEffect.StartTrip(s)))
                } else {
                    Transition(state)
                }

            TripPhase.DRIVING ->
                if (moving) Transition(state) else paused(s, parkedSinceMs = null)

            TripPhase.PAUSED ->
                if (moving) Transition(TripState(TripPhase.DRIVING)) else paused(s, state.parkedSinceMs)

            TripPhase.ASKING ->
                if (moving) {
                    Transition(
                        TripState(TripPhase.DRIVING),
                        listOf(TripEffect.CancelVisitPrompt, TripEffect.StartTrip(s)),
                    )
                } else {
                    Transition(state)
                }
        }
    }

    private fun paused(s: VehicleSnapshot, parkedSinceMs: Long?): Transition {
        val parked = s.gear == Gear.P || s.parkingBrakeOn == true
        if (!parked) return Transition(TripState(TripPhase.PAUSED))

        val since = parkedSinceMs ?: s.timestampMs
        return if (s.timestampMs - since >= config.parkedHoldMs) {
            Transition(TripState(TripPhase.ASKING), listOf(TripEffect.EndTrip(s), TripEffect.AskVisit))
        } else {
            Transition(TripState(TripPhase.PAUSED, parkedSinceMs = since))
        }
    }

    /** 시동이 꺼지면 헤드유닛도 곧 꺼지므로 질문 없이 주행만 닫는다. 방문 내용은 나중에 확인 화면에서 입력 */
    private fun onIgnitionOff(state: TripState, s: VehicleSnapshot): Transition = when (state.phase) {
        TripPhase.IDLE -> Transition(state)
        TripPhase.READY -> Transition(TripState(TripPhase.IDLE))
        TripPhase.DRIVING, TripPhase.PAUSED -> Transition(TripState(TripPhase.IDLE), listOf(TripEffect.EndTrip(s)))
        TripPhase.ASKING -> Transition(TripState(TripPhase.IDLE), listOf(TripEffect.CancelVisitPrompt))
    }
}
