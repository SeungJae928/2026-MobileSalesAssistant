package com.salesdrive.data

import com.salesdrive.data.db.Schedule
import com.salesdrive.data.db.Visit
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 캘린더 한 칸에 들어가는 항목. 직접 등록한 일정과 방문에서 나온 후속 조치를 같은 모양으로 보여 준다 */
data class CalendarEvent(
    val date: LocalDate,
    val time: LocalTime? = null,
    val kind: Kind,
    val title: String,
    val client: String? = null,
    val contact: String? = null,
    /** 일정은 받은 표현, 후속 조치는 방문 요약 */
    val note: String? = null,
    /** 일정만 있다. 후속 조치는 방문 기록에서 고친다 */
    val scheduleId: Long? = null,
) {
    enum class Kind { SCHEDULE, FOLLOW_UP }
}

object CalendarEvents {

    /** 날짜별로 묶는다. 같은 날은 시간순이고 시간이 없는 항목은 뒤로 간다 */
    fun merge(schedules: List<Schedule>, followUps: List<Visit>): Map<LocalDate, List<CalendarEvent>> {
        val fromSchedules = schedules.map {
            CalendarEvent(it.date, it.time, CalendarEvent.Kind.SCHEDULE, it.title, it.client, it.contact, it.rawText, it.id)
        }
        val fromVisits = followUps.mapNotNull { v ->
            val date = v.followUpDate ?: return@mapNotNull null
            CalendarEvent(date, null, CalendarEvent.Kind.FOLLOW_UP, v.followUp ?: "후속 일정", v.client, v.contact, v.summary)
        }
        return (fromSchedules + fromVisits)
            .sortedWith(compareBy(nullsLast<LocalTime>()) { it.time })
            .groupBy { it.date }
    }

    /** 일요일로 시작하는 주 단위 칸. 그 달이 아닌 칸은 null */
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val blanks = month.atDay(1).dayOfWeek.value % 7
        val days = List<LocalDate?>(blanks) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        return days.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }
}
