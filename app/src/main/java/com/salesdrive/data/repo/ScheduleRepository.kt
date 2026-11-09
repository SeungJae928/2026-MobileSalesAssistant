package com.salesdrive.data.repo

import com.salesdrive.data.CalendarEvent
import com.salesdrive.data.CalendarEvents
import com.salesdrive.data.db.Schedule
import com.salesdrive.data.db.ScheduleDao
import com.salesdrive.data.db.VisitDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val schedules: ScheduleDao,
    private val visits: VisitDao,
) {
    suspend fun save(schedule: Schedule): Long = schedules.insert(schedule)

    suspend fun delete(id: Long) = schedules.delete(id)

    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, List<CalendarEvent>>> {
        val from = month.atDay(1)
        val until = month.atEndOfMonth()
        return combine(schedules.observeBetween(from, until), visits.observeFollowUpsBetween(from, until), CalendarEvents::merge)
    }
}
