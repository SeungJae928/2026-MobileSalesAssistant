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

@Composable
fun SignalCheckScreen(viewModel: SignalCheckViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val s = ui.snapshot

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("신호 확인", style = MaterialTheme.typography.headlineMedium)
        if (s == null) {
            Text("주행거리 신호를 기다리는 중…")
            return@Column
        }
        Field("상태", ui.trip.phase.name)
        Field("주행거리", "%.2f km".format(s.odometerKm))
        Field("속도", s.speedKmh?.let { "%.1f km/h".format(it) } ?: "읽을 수 없음")
        Field("기어", s.gear.name)
        Field("주차브레이크", s.parkingBrakeOn?.toString() ?: "읽을 수 없음")
        Field("시동", s.ignitionOn?.toString() ?: "읽을 수 없음")
        Field("위치", s.location?.let { "%.5f, %.5f".format(it.lat, it.lng) } ?: "-")
        Field("마지막 이벤트", ui.lastEffect ?: "-")
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text(label, Modifier.width(160.dp), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
