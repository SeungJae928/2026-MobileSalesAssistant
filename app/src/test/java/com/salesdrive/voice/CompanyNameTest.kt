package com.salesdrive.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CompanyNameTest {

    private fun assertNormalized(expected: String, spoken: String) =
        assertEquals(expected, CompanyName.normalize(spoken))

    @Test fun `spoken initials become latin letters`() {
        assertNormalized("A기업 미팅", "에이 기업 미팅")
        assertNormalized("A기업 미팅", "에이기업 미팅")
        assertNormalized("C물산 박대리님", "시물산 박대리님")
        assertNormalized("D테크", "디 테크")
        assertNormalized("H건설", "에이치 건설")
    }

    @Test fun `several letters and josa are kept together`() {
        assertNormalized("LG전자", "엘지전자")
        assertNormalized("SK건설에서 미팅", "에스케이 건설에서 미팅")
        assertNormalized("B전자랑 미팅", "비전자랑 미팅")
    }

    @Test fun `latin initials are tidied`() {
        assertNormalized("B전자", "b전자")
        assertNormalized("A기업", "A 기업")
        assertNormalized("A상사", "A상사")
    }

    @Test fun `ordinary korean words stay as they are`() {
        listOf(
            "이 기업이 좋아요", "유전자 검사", "오상사 미팅", "아이 기업", "삼성전자 미팅",
            "에이 미팅", "시스템 점검", "디자인 회의", "유통기한 확인", "비상사태",
        ).forEach { assertNormalized(it, it) }
    }

    @Test fun `rule parser records the client in latin`() {
        val v = RuleVisitParser.parseNow("에이 기업 미팅", LocalDate.of(2026, 11, 5))!!
        assertEquals("A기업", v.client)
        assertEquals("미팅", v.summary)
    }
}
