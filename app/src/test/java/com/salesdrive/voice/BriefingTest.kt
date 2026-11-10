package com.salesdrive.voice

import com.salesdrive.data.db.ClientVisit
import com.salesdrive.data.db.Schedule
import com.salesdrive.data.db.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BriefingTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 10)

    /** 11월 5일 00:30 KST. UTC로는 11월 4일이므로 시간대를 지키는지 함께 본다 */
    private val visitedAt = Instant.parse("2026-11-04T15:30:00Z")

    private fun last(summary: String? = null, followUp: String? = null, followUpDate: LocalDate? = null) =
        ClientVisit(Visit(tripId = 1, rawUtterance = "r", client = "A상사", summary = summary, followUp = followUp, followUpDate = followUpDate), visitedAt)

    private fun plan(time: LocalTime?) = Schedule(date = today, time = time, title = "제안 미팅", client = "A상사", rawText = "r")

    @Test fun `last visit with follow up`() {
        assertEquals(
            "지난 방문은 11월 5일, 납품 미팅입니다. 후속 조치는 11월 10일, 견적 발송입니다.",
            Briefing.arrival(last("납품 미팅", "견적 발송", today), null, seoul),
        )
    }

    @Test fun `missing fields are left out instead of guessed`() {
        assertEquals("지난 방문은 11월 5일입니다.", Briefing.arrival(last(), null, seoul))
        assertEquals("지난 방문은 11월 5일, 납품입니다. 후속 조치는 회신입니다.", Briefing.arrival(last("납품", "회신"), null, seoul))
        assertEquals("지난 방문은 11월 5일입니다. 후속 일정은 11월 10일입니다.", Briefing.arrival(last(followUpDate = today), null, seoul))
    }

    @Test fun `today's plan is added after the last visit`() {
        assertEquals(
            "지난 방문은 11월 5일입니다. 오늘 오후 3시 제안 미팅 일정이 있습니다.",
            Briefing.arrival(last(), plan(LocalTime.of(15, 0)), seoul),
        )
        assertEquals("오늘 제안 미팅 일정이 있습니다.", Briefing.arrival(null, plan(null), seoul))
    }

    @Test fun `nothing known means no briefing`() {
        assertNull(Briefing.arrival(null, null, seoul))
    }

    @Test fun `spoken time uses a 12 hour clock`() {
        assertEquals("오전 9시", Briefing.spokenTime(LocalTime.of(9, 0)))
        assertEquals("오후 2시 30분", Briefing.spokenTime(LocalTime.of(14, 30)))
        assertEquals("오후 12시", Briefing.spokenTime(LocalTime.of(12, 0)))
        assertEquals("오전 12시 5분", Briefing.spokenTime(LocalTime.of(0, 5)))
    }
}
