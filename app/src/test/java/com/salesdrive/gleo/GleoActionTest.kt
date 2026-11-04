package com.salesdrive.gleo

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    @Test fun `visit keeps valid date and trims summary`() {
        val v = GleoAction.visit(mapOf("summary" to "가".repeat(30), "followUpDate" to "2026-11-10T09:00:00+09:00"))
        assertEquals(20, v.summary!!.length)
        assertEquals(LocalDate.of(2026, 11, 10), v.followUpDate)
        assertNull(GleoAction.visit(mapOf("followUpDate" to "다음 주")).followUpDate)
    }

    @Test fun `empty visit asks again`() {
        assertTrue(GleoAction.isEmpty(GleoAction.visit(mapOf("contact" to "김부장"))))
        assertFalse(GleoAction.isEmpty(GleoAction.visit(mapOf("client" to "A상사"))))
    }

    @Test fun `raw text marks gleo origin`() {
        assertEquals("[Gleo] A상사 · 납품 미팅", GleoAction.rawText(mapOf("summary" to "납품 미팅", "client" to "A상사")))
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
