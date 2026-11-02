package com.salesdrive.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.Trip
import com.salesdrive.data.db.TripStop
import com.salesdrive.data.db.Visit
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.data.repo.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripDetailUi(
    val trip: Trip? = null,
    val stops: List<TripStop> = emptyList(),
    val visits: List<Visit> = emptyList(),
    /** 다시 분석 중인 방문 id */
    val analyzing: Set<Long> = emptySet(),
)

/** 방문 기록 확인·수정: 인식된 거래처·요약·후속 일정을 정차 중에 사람이 고친다 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val trips: TripRepository,
    private val visits: VisitRepository,
) : ViewModel() {

    private val tripId = MutableStateFlow<Long?>(null)
    private val analyzing = MutableStateFlow<Set<Long>>(emptySet())

    val ui: StateFlow<TripDetailUi> = tripId.filterNotNull().flatMapLatest { id ->
        combine(trips.observeTrip(id), trips.observeStops(id), visits.observeByTrip(id), analyzing, ::TripDetailUi)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUi())

    fun show(id: Long) {
        tripId.value = id
    }

    fun setPurpose(purpose: Purpose) {
        val id = tripId.value ?: return
        viewModelScope.launch { trips.setPurpose(id, purpose) }
    }

    fun save(visit: Visit) {
        viewModelScope.launch { visits.update(visit) }
    }

    /** 나중에 입력: 음성으로 답하지 못한 방문을 화면에서 적는다. 저장하면 파서가 항목을 채운다 */
    fun add(text: String) {
        val id = tripId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch { visits.saveRaw(id, text.trim()) }
    }

    fun reanalyze(visitId: Long) {
        analyzing.update { it + visitId }
        viewModelScope.launch {
            try {
                visits.organize(visitId)
            } finally {
                analyzing.update { it - visitId }
            }
        }
    }
}
