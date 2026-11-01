package com.salesdrive.ui.signal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.trip.TripEvent
import com.salesdrive.trip.TripState
import com.salesdrive.trip.TripStateMachine
import com.salesdrive.vehicle.VehicleSignalSource
import com.salesdrive.vehicle.VehicleSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignalCheckUi(
    val snapshot: VehicleSnapshot? = null,
    val trip: TripState = TripState(),
    val lastEffect: String? = null,
)

/** 개발용: 들어오는 차량 신호와 상태머신 판단을 그대로 보여준다. 기록은 하지 않는다 */
@HiltViewModel
class SignalCheckViewModel @Inject constructor(
    private val source: VehicleSignalSource,
) : ViewModel() {

    private val machine = TripStateMachine()
    private val _ui = MutableStateFlow(SignalCheckUi())
    val ui: StateFlow<SignalCheckUi> = _ui.asStateFlow()
    private var job: Job? = null

    /** 권한 요청 결과가 나온 뒤 호출. 다시 부르면 구독을 새로 시작해 새 권한을 반영한다 */
    fun start() {
        job?.cancel()
        _ui.value = SignalCheckUi()
        job = viewModelScope.launch {
            source.snapshots.collect { s ->
                val tr = machine.reduce(_ui.value.trip, TripEvent.Signal(s))
                if (tr.state.phase != _ui.value.trip.phase || tr.effects.isNotEmpty()) {
                    Log.i(TAG, "${_ui.value.trip.phase} -> ${tr.state.phase} ${tr.effects}")
                }
                _ui.value = SignalCheckUi(
                    snapshot = s,
                    trip = tr.state,
                    lastEffect = tr.effects.lastOrNull()?.toString() ?: _ui.value.lastEffect,
                )
                Log.d(TAG, s.toString())
            }
        }
    }

    private companion object {
        const val TAG = "SignalCheck"
    }
}
