package com.salesdrive.gleo

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class GleoActionTest {

    @Test fun `plain json param`() {
        val p = GleoAction.params("""{"client":"A상사","contact":"김부장","summary":"납품 미팅"}""")
        assertEquals(mapOf("client" to "A상사", "contact" to "김부장", "summary" to "납품 미팅"), p)
    }

    @Test fun `json wrapped in quotes like the doc example`() {
        val p = GleoAction.params("\"{\"summary\":\"견적 협의\", \"followUpDate\":\"2026-11-10\"}\"")
        assertEquals("견적 협의", p["summary"])
        assertEquals("2026-11-10", p["followUpDate"])
    }

    @Test fun `json encoded as a string`() {
        val p = GleoAction.params("\"{\\\"summary\\\":\\\"견적 협의\\\"}\"")
        assertEquals("견적 협의", p["summary"])
    }

    @Test fun `garbage and blanks are ignored`() {
        assertTrue(GleoAction.params(null).isEmpty())
        assertTrue(GleoAction.params("not json").isEmpty())
        val p = GleoAction.params("""{"client":"","contact":"null","summary":" 미팅 ","n":3,"o":{}}""")
        assertEquals(mapOf("summary" to "미팅", "n" to "3"), p)
    }

    private val today = LocalDate.of(2026, 11, 5)

    @Test fun `follow up date is computed from the spoken words and summary is trimmed`() {
        val v = GleoAction.visit(mapOf("summary" to "가".repeat(30), "followUpWhen" to "다음 주 화요일까지"), today)
        assertEquals(20, v.summary!!.length)
        assertEquals(LocalDate.of(2026, 11, 10), v.followUpDate)
        assertEquals(LocalDate.of(2026, 11, 6), GleoAction.visit(mapOf("followUpWhen" to "내일"), today).followUpDate)
        assertNull(GleoAction.visit(mapOf("followUpWhen" to "조만간"), today).followUpDate)
    }

    @Test fun `client spelled in korean sounds is recorded in latin`() {
        assertEquals("C물산", GleoAction.visit(mapOf("client" to "시물산"), today).client)
        assertEquals("B전자", GleoAction.visit(mapOf("client" to "b전자"), today).client)
    }

    @Test fun `a date made up by gleo is ignored`() {
        assertNull(GleoAction.visit(mapOf("followUpDate" to "2023-10-26"), today).followUpDate)
    }

    @Test fun `action with or without package prefix is the same function`() {
        assertEquals("RECORD_CLIENT_VISIT", GleoAction.functionName("com.salesdrive.RECORD_CLIENT_VISIT", "com.salesdrive"))
        assertEquals("RECORD_CLIENT_VISIT", GleoAction.functionName("RECORD_CLIENT_VISIT", "com.salesdrive"))
    }

    @Test fun `empty visit asks again`() {
        assertTrue(GleoAction.isEmpty(GleoAction.visit(mapOf("contact" to "김부장"), today)))
        assertFalse(GleoAction.isEmpty(GleoAction.visit(mapOf("client" to "A상사"), today)))
    }

    @Test fun `raw text marks gleo origin`() {
        assertEquals("[Gleo] A상사 · 납품 미팅", GleoAction.rawText(mapOf("summary" to "납품 미팅", "client" to "A상사")))
    }

    @Test fun `only records made through gleo count as gleo records`() {
        assertTrue(GleoAction.isFromGleo(GleoAction.rawText(mapOf("summary" to "납품 미팅"))))
        assertFalse(GleoAction.isFromGleo("A상사 김부장 납품 미팅"))
    }

    @Test fun `schedule date is computed from the spoken words`() {
        assertEquals(today.plusDays(1), GleoAction.scheduleDate(mapOf("when" to "내일"), today))
        assertEquals(today, GleoAction.scheduleDate(mapOf("when" to "오늘 오후 3시"), today))
        assertNull(GleoAction.scheduleDate(mapOf("when" to "2023-10-26"), today))
        assertNull(GleoAction.scheduleDate(emptyMap(), today))
    }

    @Test fun `schedule reads the time from either field and normalizes the client`() {
        val p = mapOf("title" to "제안 미팅", "when" to "내일 오후 3시", "client" to "에이 기업", "contact" to "김부장")
        val s = GleoAction.schedule(p, "제안 미팅", today.plusDays(1))
        assertEquals(LocalTime.of(15, 0), s.time)
        assertEquals("A기업", s.client)
        assertEquals("김부장", s.contact)
        assertTrue(GleoAction.isFromGleo(s.rawText))

        val separate = GleoAction.schedule(mapOf("when" to "내일", "time" to "오전 10시"), "점검", today)
        assertEquals(LocalTime.of(10, 0), separate.time)
        assertNull(GleoAction.schedule(mapOf("when" to "내일"), "점검", today).time)
    }

    @Test fun `callback follows the schema`() {
        val json = JsonParser.parseString(
            GleoAction.callback("RECORD_CLIENT_VISIT", GleoAction.Status.SUCCESS, "Saved.", mapOf("client" to "A상사", "contact" to null)),
        ).asJsonObject
        assertEquals("RECORD_CLIENT_VISIT", json["functionName"].asString)
        assertEquals("success", json["status"].asString)
        val data = JsonParser.parseString(json["data"].asString).asJsonArray
        assertEquals("A상사", data[0].asJsonObject["client"].asString)
        assertFalse(data[0].asJsonObject.has("contact"))
        assertFalse(JsonParser.parseString(GleoAction.callback("X", GleoAction.Status.FAIL, "No.")).asJsonObject.has("data"))
    }
}
