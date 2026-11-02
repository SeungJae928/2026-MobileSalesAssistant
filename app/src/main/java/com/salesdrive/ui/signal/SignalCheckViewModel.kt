package com.salesdrive.ui.signal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.db.TripSummary
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.TripRecorder
import com.salesdrive.vehicle.fake.DemoSwitchSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignalCheckUi(
    val recorder: RecorderState = RecorderState(),
    val recentTrips: List<TripSummary> = emptyList(),
    val demoRunning: Boolean = false,
)

/** 개발용: 기록 서비스가 보고 있는 신호·상태와 저장된 최근 주행을 보여준다. 방문 기록 여부도 여기서 정한다 */
@HiltViewModel
class SignalCheckViewModel @Inject constructor(
    private val recorder: TripRecorder,
    trips: TripRepository,
    private val demo: DemoSwitchSource,
) : ViewModel() {

    val ui: StateFlow<SignalCheckUi> =
        combine(recorder.state, trips.observeRecent(limit = 5), demo.running, ::SignalCheckUi)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalCheckUi())

    fun recordVisit() {
        viewModelScope.launch { recorder.confirmVisit() }
    }

    /** 시연 영상용: 출발 → 약 30km → 거래처 도착 → 주차를 실제 시간 약 1분으로 재생 */
    fun toggleDemo() {
        if (demo.running.value) demo.stop() else demo.start()
    }

    fun skipVisit() {
        viewModelScope.launch { recorder.declineVisit() }
    }
}
