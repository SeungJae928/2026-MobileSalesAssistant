package com.salesdrive.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.ui.common.DATE
import com.salesdrive.ui.common.PurposeButtons
import com.salesdrive.ui.common.SectionCard
import com.salesdrive.ui.common.Stat
import com.salesdrive.ui.common.TripRow
import com.salesdrive.ui.common.VisitActions
import com.salesdrive.ui.common.canChoosePurpose
import com.salesdrive.ui.common.km
import com.salesdrive.ui.common.phaseLabel

/** 정차 중 첫 화면: 지금 상태, 출발 목적, 오늘 주행 km, 이번 달 업무 비율, 후속 일정, 오늘 주행 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenTrip: (Long) -> Unit, onOpenProfile: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (ui.needsProfile) {
                SectionCard("내 정보를 입력해 주세요") {
                    Text("운행기록부에 들어갈 성명·부서와 시트 메모리 번호를 정해요", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onOpenProfile) { Text("내 정보 입력", style = MaterialTheme.typography.titleMedium) }
                }
            }
            SectionCard(ui.recorder.phaseLabel) {
                if (ui.recorder.canChoosePurpose) {
                    Text("이번 주행 목적", style = MaterialTheme.typography.titleMedium)
                    PurposeButtons(ui.nextPurpose, viewModel::choosePurpose)
                    Text(
                        if (ui.nextPurpose == null) "고르지 않으면 업무로 기록하고, 도착 후 음성으로 바로잡을 수 있어요" else "출발하면 이 목적으로 기록해요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VisitActions(ui.recorder, onRecord = viewModel::recordVisit, onSkip = viewModel::skipVisit)
            }
            SectionCard("요약") {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Stat("오늘 주행", km(ui.todayKm))
                    Stat("이번 달 주행", km(ui.month.totalKm))
                    Stat("이번 달 업무 비율", ui.month.businessRatio?.let { "%.0f%%".format(it * 100) } ?: "-")
                }
            }
            SectionCard("다가오는 후속 일정") {
                if (ui.upcoming.isEmpty()) Text("예정된 후속 일정이 없어요", style = MaterialTheme.typography.bodyLarge)
                ui.upcoming.forEach { v ->
                    val who = listOfNotNull(v.client, v.contact).joinToString(" ")
                    Text(
                        "${v.followUpDate?.let(DATE::format)}  ${v.followUp ?: v.summary ?: "후속 일정"}${if (who.isNotEmpty()) "  ($who)" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Column(Modifier.weight(1.3f)) {
            Text("오늘 주행", style = MaterialTheme.typography.headlineSmall)
            if (ui.today.isEmpty()) {
                Text("오늘 기록된 주행이 없어요", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn {
                items(ui.today, key = { it.trip.id }) { TripRow(it) { onOpenTrip(it.trip.id) } }
            }
        }
    }
}
