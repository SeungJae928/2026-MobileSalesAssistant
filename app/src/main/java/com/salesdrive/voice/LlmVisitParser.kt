package com.salesdrive.voice

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.salesdrive.data.db.Purpose
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 프롬프트 한 번 → 응답 텍스트. 응답이 없거나 실패하면 null */
interface LlmClient {
    suspend fun generate(prompt: String): String?
}

/** LLM에 원문과 목적지명을 보내 JSON으로 받는다. JSON이 아니거나 응답이 없으면 null */
class LlmVisitParser(private val llm: LlmClient) : VisitParser {

    override suspend fun parse(utterance: String, destination: String?, today: LocalDate): ParsedVisit? {
        val reply = llm.generate(prompt(utterance, destination, today)) ?: return null
        return decode(reply).also { if (it == null) Log.w(TAG, "not json: $reply") }
    }

    companion object {
        private const val TAG = "LlmVisitParser"

        fun prompt(utterance: String, destination: String?, today: LocalDate): String {
            val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
            return """
                너는 영업 방문 기록을 정리한다. 아래 발화를 JSON으로만 답하라. 설명은 쓰지 마라.
                키: client(거래처 회사명), contact(담당자 이름과 직함), summary(20자 이내), followUp(후속 할 일),
                followUpDate(YYYY-MM-DD 또는 null), purpose(BUSINESS|COMMUTE|PERSONAL). 모르면 null.
                오늘 날짜: $today ($weekday) / 도착지: ${destination ?: "모름"}
                발화: "$utterance"
            """.trimIndent()
        }

        /** 응답 앞뒤에 ```json 같은 군더더기가 붙어도 첫 '{'부터 마지막 '}'까지만 읽는다 */
        fun decode(reply: String): ParsedVisit? {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val json = runCatching { JsonParser.parseString(reply.substring(start, end + 1)).asJsonObject }.getOrNull()
                ?: return null
            return ParsedVisit(
                client = json.text("client"),
                contact = json.text("contact"),
                summary = json.text("summary")?.take(SUMMARY_MAX),
                followUp = json.text("followUp"),
                followUpDate = json.text("followUpDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                purpose = json.text("purpose")?.uppercase()?.let { p -> Purpose.entries.firstOrNull { it.name == p } },
            )
        }

        private const val SUMMARY_MAX = 20

        private fun JsonObject.text(key: String): String? {
            val e = get(key) ?: return null
            if (e.isJsonNull || !e.isJsonPrimitive) return null
            return e.asString.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
        }
    }
}

/** LLM이 먼저, 실패하면 규칙 기반. 원문은 이미 저장돼 있으므로 둘 다 실패해도 기록은 남는다 */
class FallbackVisitParser(
    private val primary: VisitParser,
    private val fallback: VisitParser,
) : VisitParser {
    override suspend fun parse(utterance: String, destination: String?, today: LocalDate): ParsedVisit? =
        runCatching { primary.parse(utterance, destination, today) }
            .onFailure { Log.w("VisitParser", "primary failed", it) }
            .getOrNull()
            ?: fallback.parse(utterance, destination, today)
}
