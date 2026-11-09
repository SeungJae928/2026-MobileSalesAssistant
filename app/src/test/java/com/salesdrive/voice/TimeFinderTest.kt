package com.salesdrive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TimeFinderTest {

    private fun assertTime(hour: Int, minute: Int, text: String) = assertEquals(text, LocalTime.of(hour, minute), TimeFinder.find(text))

    @Test fun `morning and afternoon words set the half of the day`() {
        assertTime(9, 0, "오전 9시")
        assertTime(15, 0, "오후 3시")
        assertTime(19, 30, "저녁 7시 30분")
        assertTime(12, 0, "낮 12시")
        assertTime(0, 0, "오전 12시")
        assertTime(0, 0, "밤 12시")
    }

    @Test fun `korean counting words and half hour`() {
        assertTime(15, 0, "오후 세 시")
        assertTime(14, 30, "오후 두시 반")
        assertTime(23, 0, "밤 열한시")
    }

    @Test fun `hour without a marker assumes working hours`() {
        assertTime(15, 0, "3시")
        assertTime(9, 0, "9시")
        assertTime(14, 0, "14시")
    }

    @Test fun `clock format and sentence context`() {
        assertTime(14, 30, "14:30")
        assertTime(15, 0, "내일 오후 3시에 미팅")
    }

    @Test fun `words that are not a time`() {
        listOf("내일", "두 시간 뒤", "회의시작", "오후", "다음 주 화요일", "25시", "").forEach { assertNull(it, TimeFinder.find(it)) }
    }
}
