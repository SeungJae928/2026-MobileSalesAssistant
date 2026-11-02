package com.salesdrive.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.Mileage
import com.salesdrive.data.db.TripSummary
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.ui.common.ZONE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class LogbookUi(
    val month: YearMonth = YearMonth.now(ZONE),
    val trips: List<TripSummary> = emptyList(),
) {
    val totals: Mileage.Totals get() = Mileage.totals(trips.map { it.trip })
    val isCurrentMonth: Boolean get() = month >= YearMonth.now(ZONE)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogbookViewModel @Inject constructor(
    trips: TripRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now(ZONE))

    val ui: StateFlow<LogbookUi> = month.flatMapLatest { m ->
        trips.observeBetween(m.atDay(1).atStartOfDay(ZONE).toInstant(), m.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant())
            .map { LogbookUi(m, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookUi())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (!ui.value.isCurrentMonth) month.value = month.value.plusMonths(1)
    }
}
