package com.salesdrive.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.CalendarEvent
import com.salesdrive.data.repo.ScheduleRepository
import com.salesdrive.ui.common.ZONE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUi(
    val month: YearMonth = YearMonth.now(ZONE),
    val events: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val selected: LocalDate? = null,
) {
    val selectedEvents: List<CalendarEvent> get() = selected?.let { events[it] }.orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val schedules: ScheduleRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now(ZONE))
    private val selected = MutableStateFlow<LocalDate?>(null)

    private val monthEvents = month.flatMapLatest { m -> schedules.observeMonth(m).map { m to it } }

    val ui: StateFlow<CalendarUi> = combine(monthEvents, selected) { (m, events), day -> CalendarUi(m, events, day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUi())

    fun moveMonth(by: Long) = show(month.value.plusMonths(by))

    fun showThisMonth() = show(YearMonth.now(ZONE))

    private fun show(target: YearMonth) {
        month.value = target
        selected.value = null
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    fun delete(scheduleId: Long) {
        viewModelScope.launch { schedules.delete(scheduleId) }
    }
}
