package com.salesdrive.voice

import com.salesdrive.data.db.Purpose
import java.time.LocalDate

/** 방문 원문에서 뽑아낸 항목. 모르는 값은 null */
data class ParsedVisit(
    val client: String? = null,
    val contact: String? = null,
    val summary: String? = null,
    val followUp: String? = null,
    val followUpDate: LocalDate? = null,
    val purpose: Purpose? = null,
)

/** 방문 원문(STT) → 거래처·담당자·요약·후속 일정·목적 */
interface VisitParser {
    /** 원문을 해석하지 못하면 null. [destination]은 내비 목적지명(힌트) */
    suspend fun parse(utterance: String, destination: String?, today: LocalDate): ParsedVisit?
}
