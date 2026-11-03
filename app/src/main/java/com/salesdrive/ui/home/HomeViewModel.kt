package com.salesdrive.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesdrive.data.Mileage
import com.salesdrive.data.db.Purpose
import com.salesdrive.data.db.TripSummary
import com.salesdrive.data.db.Visit
import com.salesdrive.data.repo.ProfileRepository
import com.salesdrive.data.repo.TripRepository
import com.salesdrive.data.repo.needsSetup
import com.salesdrive.data.repo.VisitRepository
import com.salesdrive.trip.RecorderState
import com.salesdrive.trip.TripRecorder
import com.salesdrive.ui.common.ZONE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUi(
    val recorder: RecorderState = RecorderState(),
    val nextPurpose: Purpose? = null,
    val today: List<TripSummary> = emptyList(),
    val month: Mileage.Totals = Mileage.Totals(),
    val upcoming: List<Visit> = emptyList(),
    val needsProfile: Boolean = false,
) {
    val todayKm: Double get() = Mileage.totals(today.map { it.trip }).totalKm
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recorder: TripRecorder,
    private val trips: TripRepository,
    visits: VisitRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val date = LocalDate.now(ZONE)

    private val base = combine(
        recorder.state,
        trips.nextPurpose,
        trips.observeBetween(date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant()),
        trips.observeBetween(date.withDayOfMonth(1).atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant()),
        visits.observeUpcoming(date, limit = 5),
    ) { state, purpose, today, month, upcoming ->
        HomeUi(state, purpose, today, Mileage.totals(month.map { it.trip }), upcoming)
    }

    val ui: StateFlow<HomeUi> = combine(base, profiles.observe()) { ui, me ->
        ui.copy(needsProfile = me.needsSetup)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun choosePurpose(purpose: Purpose) {
        trips.chooseNextPurpose(purpose.takeUnless { it == ui.value.nextPurpose })
    }

    fun recordVisit() {
        viewModelScope.launch { recorder.confirmVisit() }
    }

    fun skipVisit() {
        viewModelScope.launch { recorder.declineVisit() }
    }
}
