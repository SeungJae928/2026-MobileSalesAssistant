package com.salesdrive.voice

import com.salesdrive.data.db.Purpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RuleVisitParserTest {

    /** 2026-11-02는 월요일 */
    private val today = LocalDate.of(2026, 11, 2)

    private fun parse(text: String) = RuleVisitParser.parseNow(text, today)!!

    @Test fun `guide example is split into client, contact, summary and follow-up`() {
        val v = parse("A상사 김부장 납품 미팅, 견적 다음 주 화요일까지")
        assertEquals("A상사", v.client)
        assertEquals("김부장", v.contact)
        assertEquals("납품 미팅", v.summary)
        assertEquals("견적", v.followUp)
        assertEquals(LocalDate.of(2026, 11, 10), v.followUpDate)
        assertEquals(Purpose.BUSINESS, v.purpose)
    }

    @Test fun `follow-up after the date without a comma`() {
        val v = parse("B물산 이과장 만나서 신제품 소개했어요 다음 주 수요일에 다시 방문하기로 했어요")
        assertEquals("B물산", v.client)
        assertEquals("이과장", v.contact)
        assertEquals("신제품 소개", v.summary)
        assertEquals("다시 방문", v.followUp)
        assertEquals(LocalDate.of(2026, 11, 11), v.followUpDate)
    }

    @Test fun `full name contact, branch and absolute date`() {
        val v = parse("한빛전자 본사에서 박민수 팀장님이랑 계약 조건 협의, 11월 20일까지 계약서 발송")
        assertEquals("한빛전자 본사", v.client)
        assertEquals("박민수 팀장", v.contact)
        assertEquals("계약 조건 협의", v.summary)
        assertEquals("계약서 발송", v.followUp)
        assertEquals(LocalDate.of(2026, 11, 20), v.followUpDate)
    }

    @Test fun `relative dates`() {
        assertEquals(today.plusDays(1), parse("견적 내일까지 보내기로 함").followUpDate)
        assertEquals(LocalDate.of(2026, 11, 6), parse("금요일에 다시 연락").followUpDate)
        assertEquals(today.plusDays(3), parse("3일 후 회신").followUpDate)
    }

    @Test fun `no follow-up and no counterpart`() {
        val v = parse("오늘 미팅 잘 끝났어요")
        assertNull(v.client)
        assertNull(v.contact)
        assertNull(v.followUp)
        assertNull(v.followUpDate)
        assertEquals(Purpose.BUSINESS, v.purpose)
    }

    @Test fun `commute and personal purpose`() {
        assertEquals(Purpose.COMMUTE, parse("퇴근길이에요").purpose)
        assertEquals(Purpose.PERSONAL, parse("개인 볼일 보러 왔어요").purpose)
        assertNull(parse("테스트").purpose)
    }

    @Test fun `blank is nothing`() {
        assertNull(RuleVisitParser.parseNow("  ", today))
    }
}
