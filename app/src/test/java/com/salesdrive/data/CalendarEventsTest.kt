package com.salesdrive.data

import com.salesdrive.data.db.Schedule
import com.salesdrive.data.db.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalendarEventsTest {

    private val day = LocalDate.of(2026, 11, 10)

    @Test fun `weeks start on sunday and blanks fill the edges`() {
        val weeks = CalendarEvents.weeks(YearMonth.of(2026, 10)) // 10월 1일은 목요일
        assertEquals(5, weeks.size)
        assertEquals(listOf(null, null, null, null), weeks[0].take(4))
        assertEquals(LocalDate.of(2026, 10, 1), weeks[0][4])
        assertEquals(LocalDate.of(2026, 10, 31), weeks[4][6])
        weeks.forEach { assertEquals(7, it.size) }
    }

    @Test fun `month that fits exactly has no blank week`() {
        val weeks = CalendarEvents.weeks(YearMonth.of(2026, 2)) // 2월 1일이 일요일, 28일
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2026, 2, 28), weeks[3][6])
    }

    @Test fun `events are grouped by day with timed plans first`() {
        val events = CalendarEvents.merge(
            listOf(
                Schedule(id = 1, date = day, time = LocalTime.of(15, 0), title = "오후 미팅", rawText = "r"),
                Schedule(id = 2, date = day, time = LocalTime.of(9, 0), title = "오전 미팅", rawText = "r"),
                Schedule(id = 3, date = day.plusDays(1), title = "다음 날", rawText = "r"),
            ),
            listOf(Visit(tripId = 1, rawUtterance = "r", client = "A상사", summary = "납품", followUp = "견적 발송", followUpDate = day)),
        )
        assertEquals(listOf("오전 미팅", "오후 미팅", "견적 발송"), events.getValue(day).map { it.title })
        assertEquals(listOf("다음 날"), events.getValue(day.plusDays(1)).map { it.title })
    }

    @Test fun `follow up keeps visit details and cannot be deleted here`() {
        val event = CalendarEvents.merge(
            emptyList(),
            listOf(Visit(tripId = 1, rawUtterance = "r", client = "A상사", contact = "김부장", summary = "납품", followUpDate = day)),
        ).getValue(day).single()
        assertEquals(CalendarEvent.Kind.FOLLOW_UP, event.kind)
        assertEquals("후속 일정", event.title)
        assertEquals("납품", event.note)
        assertNull(event.scheduleId)
    }
}
