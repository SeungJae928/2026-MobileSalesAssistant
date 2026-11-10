package com.salesdrive.voice

import com.salesdrive.data.db.ClientVisit
import com.salesdrive.data.db.Schedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 운전자에게 읽어 줄 안내 문장. 주어진 값만으로 만들어서 없는 내용을 지어내지 않는다 */
internal object Briefing {

    /** 거래처에 도착했을 때 지난 방문과 오늘 일정. 알려 줄 내용이 없으면 null */
    fun arrival(last: ClientVisit?, plan: Schedule?, zone: ZoneId = ZoneId.systemDefault()): String? = buildList {
        last?.let { (visit, visitedAt) ->
            val date = spokenDate(visitedAt.atZone(zone).toLocalDate())
            add(if (visit.summary != null) "지난 방문은 $date, ${visit.summary}입니다." else "지난 방문은 ${date}입니다.")
            val followUpDate = visit.followUpDate?.let(::spokenDate)
            when {
                visit.followUp != null && followUpDate != null -> add("후속 조치는 $followUpDate, ${visit.followUp}입니다.")
                visit.followUp != null -> add("후속 조치는 ${visit.followUp}입니다.")
                followUpDate != null -> add("후속 일정은 ${followUpDate}입니다.")
            }
        }
        plan?.let { add("오늘 ${it.time?.let { t -> spokenTime(t) + " " }.orEmpty()}${it.title} 일정이 있습니다.") }
    }.joinToString(" ").ifEmpty { null }

    fun spokenDate(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

    fun spokenTime(time: LocalTime): String {
        val hour = time.hour % 12
        val clock = "${if (hour == 0) 12 else hour}시"
        val minute = if (time.minute > 0) " ${time.minute}분" else ""
        return "${if (time.hour < 12) "오전" else "오후"} $clock$minute"
    }
}
