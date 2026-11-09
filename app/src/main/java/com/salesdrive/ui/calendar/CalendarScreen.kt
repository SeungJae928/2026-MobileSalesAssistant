package com.salesdrive.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salesdrive.data.CalendarEvent
import com.salesdrive.data.CalendarEvents
import com.salesdrive.ui.common.DATE
import com.salesdrive.ui.common.TIME
import com.salesdrive.ui.common.ZONE
import java.time.DayOfWeek
import java.time.LocalDate

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")
private const val MAX_EVENTS_IN_CELL = 2

private val CalendarEvent.Kind.label: String
    get() = when (this) {
        CalendarEvent.Kind.SCHEDULE -> "일정"
        CalendarEvent.Kind.FOLLOW_UP -> "후속 조치"
    }

private val CalendarEvent.Kind.container: Color
    @Composable get() = when (this) {
        CalendarEvent.Kind.SCHEDULE -> MaterialTheme.colorScheme.primaryContainer
        CalendarEvent.Kind.FOLLOW_UP -> MaterialTheme.colorScheme.tertiaryContainer
    }

private val CalendarEvent.Kind.onContainer: Color
    @Composable get() = when (this) {
        CalendarEvent.Kind.SCHEDULE -> MaterialTheme.colorScheme.onPrimaryContainer
        CalendarEvent.Kind.FOLLOW_UP -> MaterialTheme.colorScheme.onTertiaryContainer
    }

/** 왼쪽에 달력, 오른쪽에 고른 날짜의 내용. 일정이 있는 날짜만 누를 수 있다 */
@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<CalendarEvent?>(null) }

    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(ui, onMove = viewModel::moveMonth, onToday = viewModel::showThisMonth)
            MonthGrid(ui, onSelect = viewModel::select, modifier = Modifier.weight(1f))
        }
        DayDetail(ui, onDelete = { deleting = it }, modifier = Modifier.weight(1f).fillMaxHeight())
    }

    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("일정을 지울까요?") },
            text = { Text(event.title, style = MaterialTheme.typography.titleMedium) },
            confirmButton = {
                TextButton(onClick = {
                    event.scheduleId?.let(viewModel::delete)
                    deleting = null
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun Header(ui: CalendarUi, onMove: (Long) -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { onMove(-1) }, modifier = Modifier.height(64.dp)) { Text("◀", style = MaterialTheme.typography.titleLarge) }
        Text("${ui.month.year}년 ${ui.month.monthValue}월", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { onMove(1) }, modifier = Modifier.height(64.dp)) { Text("▶", style = MaterialTheme.typography.titleLarge) }
        OutlinedButton(onClick = onToday, modifier = Modifier.height(64.dp)) { Text("이번 달", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.weight(1f))
        CalendarEvent.Kind.entries.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(16.dp).background(kind.container, CircleShape))
                Text(kind.label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun MonthGrid(ui: CalendarUi, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now(ZONE)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WEEKDAYS.forEachIndexed { i, day ->
                Text(
                    day,
                    Modifier.weight(1f).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = weekendColor(i) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CalendarEvents.weeks(ui.month).forEach { week ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date -> DayCell(date, ui, today, onSelect) }
            }
        }
    }
}

@Composable
private fun weekendColor(columnFromSunday: Int): Color? = when (columnFromSunday) {
    0 -> MaterialTheme.colorScheme.error
    6 -> MaterialTheme.colorScheme.primary
    else -> null
}

@Composable
private fun RowScope.DayCell(date: LocalDate?, ui: CalendarUi, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    if (date == null) {
        Spacer(Modifier.weight(1f))
        return
    }
    val events = ui.events[date].orEmpty()
    val background =
        if (date == ui.selected && events.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(if (events.isNotEmpty()) Modifier.clickable { onSelect(date) } else Modifier)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayNumber(date, isToday = date == today)
        events.take(MAX_EVENTS_IN_CELL).forEach { event ->
            Text(
                event.title,
                Modifier.fillMaxWidth().background(event.kind.container, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = event.kind.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (events.size > MAX_EVENTS_IN_CELL) {
            Text("+${events.size - MAX_EVENTS_IN_CELL}개 더", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DayNumber(date: LocalDate, isToday: Boolean) {
    val weekend = weekendColor(date.dayOfWeek.value % 7).takeIf { date.dayOfWeek in setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY) }
    Box(
        Modifier.size(36.dp).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.onPrimary else weekend ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DayDetail(ui: CalendarUi, onDelete: (CalendarEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val date = ui.selected
        if (date == null || ui.selectedEvents.isEmpty()) {
            Text(
                "일정이 있는 날짜를 누르면 내용이 여기에 표시돼요",
                Modifier.padding(top = 96.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(DATE.format(date), style = MaterialTheme.typography.headlineMedium)
        ui.selectedEvents.forEach { event -> EventCard(event, onDelete) }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, onDelete: (CalendarEvent) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    event.kind.label,
                    Modifier.background(event.kind.container, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = event.kind.onContainer,
                )
                Text(event.time?.let(TIME::format) ?: "시간 미정", style = MaterialTheme.typography.titleLarge)
            }
            Text(event.title, style = MaterialTheme.typography.headlineSmall)
            Line("거래처", event.client)
            Line("담당자", event.contact)
            Line(if (event.kind == CalendarEvent.Kind.SCHEDULE) "받은 내용" else "방문 요약", event.note)
            if (event.scheduleId != null) {
                OutlinedButton(onClick = { onDelete(event) }, modifier = Modifier.height(56.dp)) {
                    Text("지우기", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String?) {
    if (value == null) return
    Text("$label: $value", style = MaterialTheme.typography.titleMedium)
}
