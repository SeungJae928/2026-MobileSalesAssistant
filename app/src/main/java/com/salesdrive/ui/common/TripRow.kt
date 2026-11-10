package com.salesdrive.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salesdrive.data.Mileage
import com.salesdrive.data.db.TripSummary

/** 기록 목록 한 줄: 시각, 거리, 목적, 도착지·거래처, 방문 요약 */
@Composable
fun TripRow(summary: TripSummary, onClick: () -> Unit) {
    val trip = summary.trip
    val start = trip.startedAt.atZone(ZONE)
    val end = trip.endedAt?.atZone(ZONE)?.let(TIME::format) ?: "주행 중"
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.width(220.dp)) {
                Text(DATE.format(start), style = MaterialTheme.typography.titleMedium)
                Text("${TIME.format(start)} → $end", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                if (trip.endedAt != null) km(Mileage.distanceKm(trip)) else "-",
                Modifier.width(140.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            AssistChip(onClick = onClick, label = { Text(trip.purpose.label, style = MaterialTheme.typography.titleMedium) })
            Column(Modifier.weight(1f)) {
                val place = listOfNotNull(summary.client, trip.naviDestination).distinct().joinToString(" · ")
                    .ifEmpty { if (trip.endedAt == null) "기록 중" else "도착지 미상" }
                Text(place, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val note = buildList {
                    summary.visitNote?.let { add(if (summary.visitCount > 1) "$it 외 ${summary.visitCount - 1}건" else it) }
                    if (summary.stopCount > 0) add("경유 ${summary.stopCount}회")
                }.joinToString("  ·  ").ifEmpty { if (trip.endedAt != null) "방문 기록 없음" else "" }
                Text(
                    note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("#${trip.id}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        HorizontalDivider()
    }
}
