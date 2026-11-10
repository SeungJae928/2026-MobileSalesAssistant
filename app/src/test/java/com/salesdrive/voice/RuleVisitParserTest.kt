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

    @Test fun `내일모레 is two days away`() {
        val v = parse("A상사 김부장 납품 미팅 하고 내일모레까지 견적 드려야 해요")
        assertEquals(today.plusDays(2), v.followUpDate)
        assertEquals("견적", v.followUp)
        assertEquals("납품 미팅", v.summary)
    }

    @Test fun `next month and month end`() {
        assertEquals(LocalDate.of(2026, 12, 20), parse("다음달 20일까지 제안서 제출").followUpDate)
        assertEquals(LocalDate.of(2026, 11, 30), parse("이달 말까지 입금 예정이에요").followUpDate)
        assertEquals(LocalDate.of(2026, 12, 31), parse("다음 달 말에 계약").followUpDate)
    }

    @Test fun `time of day and connecting endings stay out of the summary`() {
        assertEquals("납품 미팅", parse("오늘 오전에 A상사 김부장 만나서 납품 미팅 했어요").summary)
        assertEquals("미팅", parse("오후에 G테크 윤 부장 미팅 했고요 11월 20일에 계약하기로 했어요").summary)
        assertEquals("샘플 전달", parse("LG화학 방문해서 샘플 전달했어요").summary)
        assertEquals("현장 점검", parse("오늘 D건설 현장 점검 다녀왔습니다").summary)
    }

    @Test fun `clauses joined by 했고 are split into summary and follow-up`() {
        val v = parse("한빛상사에 납품 완료했고 대금은 이달 말까지 입금 예정이에요")
        assertEquals("납품", v.summary)
        assertEquals("입금", v.followUp)
        assertEquals(LocalDate.of(2026, 11, 30), v.followUpDate)
    }

    @Test fun `promise to come again after the date is a follow-up`() {
        val v = parse("B전자 최과장님 만났는데 가격 협상 좀 더 해야 돼요 다음주 수요일에 다시 올게요")
        assertEquals("가격 협상", v.summary)
        assertEquals("다시 올게요", v.followUp)
        assertEquals(LocalDate.of(2026, 11, 11), v.followUpDate)
    }

    @Test fun `honorific 께 and corporate mark leave the summary`() {
        assertEquals("신제품 소개", parse("E제약 김 이사님께 신제품 소개 드렸어요").summary)
        val v = parse("주식회사 한솔 강대표님 면담, 다음달 3일까지 제안서 제출")
        assertEquals("(주)한솔", v.client)
        assertEquals("면담", v.summary)
    }

    @Test fun `company name split by speech recognition is joined`() {
        assertEquals("삼성전자", parse("삼성 전자 이 대리님 미팅했고 내일 다시 전화 드리기로 했어").client)
    }

    @Test fun `navi destination fills in a client that was not spoken`() {
        assertEquals("A상사 본사", RuleVisitParser.parseNow("납품 미팅 했어요", today, "A상사 본사")!!.client)
        assertEquals("B물산", RuleVisitParser.parseNow("B물산 납품 미팅", today, "A상사 본사")!!.client)
        assertNull(RuleVisitParser.parseNow("납품 미팅 했어요", today, "스타벅스 강남점")!!.client)
        assertNull(RuleVisitParser.parseNow("개인 볼일 봤어요", today, "A상사 본사")!!.client)
    }

    @Test fun `blank is nothing`() {
        assertNull(RuleVisitParser.parseNow("  ", today))
    }
}
