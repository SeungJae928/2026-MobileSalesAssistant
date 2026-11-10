package com.salesdrive.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salesdrive.data.db.Purpose
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.StopDecision
import com.salesdrive.trip.TripPhase
import com.salesdrive.vehicle.Gear
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val ZONE: ZoneId get() = ZoneId.systemDefault()
val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)")

val Purpose.label: String
    get() = when (this) {
        Purpose.BUSINESS -> "업무"
        Purpose.COMMUTE -> "출퇴근"
        Purpose.PERSONAL -> "개인"
    }

fun km(value: Double): String = "%.1f km".format(value)

val RecorderState.phaseLabel: String
    get() = when (trip.phase) {
        TripPhase.IDLE -> "시동 꺼짐"
        TripPhase.READY -> "출발 대기"
        TripPhase.DRIVING -> "주행 기록 중"
        TripPhase.PAUSED -> "잠시 정차"
        TripPhase.STOPPED ->
            if (trip.stopDecision == StopDecision.WAYPOINT) "정차 중 · 경유지로 표시됨" else "정차 중 · 방문 기록 여부를 정해 주세요"
        TripPhase.ASKING -> "방문 내용 듣는 중"
    }

private val RecorderState.parkedNow: Boolean
    get() = snapshot?.let { it.gear == Gear.P || it.parkingBrakeOn == true } == true

/** 주행 중에는 숨긴다. 정차 확정 전이라도 주차했다면 바로 누를 수 있다 */
val RecorderState.canRecordVisit: Boolean
    get() = trip.phase == TripPhase.STOPPED || trip.phase == TripPhase.READY ||
        (trip.phase == TripPhase.PAUSED && parkedNow)

/** 아직 정하지 않은 정차에서만 */
val RecorderState.canSkipVisit: Boolean
    get() = trip.phase == TripPhase.STOPPED && trip.stopDecision == StopDecision.UNDECIDED

/** 출발 전(시동 켜고 정차)에만 목적을 고른다. 주행이 시작되면 자동으로 사라진다 */
val RecorderState.canChoosePurpose: Boolean
    get() = trip.phase == TripPhase.READY

/** 음성 확인에 답하지 못했을 때, 또는 자동으로 묻지 않은 곳에서 직접 방문을 기록할 때 쓴다 */
@Composable
fun VisitActions(state: RecorderState, onRecord: () -> Unit, onSkip: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onRecord, enabled = state.canRecordVisit, modifier = Modifier.height(72.dp)) {
            Text("방문 기록", style = MaterialTheme.typography.titleLarge)
        }
        if (state.canSkipVisit) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.height(72.dp)) {
                Text("건너뛰기 (경유)", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** 업무 / 출퇴근 / 개인 큰 버튼 3개. [selected]는 강조 */
@Composable
fun PurposeButtons(selected: Purpose?, onSelect: (Purpose) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Purpose.entries.forEach { p ->
            val colors = if (p == selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
            val border = if (p == selected) null else ButtonDefaults.outlinedButtonBorder(enabled = true)
            Button(
                onClick = { onSelect(p) },
                colors = colors,
                border = border,
                modifier = Modifier.weight(1f).height(88.dp),
            ) {
                Text(p.label, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall)
    }
}
