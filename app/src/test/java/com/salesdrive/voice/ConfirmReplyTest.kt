package com.salesdrive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmReplyTest {

    private fun assertAll(expected: ConfirmReply?, vararg heard: String) =
        heard.forEach { assertEquals("\"$it\"", expected, ConfirmReply.classify(it)) }

    @Test fun yes() = assertAll(
        ConfirmReply.YES,
        "응", "네", "네네", "예.", "그래요", "좋아", "OK", "응 기록해", "네 기록해 주세요", "기록해줘", "부탁해",
    )

    @Test fun no() = assertAll(
        ConfirmReply.NO,
        "아니", "아니요", "아니 휴게소야", "아뇨 괜찮아요", "됐어", "괜찮아", "건너뛰어", "취소", "휴게소", "잠깐 쉬는 중", "주유하러 왔어",
    )

    @Test fun `visit content is taken as content`() = assertAll(
        ConfirmReply.CONTENT,
        "A상사 김부장 납품 미팅",
        "어제 A상사 미팅 후속",
        "네오텍 견적 회신 받음",
        "충전기 납품 건으로 B물산 방문 후 다음 주 화요일 재방문",
        "그래도 계약은 다음 달",
    )

    @Test fun `noise is undecided`() {
        assertNull(ConfirmReply.classify(""))
        assertNull(ConfirmReply.classify(" . "))
        assertNull(ConfirmReply.classify("음"))
    }
}
