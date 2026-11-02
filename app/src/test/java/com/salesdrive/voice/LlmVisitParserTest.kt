package com.salesdrive.voice

import com.salesdrive.data.db.Purpose
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LlmVisitParserTest {

    private val today = LocalDate.of(2026, 11, 2)

    private class Reply(private val text: String?) : LlmClient {
        var prompt: String? = null
        override suspend fun generate(prompt: String): String? = text.also { this.prompt = prompt }
    }

    @Test fun `json reply wrapped in a code fence is decoded`() = runTest {
        val llm = Reply(
            """
            ```json
            {"client":"A상사","contact":"김부장","summary":"납품 미팅","followUp":"견적 회신",
             "followUpDate":"2026-11-10","purpose":"business"}
            ```
            """.trimIndent(),
        )
        val v = LlmVisitParser(llm).parse("A상사 김부장 납품 미팅, 견적 다음 주 화요일까지", "A상사 본사", today)!!

        assertEquals(ParsedVisit("A상사", "김부장", "납품 미팅", "견적 회신", LocalDate.of(2026, 11, 10), Purpose.BUSINESS), v)
        assertTrue(llm.prompt!!.contains("2026-11-02 (월요일)"))
        assertTrue(llm.prompt!!.contains("도착지: A상사 본사"))
    }

    @Test fun `null strings and bad values become null`() {
        val v = LlmVisitParser.decode("""{"client":"null","contact":null,"summary":"","followUpDate":"다음 주","purpose":"WORK"}""")!!
        assertEquals(ParsedVisit(), v)
    }

    @Test fun `non json reply is a failure`() = runTest {
        assertNull(LlmVisitParser(Reply("잘 모르겠습니다")).parse("테스트", null, today))
        assertNull(LlmVisitParser(Reply(null)).parse("테스트", null, today))
    }

    @Test fun `falls back to rules when the llm fails`() = runTest {
        val parser = FallbackVisitParser(LlmVisitParser(Reply(null)), RuleVisitParser)
        assertEquals("A상사", parser.parse("A상사 김부장 납품 미팅", null, today)!!.client)
    }
}
