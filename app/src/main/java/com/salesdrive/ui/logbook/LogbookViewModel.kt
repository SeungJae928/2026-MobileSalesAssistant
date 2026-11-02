package com.salesdrive.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.Mileage
import com.salesdrive.data.db.TripSummary
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.export.LogbookExporter
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
import java.time.YearMonth
import javax.inject.Inject

data class LogbookUi(
    val month: YearMonth = YearMonth.now(ZONE),
    val trips: List<TripSummary> = emptyList(),
    val exporting: Boolean = false,
    /** 내보내기 결과 안내 문구 */
    val message: String? = null,
) {
    val totals: Mileage.Totals get() = Mileage.totals(trips.map { it.trip })
    val isCurrentMonth: Boolean get() = month >= YearMonth.now(ZONE)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogbookViewModel @Inject constructor(
    trips: TripRepository,
    private val exporter: LogbookExporter,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now(ZONE))
    private val export = MutableStateFlow(false to (null as String?))

    val ui: StateFlow<LogbookUi> = combine(
        month.flatMapLatest { m ->
            trips.observeBetween(m.atDay(1).atStartOfDay(ZONE).toInstant(), m.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant())
                .map { LogbookUi(m, it) }
        },
        export,
    ) { ui, (busy, message) -> ui.copy(exporting = busy, message = message) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookUi())

    fun exportCsv() {
        val m = month.value
        export.value = true to null
        viewModelScope.launch {
            val message = runCatching { exporter.export(m, ZONE) }
                .fold({ "저장했어요: $it" }, { "저장하지 못했어요: ${it.message}" })
            export.value = false to message
        }
    }

    fun previousMonth() {
        export.value = false to null
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        export.value = false to null
        if (!ui.value.isCurrentMonth) month.value = month.value.plusMonths(1)
    }
}
