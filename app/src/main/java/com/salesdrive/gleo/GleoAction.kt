package com.salesdrive.gleo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.salesdrive.data.db.Schedule
import com.salesdrive.voice.CompanyName
import com.salesdrive.voice.DateFinder
import com.salesdrive.voice.ParsedVisit
import com.salesdrive.voice.TimeFinder
import java.time.LocalDate

/**
 * Gleo Interface(Gleo AI의 function calling) 입출력 변환. 안드로이드 의존 없이 단위 테스트한다.
 * - 입력: Gleo가 assets/schema.json의 function을 골라 인텐트로 보낸다. 인자는 ACTION_PARAM에 JSON 문자열로 온다.
 * - 출력: 처리 결과를 Callback JSON으로 돌려주면 Gleo가 그걸 바탕으로 운전자에게 말한다.
 */
object GleoAction {
    const val RECORD_CLIENT_VISIT = "RECORD_CLIENT_VISIT"
    const val RECORD_SCHEDULE = "RECORD_SCHEDULE"
    const val EXTRA_ACTION_PARAM = "ACTION_PARAM"
    const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    /**
     * ACTION_PARAM → key/value. 문서 예시는 JSON을 한 번 더 따옴표로 감싸 보내므로 감싼 문자열도 풀어 읽는다.
     * 읽을 수 없으면 빈 맵.
     */
    fun params(actionParam: String?): Map<String, String> {
        val obj = toObject(actionParam?.trim().orEmpty()) ?: return emptyMap()
        return obj.entrySet().mapNotNull { (k, v) ->
            if (v.isJsonNull || !v.isJsonPrimitive) return@mapNotNull null
            v.asString.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }?.let { k to it }
        }.toMap()
    }

    private fun toObject(s: String, depth: Int = 0): JsonObject? {
        if (s.isEmpty() || depth > 2) return null
        val e = runCatching { JsonParser.parseString(s) }.getOrNull()
        return when {
            e == null -> {
                // 예시처럼 "\"{"title":...}\"" 로 따옴표만 덧붙은 경우
                val inner = s.removeSurrounding("\"")
                if (inner != s) toObject(inner, depth + 1) else null
            }
            e.isJsonObject -> e.asJsonObject
            e.isJsonPrimitive && e.asJsonPrimitive.isString -> toObject(e.asString.trim(), depth + 1)
            else -> null
        }
    }

    /** Gleo는 action 앞에 패키지명을 붙여 보내기도 한다. 어느 쪽이든 schema의 function 이름으로 맞춘다 */
    fun functionName(action: String, packageName: String): String = action.removePrefix("$packageName.")

    /**
     * Gleo가 뽑아 준 값 → 방문 항목.
     * Gleo는 "내일"을 2023-10-26처럼 틀린 날짜로 바꾼 적이 있어, 말 그대로 받은 표현을 오늘 기준으로 직접 계산한다.
     */
    fun visit(p: Map<String, String>, today: LocalDate): ParsedVisit = ParsedVisit(
        client = p["client"]?.let(CompanyName::normalize),
        contact = p["contact"],
        summary = p["summary"]?.take(SUMMARY_MAX),
        followUp = p["followUp"],
        followUpDate = p["followUpWhen"]?.let { DateFinder.find(it, today)?.date },
    )

    /**
     * Gleo 경로는 STT 원문이 오지 않는다. 방문 원문 칸은 비울 수 없으므로 받은 값을 이어 붙여 남기고,
     * 어디서 온 기록인지 알 수 있게 앞에 표시한다.
     */
    fun rawText(p: Map<String, String>): String =
        listOfNotNull(p["client"], p["contact"], p["summary"], p["followUp"], p["followUpWhen"])
            .joinToString(" · ")
            .ifEmpty { "(내용 없음)" }
            .let { RAW_PREFIX + it }

    /** Gleo가 이미 정리한 기록인지. 원문이 이어 붙인 값이라 다시 파싱하면 정리된 값이 망가진다 */
    fun isFromGleo(rawUtterance: String) = rawUtterance.startsWith(RAW_PREFIX)

    /** 일정 날짜도 Gleo가 바꾼 값을 믿지 않고 말한 표현을 오늘 기준으로 계산한다 */
    fun scheduleDate(p: Map<String, String>, today: LocalDate): LocalDate? =
        p["when"]?.let { DateFinder.find(it, today, includeToday = true)?.date }

    /** 시각은 따로 오지 않아도 날짜 표현 안에 섞여 있을 수 있다("내일 오후 3시") */
    fun schedule(p: Map<String, String>, title: String, date: LocalDate): Schedule = Schedule(
        date = date,
        time = (p["time"] ?: p["when"])?.let(TimeFinder::find),
        title = title.take(SUMMARY_MAX),
        client = p["client"]?.let(CompanyName::normalize),
        contact = p["contact"],
        rawText = RAW_PREFIX + listOfNotNull(p["when"], p["time"], title, p["client"], p["contact"]).joinToString(" · "),
    )

    /** 내용이 하나도 없으면 저장하지 않고 다시 묻게 한다 */
    fun isEmpty(v: ParsedVisit) = v.summary == null && v.client == null && v.followUp == null

    enum class Status(val wire: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }

    /** Callback Schema. message는 영어 문장이어야 하고, 고유명사는 원문 그대로 넣어도 된다 */
    fun callback(function: String, status: Status, message: String, data: Map<String, String?>? = null): String =
        JsonObject().apply {
            addProperty("functionName", function)
            addProperty("status", status.wire)
            addProperty("message", message)
            data?.let { d ->
                val item = JsonObject().apply { d.forEach { (k, v) -> if (v != null) addProperty(k, v) } }
                addProperty("data", com.google.gson.JsonArray().apply { add(item) }.toString())
            }
        }.toString()

    private const val SUMMARY_MAX = 20
    private const val RAW_PREFIX = "[Gleo] "
}
