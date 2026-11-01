package com.salesdrive.ui.signal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.data.db.Trip
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun SignalCheckScreen(viewModel: SignalCheckViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val s = ui.recorder.snapshot

    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("신호 확인", style = MaterialTheme.typography.headlineMedium)
            if (s == null) {
                Text("주행거리 신호를 기다리는 중…")
            } else {
                Field("상태", ui.recorder.trip.phase.name)
                Field("주행거리", "%.2f km".format(s.odometerKm))
                Field("속도", s.speedKmh?.let { "%.1f km/h".format(it) } ?: "읽을 수 없음")
                Field("기어", s.gear.name)
                Field("주차브레이크", s.parkingBrakeOn?.toString() ?: "읽을 수 없음")
                Field("시동", s.ignitionOn?.toString() ?: "읽을 수 없음")
                Field("위치", s.location?.let { "%.5f, %.5f".format(it.lat, it.lng) } ?: "-")
                Field("마지막 이벤트", ui.recorder.lastEffect?.let { it::class.simpleName } ?: "-")
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("최근 주행", style = MaterialTheme.typography.headlineMedium)
            if (ui.recentTrips.isEmpty()) Text("아직 기록된 주행이 없습니다")
            ui.recentTrips.forEach { TripRow(it) }
        }
    }
}

@Composable
private fun TripRow(trip: Trip) {
    val end = trip.endedAt?.let(TIME::format) ?: "주행 중"
    val distance = trip.odoEndKm?.let { "%.1f km".format(it - trip.odoStartKm) } ?: "-"
    Text("#${trip.id}  ${TIME.format(trip.startedAt)} → $end  ·  $distance  ·  ${trip.purpose}", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text(label, Modifier.width(160.dp), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
