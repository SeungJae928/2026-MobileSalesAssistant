package com.salesdrive.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.ui.common.SectionCard
import com.salesdrive.ui.common.Stat
import com.salesdrive.ui.common.TripRow
import com.salesdrive.ui.common.km

/** 월별 운행기록부와 CSV 내보내기 */
@Composable
fun LogbookScreen(viewModel: LogbookViewModel, onOpenTrip: (Long) -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedButton(onClick = viewModel::previousMonth, modifier = Modifier.height(64.dp)) {
                Text("◀", style = MaterialTheme.typography.titleLarge)
            }
            Text("${ui.month.year}년 ${ui.month.monthValue}월 운행기록부", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = viewModel::nextMonth, enabled = !ui.isCurrentMonth, modifier = Modifier.height(64.dp)) {
                Text("▶", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = viewModel::exportCsv,
                enabled = !ui.exporting && ui.trips.any { it.trip.endedAt != null },
                modifier = Modifier.height(64.dp),
            ) {
                Text(if (ui.exporting) "저장 중…" else "CSV 내보내기", style = MaterialTheme.typography.titleLarge)
            }
        }
        ui.message?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        SectionCard("${ui.trips.count { it.trip.endedAt != null }}회 주행") {
            val t = ui.totals
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat("총 주행", km(t.totalKm))
                Stat("일반업무", km(t.businessKm))
                Stat("출퇴근", km(t.commuteKm))
                Stat("개인", km(t.personalKm))
                Stat("업무사용비율", t.businessRatio?.let { "%.0f%%".format(it * 100) } ?: "-")
            }
        }
        if (ui.trips.isEmpty()) Text("이 달에 기록된 주행이 없어요", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.weight(1f)) {
            items(ui.trips, key = { it.trip.id }) { TripRow(it) { onOpenTrip(it.trip.id) } }
        }
    }
}
