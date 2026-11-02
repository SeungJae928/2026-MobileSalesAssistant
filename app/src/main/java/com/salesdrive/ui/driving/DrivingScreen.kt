package com.salesdrive.ui.driving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.TripPhase
import com.salesdrive.trip.TripRecorder
import com.salesdrive.vehicle.DrivingRestriction
import com.salesdrive.vehicle.Gear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * 주행 중 화면 제한 여부. 차량이 제한을 요구하거나, 기록기가 주행 중으로 보면 true.
 * 신호 대기(PAUSED)에서도 기어가 P가 아니면 제한을 유지해 화면이 깜빡이지 않게 한다.
 */
fun isRestricted(uxRequired: Boolean, state: RecorderState): Boolean {
    val parked = state.snapshot?.let { it.gear == Gear.P || it.parkingBrakeOn == true } == true
    return uxRequired || state.trip.phase == TripPhase.DRIVING || (state.trip.phase == TripPhase.PAUSED && !parked)
}

@HiltViewModel
class DrivingViewModel @Inject constructor(
    restriction: DrivingRestriction,
    recorder: TripRecorder,
) : ViewModel() {
    val restricted: Flow<Boolean> =
        combine(restriction.requiresDistractionOptimization, recorder.state, ::isRestricted).distinctUntilChanged()
}

/** 주행 중 유일하게 보이는 화면. 움직이는 요소 없이 아이콘과 한 줄만 */
@Composable
fun DrivingScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🚗", style = MaterialTheme.typography.displayLarge)
            Text("주행 기록 중", style = MaterialTheme.typography.displayMedium)
            Text(
                "도착하면 음성으로 방문 내용을 여쭤볼게요",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
