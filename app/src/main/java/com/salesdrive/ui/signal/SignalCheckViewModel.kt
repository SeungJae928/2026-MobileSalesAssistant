package com.salesdrive.ui.signal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.db.Trip
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.StopDecision
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripRecorder
import com.salesdrive.vehicle.Gear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignalCheckUi(
    val recorder: RecorderState = RecorderState(),
    val recentTrips: List<Trip> = emptyList(),
) {
    private val phase get() = recorder.trip.phase
    private val parkedNow get() = recorder.snapshot?.let { it.gear == Gear.P || it.parkingBrakeOn == true } == true

    /** 주행 중에는 숨긴다. 정차 확정 전이라도 주차했다면 바로 누를 수 있다 */
    val canRecordVisit: Boolean
        get() = phase == TripPhase.STOPPED || phase == TripPhase.READY || (phase == TripPhase.PAUSED && parkedNow)

    /** 아직 정하지 않은 정차에서만 */
    val canSkipVisit: Boolean
        get() = phase == TripPhase.STOPPED && recorder.trip.stopDecision == StopDecision.UNDECIDED
}

/** 개발용: 기록 서비스가 보고 있는 신호·상태와 저장된 최근 주행을 보여준다. 방문 기록 여부도 여기서 정한다 */
@HiltViewModel
class SignalCheckViewModel @Inject constructor(
    private val recorder: TripRecorder,
    trips: TripRepository,
) : ViewModel() {

    val ui: StateFlow<SignalCheckUi> =
        combine(recorder.state, trips.observeRecent(limit = 5), ::SignalCheckUi)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalCheckUi())

    fun recordVisit() {
        viewModelScope.launch { recorder.confirmVisit() }
    }

    fun skipVisit() {
        viewModelScope.launch { recorder.declineVisit() }
    }
}
