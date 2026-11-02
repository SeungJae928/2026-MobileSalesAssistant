package com.salesdrive.trip

import com.salesdrive.vehicle.Gear
import com.salesdrive.vehicle.VehicleSnapshot

/**
 * 주차가 확정되면 바로 도착으로 보지 않고 STOPPED에서 운전자의 결정을 기다린다.
 * 휴게소·졸음쉼터처럼 잠깐 서는 곳에서 주행이 끊기지 않도록, 도착은 운전자가 확인(음성 "응"·버튼)하거나 시동을 끌 때만 확정한다.
 * 확정되면 그 정차 시점으로 주행을 닫고 ASKING에서 방문 내용을 묻는다.
 */
enum class TripPhase { IDLE, READY, DRIVING, PAUSED, STOPPED, ASKING }

/** 정차 지점에 대한 운전자의 결정. 아직 묻지 않았거나 답이 없으면 UNDECIDED */
enum class StopDecision { UNDECIDED, WAYPOINT }

data class TripConfig(
    /** 이 속도를 넘으면 움직이는 것으로 본다 (정차 중 센서 잡음 무시) */
    val movingSpeedKmh: Float = 1f,
    /** PAUSED에서 주차 상태(P단 또는 주차브레이크)가 이만큼 유지되면 정차 확정 */
    val parkedHoldMs: Long = 30_000,
)

data class TripState(
    val phase: TripPhase = TripPhase.IDLE,
    /** PAUSED에서 주차 상태가 시작된 시각. 주차가 풀리면 null */
    val parkedSinceMs: Long? = null,
    /** STOPPED: 정차가 확정된 시점의 신호. 도착으로 확정되면 이 시점으로 주행을 닫는다 */
    val stopAt: VehicleSnapshot? = null,
    val stopDecision: StopDecision = StopDecision.UNDECIDED,
)

sealed interface TripEvent {
    data class Signal(val snapshot: VehicleSnapshot) : TripEvent

    /** 운전자가 방문을 기록하겠다고 함 (음성 "응" 또는 화면 버튼). [now]는 그 순간의 차량 신호 */
    data class VisitConfirmed(val now: VehicleSnapshot?) : TripEvent

    /** 운전자가 이번 정차는 방문이 아니라고 함 (음성 "아니" 또는 건너뛰기 버튼) */
    data object VisitDeclined : TripEvent

    /** 방문 질문이 끝남 (응답 저장 또는 무응답 2회) */
    data object VisitPromptFinished : TripEvent
}

sealed interface TripEffect {
    data class StartTrip(val at: VehicleSnapshot) : TripEffect
    data class EndTrip(val at: VehicleSnapshot) : TripEffect

    /** 주차가 확정됨. 경유인지 도착인지 운전자에게 확인한다 */
    data class StopDetected(val at: VehicleSnapshot) : TripEffect
    data object AskVisit : TripEffect

    /** 확인·질문 도중 다시 출발하거나 시동이 꺼져 진행 중인 음성 대화를 접는다 */
    data object CancelVisitPrompt : TripEffect
}

data class Transition(val state: TripState, val effects: List<TripEffect> = emptyList())

/**
 * 순수 함수 상태머신. 시간은 스냅샷의 timestampMs만 쓰므로 Fake 재생 속도와 무관하게 같은 결과가 나온다.
 *
 * 시동 신호를 읽을 수 없으면(ignitionOn == null) 첫 신호를 시동으로 보고, 기어를 읽을 수 없으면 속도만으로 출발을 판단한다.
 * 속도를 읽을 수 없으면(speedKmh == null) D·R단을 주행 중으로 본다. 이때 신호 대기는 DRIVING으로 남고, P단부터 정차로 판단한다.
 */
class TripStateMachine(private val config: TripConfig = TripConfig()) {

    fun reduce(state: TripState, event: TripEvent): Transition = when (event) {
        is TripEvent.Signal -> onSignal(state, event.snapshot)
        is TripEvent.VisitConfirmed -> onConfirmed(state, event.now)
        TripEvent.VisitDeclined ->
            if (state.phase == TripPhase.STOPPED) Transition(state.copy(stopDecision = StopDecision.WAYPOINT)) else Transition(state)
        TripEvent.VisitPromptFinished ->
            if (state.phase == TripPhase.ASKING) Transition(TripState(TripPhase.READY)) else Transition(state)
    }

    private fun onSignal(state: TripState, s: VehicleSnapshot): Transition {
        if (s.ignitionOn == false) return onIgnitionOff(state, s)

        val moving = s.speedKmh?.let { it > config.movingSpeedKmh } ?: (s.gear == Gear.D || s.gear == Gear.R)
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

            // 도착 확인 없이 다시 출발하면 경유 정차였던 것. 같은 주행을 이어 간다
            TripPhase.STOPPED ->
                if (moving) Transition(TripState(TripPhase.DRIVING), listOf(TripEffect.CancelVisitPrompt)) else Transition(state)

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
        if (!isParked(s)) return Transition(TripState(TripPhase.PAUSED))

        val since = parkedSinceMs ?: s.timestampMs
        return if (s.timestampMs - since >= config.parkedHoldMs) {
            Transition(TripState(TripPhase.STOPPED, stopAt = s), listOf(TripEffect.StopDetected(s)))
        } else {
            Transition(TripState(TripPhase.PAUSED, parkedSinceMs = since))
        }
    }

    /**
     * STOPPED: 정차 확정 시점으로 주행을 닫는다.
     * PAUSED: 주차 직후 30초를 기다리지 않고 버튼을 누른 경우. 주차 중일 때만 지금 시점으로 닫는다.
     * READY: 이미 닫힌 직전 주행에 방문을 하나 더 기록한다.
     */
    private fun onConfirmed(state: TripState, now: VehicleSnapshot?): Transition {
        val arrival = when (state.phase) {
            TripPhase.STOPPED -> state.stopAt ?: now
            TripPhase.PAUSED -> now?.takeIf(::isParked)
            TripPhase.READY -> return Transition(TripState(TripPhase.ASKING), listOf(TripEffect.AskVisit))
            else -> null
        } ?: return Transition(state)
        return Transition(TripState(TripPhase.ASKING), listOf(TripEffect.EndTrip(arrival), TripEffect.AskVisit))
    }

    /**
     * 시동이 꺼지면 헤드유닛도 곧 꺼지므로 질문하지 않는다. 방문 내용은 나중에 화면에서 입력.
     * 정차 중(STOPPED)에 시동을 끄면 그곳을 도착지로 보고 정차 확정 시점으로 주행을 닫는다.
     */
    private fun onIgnitionOff(state: TripState, s: VehicleSnapshot): Transition = when (state.phase) {
        TripPhase.IDLE -> Transition(state)
        TripPhase.READY -> Transition(TripState(TripPhase.IDLE))
        TripPhase.DRIVING, TripPhase.PAUSED -> Transition(TripState(TripPhase.IDLE), listOf(TripEffect.EndTrip(s)))
        TripPhase.STOPPED -> Transition(
            TripState(TripPhase.IDLE),
            listOf(TripEffect.CancelVisitPrompt, TripEffect.EndTrip(state.stopAt ?: s)),
        )
        TripPhase.ASKING -> Transition(TripState(TripPhase.IDLE), listOf(TripEffect.CancelVisitPrompt))
    }

    private fun isParked(s: VehicleSnapshot) = s.gear == Gear.P || s.parkingBrakeOn == true
}
