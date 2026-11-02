package com.salesdrive.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.data.Mileage
import com.salesdrive.data.db.Visit
import com.salesdrive.ui.common.DATE
import com.salesdrive.ui.common.DATE_TIME
import com.salesdrive.ui.common.PurposeButtons
import com.salesdrive.ui.common.SectionCard
import com.salesdrive.ui.common.Stat
import com.salesdrive.ui.common.TIME
import com.salesdrive.ui.common.ZONE
import com.salesdrive.ui.common.km
import java.time.LocalDate

@Composable
fun TripDetailScreen(viewModel: TripDetailViewModel, tripId: Long, onBack: () -> Unit) {
    LaunchedEffect(tripId) { viewModel.show(tripId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Visit?>(null) }
    var adding by remember { mutableStateOf(false) }
    val trip = ui.trip?.takeIf { it.id == tripId }

    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.height(64.dp)) { Text("◀ 목록", style = MaterialTheme.typography.titleLarge) }
            Text(
                trip?.let { "${DATE.format(it.startedAt.atZone(ZONE))} 주행 #${it.id}" } ?: "주행 기록",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        if (trip == null) return@Column

        SectionCard("주행") {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat("출발", TIME.format(trip.startedAt.atZone(ZONE)))
                Stat("도착", trip.endedAt?.let { TIME.format(it.atZone(ZONE)) } ?: "주행 중")
                Stat("계기판", "%.1f → %s".format(trip.odoStartKm, trip.odoEndKm?.let { "%.1f".format(it) } ?: "-"))
                Stat("주행거리", km(Mileage.distanceKm(trip)))
            }
            trip.naviDestination?.let { Text("도착지: $it", style = MaterialTheme.typography.titleMedium) }
            Text("목적", style = MaterialTheme.typography.titleMedium)
            PurposeButtons(trip.purpose, viewModel::setPurpose, Modifier.fillMaxWidth(0.7f))
        }

        if (ui.stops.isNotEmpty()) {
            SectionCard("경유 정차 ${ui.stops.size}회") {
                ui.stops.forEach { s ->
                    val stay = java.time.Duration.between(s.arrivedAt, s.departedAt).toMinutes()
                    Text(
                        "${DATE_TIME.format(s.arrivedAt.atZone(ZONE))}  ${s.name ?: "경유지"}  ·  %.1f km 지점  ·  ${stay}분".format(s.odoKm),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        SectionCard("방문 기록") {
            if (ui.visits.isEmpty()) Text("방문 기록이 없어요", style = MaterialTheme.typography.bodyLarge)
            ui.visits.forEach { v ->
                VisitCard(
                    v,
                    analyzing = v.id in ui.analyzing,
                    onEdit = { editing = v },
                    onReanalyze = { viewModel.reanalyze(v.id) },
                )
            }
            if (trip.endedAt != null) {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.height(64.dp)) {
                    Text("+ 방문 기록 추가", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    editing?.let { v ->
        VisitEditDialog(v, onDismiss = { editing = null }, onSave = {
            viewModel.save(it)
            editing = null
        })
    }
    if (adding) {
        AddVisitDialog(onDismiss = { adding = false }, onSave = {
            viewModel.add(it)
            adding = false
        })
    }
}

@Composable
private fun VisitCard(v: Visit, analyzing: Boolean, onEdit: () -> Unit, onReanalyze: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("\"${v.rawUtterance}\"", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field("거래처", v.client)
        Field("담당자", v.contact)
        Field("요약", v.summary)
        Field("후속", listOfNotNull(v.followUp, v.followUpDate?.let(DATE::format)).joinToString("  ·  ").ifEmpty { null })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onEdit, modifier = Modifier.height(56.dp)) { Text("수정", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = onReanalyze, enabled = !analyzing, modifier = Modifier.height(56.dp)) {
                Text(if (analyzing) "분석 중…" else "다시 분석", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.titleMedium)
        Text(value ?: "-", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun VisitEditDialog(visit: Visit, onDismiss: () -> Unit, onSave: (Visit) -> Unit) {
    var client by remember { mutableStateOf(visit.client.orEmpty()) }
    var contact by remember { mutableStateOf(visit.contact.orEmpty()) }
    var summary by remember { mutableStateOf(visit.summary.orEmpty()) }
    var followUp by remember { mutableStateOf(visit.followUp.orEmpty()) }
    var date by remember { mutableStateOf(visit.followUpDate?.toString().orEmpty()) }
    val parsedDate = date.trim().takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val dateOk = date.isBlank() || parsedDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("방문 기록 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("원문: ${visit.rawUtterance}", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(client, { client = it }, label = { Text("거래처") }, singleLine = true)
                OutlinedTextField(contact, { contact = it }, label = { Text("담당자") }, singleLine = true)
                OutlinedTextField(summary, { summary = it.take(40) }, label = { Text("요약") }, singleLine = true)
                OutlinedTextField(followUp, { followUp = it }, label = { Text("후속 할 일") }, singleLine = true)
                OutlinedTextField(
                    date, { date = it },
                    label = { Text("후속 날짜 (YYYY-MM-DD)") },
                    isError = !dateOk,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = dateOk, onClick = {
                onSave(
                    visit.copy(
                        client = client.trim().ifEmpty { null },
                        contact = contact.trim().ifEmpty { null },
                        summary = summary.trim().ifEmpty { null },
                        followUp = followUp.trim().ifEmpty { null },
                        followUpDate = parsedDate,
                    ),
                )
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AddVisitDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("방문 기록 추가") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("방문 내용") }, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text("예: A상사 김부장 납품 미팅, 견적 다음 주 화요일까지", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
