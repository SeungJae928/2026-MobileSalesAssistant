package com.salesdrive.data.repo

import com.salesdrive.data.CalendarEvent
import com.salesdrive.data.CalendarEvents
import com.salesdrive.data.db.Schedule
import com.salesdrive.data.db.ScheduleDao
import com.salesdrive.data.db.VisitDao
import com.salesdrive.voice.ArrivalBriefer
import com.salesdrive.voice.Briefing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val schedules: ScheduleDao,
    private val visits: VisitDao,
) : ArrivalBriefer {
    override suspend fun briefing(place: String): String? =
        Briefing.arrival(visits.latestAtPlace(place), schedules.firstAtPlace(place, LocalDate.now()))

    suspend fun save(schedule: Schedule): Long = schedules.insert(schedule)

    suspend fun delete(id: Long) = schedules.delete(id)

    suspend fun eventsOn(date: LocalDate): List<CalendarEvent> =
        observeMonth(YearMonth.from(date)).first()[date].orEmpty()

    fun observeUpcoming(from: LocalDate, limit: Int): Flow<List<CalendarEvent>> =
        combine(schedules.observeUpcoming(from, limit), visits.observeUpcoming(from, limit)) { plans, followUps ->
            CalendarEvents.upcoming(plans, followUps, limit)
        }

    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<CalendarEvent>>> {
        val from = month.atDay(1)
        val until = month.atEndOfMonth()
        return combine(schedules.observeBetween(from, until), visits.observeFollowUpsBetween(from, until), CalendarEvents::merge)
    }
}
